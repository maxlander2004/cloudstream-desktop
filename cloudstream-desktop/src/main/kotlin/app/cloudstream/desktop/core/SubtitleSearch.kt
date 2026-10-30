package app.cloudstream.desktop.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.zip.ZipInputStream

/** One subtitle found online. */
data class SubtitleHit(
    val provider: String,
    val title: String,
    val language: String,
    val release: String? = null,
    /** Where the subtitle is fetched from. For OpenSubtitles this is the download endpoint. */
    val url: String,
    /** OpenSubtitles needs a file id rather than a URL to start a download. */
    val fileId: Int? = null,
) {
    val label: String
        get() = buildString {
            append(language.ifBlank { "Unknown" })
            release?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
            if (release.isNullOrBlank()) title.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        }
}

/**
 * Online subtitle search.
 *
 * Two of the original's providers are implemented, and both are honest about what they need:
 *
 * * **SubDL** wants an API key, which each user creates for free on their own account.
 * * **OpenSubtitles** searches with the public key CloudStream itself ships, so search works out
 *   of the box; downloads are the part OpenSubtitles gates behind a free account, so a sign-in
 *   is required for those and the token is what is kept (never the password).
 *
 * SubSource is not here on purpose: its API only answers IMDb-id lookups, and a provider's
 * search result URL does not carry a reliable IMDb id, so it would silently return nothing.
 */
object SubtitleSearch {
    const val PROVIDER_SUBDL = "subdl"
    const val PROVIDER_OPENSUBTITLES = "opensubtitles"

    /** The public key CloudStream's own OpenSubtitles provider uses. */
    private const val OS_KEY = "uyBLgFD17MgrYmA0gSXoKllMJBelOYj2"
    private const val OS_HOST = "https://api.opensubtitles.com/api/v1"
    private const val OS_UA = "CloudStreamDesktop v1.3"

    private val json = Json { ignoreUnknownKeys = true }

    /** SubDL expects upper-case two letter codes; anything unknown falls back to English. */
    fun subdlLanguage(code: String?): String {
        val short = code.orEmpty().trim().lowercase(Locale.US).substringBefore('-')
        return when (short) {
            "ar", "bg", "zh", "hr", "cs", "da", "nl", "fa", "fi", "fr", "de", "el", "he",
            "hi", "hu", "id", "it", "ja", "ko", "no", "pl", "pt", "ro", "ru", "sr", "sk",
            "sl", "es", "sv", "th", "tr", "uk", "ur", "vi",
            -> short.uppercase(Locale.US)
            else -> "EN"
        }
    }

    /** OpenSubtitles uses the same two letter codes, but lower case. */
    fun openSubtitlesLanguage(code: String?): String = subdlLanguage(code).lowercase(Locale.US)

    // ------------------------------------------------------------------ search

    suspend fun search(
        provider: String,
        query: String,
        language: String,
        subdlApiKey: String = "",
        openSubtitlesToken: String? = null,
        season: Int? = null,
        episode: Int? = null,
    ): List<SubtitleHit> = withContext(Dispatchers.IO) {
        val text = query.trim()
        if (text.isEmpty()) return@withContext emptyList()
        when (provider) {
            PROVIDER_SUBDL -> searchSubdl(text, language, subdlApiKey, season, episode)
            PROVIDER_OPENSUBTITLES -> searchOpenSubtitles(text, language, season, episode)
            else -> emptyList()
        }
    }

    private suspend fun searchSubdl(
        query: String,
        language: String,
        apiKey: String,
        season: Int?,
        episode: Int?,
    ): List<SubtitleHit> {
        val key = apiKey.trim()
        if (key.isEmpty()) return emptyList()
        val url = buildString {
            append("https://api.subdl.com/api/v1/subtitles")
            append("?api_key=").append(java.net.URLEncoder.encode(key, Charsets.UTF_8))
            append("&film_name=").append(java.net.URLEncoder.encode(query, Charsets.UTF_8))
            append("&languages=").append(subdlLanguage(language))
            append("&subs_per_page=30")
            if (season != null && episode != null) {
                append("&season_number=").append(season)
                append("&episode_number=").append(episode)
            }
        }
        val body = runCatching { Http.get(url) }.getOrNull() ?: return emptyList()
        return parseSubdl(body)
    }

    private suspend fun searchOpenSubtitles(
        query: String,
        language: String,
        season: Int?,
        episode: Int?,
    ): List<SubtitleHit> {
        val params = buildString {
            append("?query=").append(java.net.URLEncoder.encode(query, Charsets.UTF_8))
            append("&languages=").append(openSubtitlesLanguage(language))
            append("&order_by=download_count")
            if (season != null && episode != null) {
                append("&season_number=").append(season)
                append("&episode_number=").append(episode)
            }
        }
        val body = runCatching {
            Http.send(
                method = "GET",
                url = "$OS_HOST/subtitles$params",
                headers = mapOf("Api-Key" to OS_KEY, "User-Agent" to OS_UA, "Accept" to "application/json"),
            )
        }.getOrNull() ?: return emptyList()
        return parseOpenSubtitles(body)
    }

    /** SubDL answers `{"status":true,"subtitles":[{"url":"/subtitle/x.zip", ...}]}`. */
    internal fun parseSubdl(body: String): List<SubtitleHit> = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        val items = root["subtitles"]?.jsonArray ?: return@runCatching emptyList()
        items.mapNotNull { element ->
            val item = element.jsonObject
            val path = item["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val name = item["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            SubtitleHit(
                provider = PROVIDER_SUBDL,
                title = name.ifBlank { item["release"]?.jsonPrimitive?.contentOrNull.orEmpty() },
                language = item["language"]?.jsonPrimitive?.contentOrNull
                    ?: item["lang"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                release = item["release"]?.jsonPrimitive?.contentOrNull,
                url = if (path.startsWith("http")) path else "https://dl.subdl.com$path",
            )
        }
    }.getOrDefault(emptyList())

    /**
     * OpenSubtitles answers with `data[].attributes`, where the files array holds the ids a
     * download request needs and the feature details hold the episode it belongs to.
     */
    internal fun parseOpenSubtitles(body: String): List<SubtitleHit> = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        val items = root["data"]?.jsonArray ?: return@runCatching emptyList()
        items.mapNotNull { element ->
            val attributes = element.jsonObject["attributes"]?.jsonObject ?: return@mapNotNull null
            val fileId = attributes["files"]?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("file_id")?.jsonPrimitive?.intOrNull
                ?: return@mapNotNull null
            SubtitleHit(
                provider = PROVIDER_OPENSUBTITLES,
                title = attributes["feature_details"]?.jsonObject
                    ?.get("title")?.jsonPrimitive?.contentOrNull.orEmpty(),
                language = attributes["language"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                release = attributes["release"]?.jsonPrimitive?.contentOrNull,
                url = "$OS_HOST/download",
                fileId = fileId,
            )
        }
    }.getOrDefault(emptyList())

    // ------------------------------------------------------------------ accounts

    /** Exchanges an OpenSubtitles login for the JWT downloads need; only the token is kept. */
    suspend fun openSubtitlesLogin(username: String, password: String): Result<OpenSubtitlesSession> =
        runCatching {
            require(username.isNotBlank() && password.isNotBlank()) { "Enter your OpenSubtitles username and password" }
            val payload = buildString {
                append("{\"username\":\"").append(jsonEscape(username.trim()))
                append("\",\"password\":\"").append(jsonEscape(password)).append("\"}")
            }
            val body = Http.send(
                method = "POST",
                url = "$OS_HOST/login",
                body = payload,
                contentType = "application/json",
                headers = mapOf("Api-Key" to OS_KEY, "User-Agent" to OS_UA),
            )
            val token = json.parseToJsonElement(body).jsonObject["token"]?.jsonPrimitive?.contentOrNull
                ?: error("OpenSubtitles did not return a login token")
            val allowed = json.parseToJsonElement(body).jsonObject["user"]?.jsonObject
                ?.get("allowed_downloads")?.jsonPrimitive?.intOrNull
            OpenSubtitlesSession(token = token, username = username.trim(), allowedDownloads = allowed)
        }

    // ------------------------------------------------------------------ download

    suspend fun download(hit: SubtitleHit, openSubtitlesToken: String? = null): Path? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = when (hit.provider) {
                    PROVIDER_OPENSUBTITLES -> {
                        val fileId = hit.fileId ?: error("No file id on this result")
                        val token = openSubtitlesToken?.takeIf { it.isNotBlank() }
                            ?: error("Sign in to OpenSubtitles in Settings → Player to download")
                        val response = Http.send(
                            method = "POST",
                            url = "$OS_HOST/download",
                            body = "{\"file_id\":$fileId,\"sub_format\":\"srt\"}",
                            contentType = "application/json",
                            headers = mapOf(
                                "Api-Key" to OS_KEY,
                                "User-Agent" to OS_UA,
                                "Authorization" to "Bearer $token",
                            ),
                        )
                        val link = parseOpenSubtitlesDownload(response)
                            ?: error("OpenSubtitles returned no download link")
                        Http.getBytes(link)
                    }
                    else -> Http.getBytes(hit.url)
                }
                val (name, content) = extractSubtitle(bytes) ?: return@runCatching null
                val extension = name.substringAfterLast('.', "srt").lowercase(Locale.US)
                    .takeIf { it in Subtitles.extensions } ?: "srt"
                val target = Subtitles.cacheDir().resolve(
                    "sub-${Integer.toHexString(hit.url.hashCode())}-${hit.fileId ?: 0}.$extension",
                )
                Files.createDirectories(target.parent)
                val text = Subtitles.decodeSubtitle(content)
                check(text.isNotBlank()) { "empty subtitle file" }
                Files.writeString(target, text, Charsets.UTF_8)
                target
            }.getOrNull()
        }

    internal fun parseOpenSubtitlesDownload(body: String): String? = runCatching {
        json.parseToJsonElement(body).jsonObject["link"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    /** Zips are the usual shape; a bare .srt is accepted too. */
    internal fun extractSubtitle(bytes: ByteArray): Pair<String, ByteArray>? {
        if (bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name.substringAfterLast('/')
                    val extension = name.substringAfterLast('.', "").lowercase(Locale.US)
                    if (extension !in Subtitles.extensions) continue
                    return name to zip.readBytes()
                }
            }
            return null
        }
        val text = String(bytes, 0, minOf(bytes.size, 512), Charsets.ISO_8859_1)
        return if (text.contains("-->")) "subtitle.srt" to bytes else null
    }

    private fun jsonEscape(value: String): String = buildString {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character < ' ') append("\\u%04x".format(character.code)) else append(character)
            }
        }
    }
}

/** What an OpenSubtitles sign-in leaves behind: a token and the account it belongs to. */
data class OpenSubtitlesSession(
    val token: String,
    val username: String,
    val allowedDownloads: Int? = null,
)
