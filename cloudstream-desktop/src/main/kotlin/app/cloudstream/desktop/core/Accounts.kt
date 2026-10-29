package app.cloudstream.desktop.core

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.awt.Desktop
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.CompletableFuture

/** The two lists the original app syncs with, reduced to what this build can talk to. */
enum class TrackingProvider(
    val id: String,
    val label: String,
    val description: String,
    val registerUrl: String,
) {
    AniList(
        id = "anilist",
        label = "AniList",
        description = "Anime and manga lists. Create an API client at anilist.co/settings/developer " +
            "and set its redirect URL to http://127.0.0.1:47199/anilist.",
        registerUrl = "https://anilist.co/settings/developer",
    ),
    MyAnimeList(
        id = "mal",
        label = "MyAnimeList",
        description = "Anime and manga lists. Create an app at myanimelist.net/apiconfig with the " +
            "redirect URL http://127.0.0.1:47199/mal.",
        registerUrl = "https://myanimelist.net/apiconfig",
    );

    val redirectUrl: String get() = "http://127.0.0.1:${Accounts.PORT}/$id"

    companion object {
        fun of(id: String?): TrackingProvider? = entries.firstOrNull { it.id == id }
    }
}

/** One signed-in list account. */
@Serializable
data class LinkedAccount(
    val provider: String,
    val userId: String = "",
    val username: String = "",
    val accessToken: String = "",
    val refreshToken: String? = null,
    /** Epoch millis; 0 means the token does not expire. */
    val expiresAt: Long = 0,
    val clientId: String = "",
    val enabled: Boolean = true,
    val linkedAt: Long = System.currentTimeMillis(),
)

@Serializable
private data class AccountFile(val accounts: List<LinkedAccount> = emptyList())

/**
 * Sign-in and storage for AniList and MyAnimeList.
 *
 * The Android app runs the OAuth page in a WebView and intercepts the redirect; a desktop app
 * has no WebView, so the system browser is used and the redirect lands on a loopback listener
 * on a fixed port (redirect URLs have to be registered with the provider byte for byte, which
 * is why the port is not random). AniList answers with the token in the URL fragment, which a
 * server cannot read, so its callback page forwards the fragment by POST.
 */
object Accounts {
    const val PORT = 47199

    private val file: Path =
        Paths.get(System.getProperty("user.home"), ".cloudstream-desktop", "accounts.json")

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val lock = Any()

    private var data: AccountFile = read()

    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes

    private fun read(): AccountFile = runCatching {
        if (Files.isRegularFile(file)) {
            json.decodeFromString<AccountFile>(Files.readString(file))
        } else {
            AccountFile()
        }
    }.getOrElse { AccountFile() }

    private fun write() {
        runCatching {
            Files.createDirectories(file.parent)
            Files.writeString(file, json.encodeToString(data))
            // Tokens are credentials: keep the file private to the user account.
            runCatching { Files.setPosixFilePermissions(file, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")) }
        }
    }

    fun all(): List<LinkedAccount> = synchronized(lock) { data.accounts }

    fun get(provider: TrackingProvider): LinkedAccount? = synchronized(lock) {
        data.accounts.firstOrNull { it.provider == provider.id }
    }

    fun isLinked(provider: TrackingProvider): Boolean = get(provider) != null

    fun setEnabled(provider: TrackingProvider, enabled: Boolean) = synchronized(lock) {
        data = data.copy(
            accounts = data.accounts.map {
                if (it.provider == provider.id) it.copy(enabled = enabled) else it
            },
        )
        write()
        _changes.value += 1
    }

    fun save(account: LinkedAccount) = synchronized(lock) {
        val others = data.accounts.filterNot { it.provider == account.provider }
        data = data.copy(accounts = others + account)
        write()
        _changes.value += 1
    }

    fun remove(provider: TrackingProvider) = synchronized(lock) {
        data = data.copy(accounts = data.accounts.filterNot { it.provider == provider.id })
        write()
        _changes.value += 1
    }

    // ------------------------------------------------------------------ sign-in

    /** Runs the whole browser flow; the result is only saved when the profile call succeeds. */
    suspend fun signIn(provider: TrackingProvider, clientId: String): Result<LinkedAccount> =
        runCatching {
            val trimmed = clientId.trim()
            require(trimmed.isNotEmpty()) { "Enter the client ID from your ${provider.label} app first" }

            val verifier = if (provider == TrackingProvider.MyAnimeList) codeVerifier() else null
            val server = CallbackServer(provider)
            try {
                server.start()
                openBrowser(authorizeUrl(provider, trimmed, verifier))
                val payload = withTimeoutOrNull(180_000) { server.await() }
                    ?: error("Sign-in timed out — close the browser tab and try again")
                when (provider) {
                    TrackingProvider.AniList -> aniListAccount(payload, trimmed)
                    TrackingProvider.MyAnimeList -> malAccount(payload, trimmed, verifier!!)
                }
            } finally {
                server.stop()
            }
        }.onSuccess { save(it) }

    private fun openBrowser(url: String) {
        val desktop = runCatching { Desktop.getDesktop() }.getOrNull()
        if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
            desktop.browse(URI(url))
        } else {
            error("No browser available — open this address to sign in:\n$url")
        }
    }

    private fun authorizeUrl(
        provider: TrackingProvider,
        clientId: String,
        verifier: String?,
    ): String = when (provider) {
        TrackingProvider.AniList ->
            "https://anilist.co/api/v2/oauth/authorize?client_id=$clientId&response_type=token"
        TrackingProvider.MyAnimeList -> buildString {
            append("https://myanimelist.net/v1/oauth2/authorize?response_type=code")
            append("&client_id=").append(enc(clientId))
            append("&code_challenge=").append(enc(challenge(verifier!!)))
            append("&code_challenge_method=S256")
            append("&state=").append(enc("cloudstream-desktop"))
            append("&redirect_uri=").append(enc(provider.redirectUrl))
        }
    }

    private suspend fun aniListAccount(payload: String, clientId: String): LinkedAccount {
        val form = parseForm(payload)
        val token = form["access_token"] ?: error("AniList returned no access token")
        val expiresIn = form["expires_in"]?.toLongOrNull() ?: 31536000
        val viewer = HttpClient.graphql(
            token = token,
            query = "query { Viewer { id name } }",
        ) ?: error("AniList did not return the signed-in user")
        val viewerObject = Json.parseToJsonElement(viewer).jsonObject["data"]?.jsonObject
            ?.get("Viewer")?.jsonObject
        val id = viewerObject?.get("id")?.jsonPrimitive?.contentOrNull.orEmpty()
        val name = viewerObject?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
        return LinkedAccount(
            provider = TrackingProvider.AniList.id,
            userId = id,
            username = name,
            accessToken = token,
            expiresAt = System.currentTimeMillis() + expiresIn * 1000,
            clientId = clientId,
        )
    }

    private suspend fun malAccount(payload: String, clientId: String, verifier: String): LinkedAccount {
        val form = parseForm(payload)
        val code = form["code"] ?: error("MyAnimeList returned no authorization code")
        val body = formEncode(
            mapOf(
                "client_id" to clientId,
                "code" to code,
                "code_verifier" to verifier,
                "grant_type" to "authorization_code",
                "redirect_uri" to TrackingProvider.MyAnimeList.redirectUrl,
            ),
        )
        val tokenResponse = Http.send(
            method = "POST",
            url = "https://myanimelist.net/v1/oauth2/token",
            body = body,
            contentType = "application/x-www-form-urlencoded",
        )
        val token = Json.parseToJsonElement(tokenResponse).jsonObject
        val access = token["access_token"]?.jsonPrimitive?.contentOrNull
            ?: error("MyAnimeList returned no access token")
        val refresh = token["refresh_token"]?.jsonPrimitive?.contentOrNull
        val expiresIn = token["expires_in"]?.jsonPrimitive?.intOrNull ?: 0
        val me = Http.send(
            method = "GET",
            url = "https://api.myanimelist.net/v2/users/@me",
            headers = mapOf("Authorization" to "Bearer $access"),
        )
        val profile = Json.parseToJsonElement(me).jsonObject
        return LinkedAccount(
            provider = TrackingProvider.MyAnimeList.id,
            userId = profile["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            username = profile["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            accessToken = access,
            refreshToken = refresh,
            expiresAt = if (expiresIn > 0) System.currentTimeMillis() + expiresIn * 1000L else 0,
            clientId = clientId,
        )
    }

    /**
     * A usable access token, refreshing it first when the provider handed out an expiring one.
     * Null when the account can no longer be used, which drops it out of syncing quietly.
     */
    suspend fun token(account: LinkedAccount): String? {
        val expiry = account.expiresAt
        if (expiry == 0L || expiry - 60_000 > System.currentTimeMillis()) return account.accessToken
        val refresh = account.refreshToken ?: return null
        val provider = TrackingProvider.of(account.provider) ?: return null
        if (provider != TrackingProvider.MyAnimeList || account.clientId.isBlank()) return null

        return runCatching {
            val body = formEncode(
                mapOf(
                    "client_id" to account.clientId,
                    "grant_type" to "refresh_token",
                    "refresh_token" to refresh,
                ),
            )
            val response = Http.send(
                method = "POST",
                url = "https://myanimelist.net/v1/oauth2/token",
                body = body,
                contentType = "application/x-www-form-urlencoded",
            )
            val parsed = Json.parseToJsonElement(response).jsonObject
            val access = parsed["access_token"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
            val expiresIn = parsed["expires_in"]?.jsonPrimitive?.intOrNull ?: 0
            val updated = account.copy(
                accessToken = access,
                refreshToken = parsed["refresh_token"]?.jsonPrimitive?.contentOrNull ?: refresh,
                expiresAt = if (expiresIn > 0) System.currentTimeMillis() + expiresIn * 1000L else 0,
            )
            save(updated)
            access
        }.getOrNull()
    }

    // ------------------------------------------------------------------ callback server

    /** Loopback listener for one sign-in; the first hit with a payload completes it. */
    private class CallbackServer(private val provider: TrackingProvider) {
        private val result = CompletableFuture<String>()
        private val server: HttpServer = HttpServer.create(
            InetSocketAddress("127.0.0.1", PORT),
            0,
        )

        fun start() {
            server.createContext("/") { exchange -> handle(exchange) }
            server.executor = null
            server.start()
        }

        fun stop() = runCatching { server.stop(0) }.let { }

        suspend fun await(): String = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            result.whenComplete { value, error ->
                if (error != null) continuation.cancel(error) else continuation.resumeWith(Result.success(value))
            }
            continuation.invokeOnCancellation { result.cancel(true) }
        }

        private fun handle(exchange: com.sun.net.httpserver.HttpExchange) {
            val path = exchange.requestURI.path
            val query = exchange.requestURI.rawQuery.orEmpty()
            val body = exchange.requestBody.bufferedReader().use { it.readText() }
            val answer = when {
                path.endsWith("/token") && body.isNotBlank() -> {
                    result.complete(body)
                    donePage()
                }
                provider == TrackingProvider.AniList && path.contains("anilist") ->
                    fragmentForwardPage()
                provider == TrackingProvider.MyAnimeList && query.contains("code=") -> {
                    result.complete(query)
                    donePage()
                }
                else -> "<html><body style=\"background:#111;color:#eee;font-family:sans-serif;padding:40px\">" +
                    "<h2>CloudStream Desktop</h2><p>Waiting for ${provider.label}…</p></body></html>"
            }
            val bytes = answer.toByteArray(StandardCharsets.UTF_8)
            runCatching {
                exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            runCatching { exchange.close() }
        }

        private fun donePage(): String =
            "<html><body style=\"background:#111;color:#eee;font-family:sans-serif;padding:40px\">" +
                "<h2>CloudStream Desktop</h2><p>Signed in with ${provider.label}. " +
                "You can close this tab and return to the app.</p></body></html>"

        /** AniList sends the token after a `#`, which never reaches the server — forward it. */
        private fun fragmentForwardPage(): String =
            """
            <html><body style="background:#111;color:#eee;font-family:sans-serif;padding:40px">
            <h2>CloudStream Desktop</h2><p id="status">Finishing ${provider.label} sign-in…</p>
            <script>
              var payload = location.hash.replace(/^#/, '');
              if (!payload) { document.getElementById('status').textContent = 'No token in the response.'; }
              else {
                fetch('/anilist/token', { method: 'POST', body: payload })
                  .then(function () { document.getElementById('status').textContent = 'Signed in. You can close this tab.'; })
                  .catch(function () { document.getElementById('status').textContent = 'Could not reach the app.'; });
              }
            </script></body></html>
            """.trimIndent()
    }

    // ------------------------------------------------------------------ helpers

    internal fun parseForm(value: String): Map<String, String> =
        value.split('&').mapNotNull { part ->
            if (part.isBlank()) return@mapNotNull null
            val key = part.substringBefore('=')
            val raw = part.substringAfter('=', "")
            key to java.net.URLDecoder.decode(raw, StandardCharsets.UTF_8)
        }.toMap()

    internal fun formEncode(values: Map<String, String>): String =
        values.entries.joinToString("&") { (key, value) -> "${enc(key)}=${enc(value)}" }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun codeVerifier(): String {
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    internal fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    /** Just enough GraphQL for the two calls sign-in and syncing need. */
    internal object HttpClient {
        suspend fun graphql(token: String, query: String, variables: Map<String, Any?> = emptyMap()): String? {
            val payload = buildJsonObject {
                put("query", JsonPrimitive(query))
                put(
                    "variables",
                    JsonObject(
                        variables.entries.associate { (key, value) ->
                            key to when (value) {
                                null -> JsonNull
                                is Int -> JsonPrimitive(value)
                                is Boolean -> JsonPrimitive(value)
                                else -> JsonPrimitive(value.toString())
                            }
                        },
                    ),
                )
            }.toString()
            return runCatching {
                Http.send(
                    method = "POST",
                    url = "https://graphql.anilist.co",
                    body = payload,
                    contentType = "application/json",
                    headers = mapOf("Authorization" to "Bearer $token", "Accept" to "application/json"),
                )
            }.getOrNull()
        }
    }
}
