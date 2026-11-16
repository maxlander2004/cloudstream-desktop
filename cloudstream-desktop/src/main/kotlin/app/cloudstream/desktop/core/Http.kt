package app.cloudstream.desktop.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Thin wrapper over the JDK HTTP client so extensions need no extra dependency. */
object Http {
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) CloudStreamDesktop/1.0"

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    private val noRedirectClient: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    /** The settings whose Network & Proxy toggles rewrite every request made here. */
    @Volatile
    private var policy: Settings? = null

    fun use(settings: Settings) {
        policy = settings
    }

    /** Clone site bypass and the GitHub proxy, applied the way the original applies them. */
    internal fun rewrite(url: String): String {
        val settings = policy ?: return url
        return Network.rewrite(url, settings.cloneSite, settings.githubProxy)
    }

    /**
     * `java.net.URI` rejects a raw space, but repositories publish extension urls as written on
     * disk (`.../CNC Verse.cs3`). Android's OkHttp quotes those for us; the JDK client does not,
     * so quote the characters RFC 3986 forbids before handing the url over.
     */
    internal fun uriOf(url: String): URI {
        var needsQuoting = false
        for (ch in url) {
            if (ch == ' ' || ch == '"' || ch == '<' || ch == '>' || ch == '`' ||
                ch == '{' || ch == '}' || ch == '|' || ch == '\\' || ch == '^' ||
                ch == '[' || ch == ']'
            ) {
                needsQuoting = true
                break
            }
        }
        if (!needsQuoting) return URI.create(url)

        val quoted = StringBuilder(url.length + 16)
        for (ch in url) {
            if (ch == ' ' || ch == '"' || ch == '<' || ch == '>' || ch == '`' ||
                ch == '{' || ch == '}' || ch == '|' || ch == '\\' || ch == '^' ||
                ch == '[' || ch == ']'
            ) {
                for (byte in ch.toString().toByteArray(Charsets.UTF_8)) {
                    quoted.append('%').append("%02X".format(byte))
                }
            } else {
                quoted.append(ch)
            }
        }
        return URI.create(quoted.toString())
    }

    private fun request(url: String, timeout: Duration): HttpRequest =
        HttpRequest.newBuilder(uriOf(rewrite(url)))
            .timeout(timeout)
            .header("User-Agent", UA)
            .GET()
            .build()

    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val response = client.send(request(url, Duration.ofSeconds(25)), HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) { "HTTP ${response.statusCode()} for $url" }
        response.body()
    }

    suspend fun getBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        var response = client.send(request(url, Duration.ofSeconds(60)), HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() !in 200..299) {
            // Some repo CDNs hand out a malformed Location header across a 302/307 and the JDK
            // rejects the subsequent request with "malformed utf-8 sequence". In that case the
            // canonical extension URL still resolves if we retry without the automated rewrite,
            // so fall back once rather than treating the extension as undownloadable.
            response = client.send(
                HttpRequest.newBuilder(uriOf(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("User-Agent", UA)
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofByteArray(),
            )
            check(response.statusCode() in 200..299) {
                "HTTP ${response.statusCode()} for $url"
            }
        }
        response.body()
    }

    /**
     * A request with a method, a body and headers — what the list APIs and the subtitle search
     * need. Kept here so every call still goes through the clone-site and proxy rewrites.
     */
    suspend fun send(
        method: String,
        url: String,
        body: String? = null,
        contentType: String? = null,
        headers: Map<String, String> = emptyMap(),
        timeoutSeconds: Long = 30,
    ): String = withContext(Dispatchers.IO) {
        val builder = HttpRequest.newBuilder(URI.create(rewrite(url)))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .header("User-Agent", UA)
            .method(
                method,
                body?.let { HttpRequest.BodyPublishers.ofString(it) }
                    ?: HttpRequest.BodyPublishers.noBody(),
            )
        contentType?.let { builder.header("Content-Type", it) }
        headers.forEach { (key, value) -> builder.header(key, value) }
        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) {
            "HTTP ${response.statusCode()}: ${response.body().take(200)}"
        }
        response.body()
    }

    /**
     * Where a short link points, without following it — the redirect lookup upstream uses to
     * resolve repository shortcodes such as `megarepo`. Null when the link does not redirect.
     */
    suspend fun redirectTarget(url: String): String? = withContext(Dispatchers.IO) {
        val response = noRedirectClient.send(
            request(url, Duration.ofSeconds(15)),
            HttpResponse.BodyHandlers.discarding(),
        )
        if (response.statusCode() !in 300..399) return@withContext null
        response.headers().firstValue("location").orElse(null)?.trim()
    }
}
