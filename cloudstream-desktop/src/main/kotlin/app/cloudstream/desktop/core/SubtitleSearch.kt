package app.cloudstream.desktop.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    /** Remote archive path; the download is a zip holding the subtitle files. */
    val url: String,
) {
    val label: String
        get() = buildString {
            append(language.ifBlank { "Unknown" })
            release?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
            if (release.isNullOrBlank()) title.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        }
}

/**
 * Online subtitle search, the counterpart of the original's subtitle providers.
 *
 * Only SubDL is wired up: it is the one provider whose public API works with a plain user API
 * key, no server-side secret and no login token. The original's OpenSubtitles and SubSource
 * providers need credentials this build cannot ship, so they are not pretended to work — the
 * settings screen says which provider is in use.
 */
object SubtitleSearch {
    const val PROVIDER_SUBDL = "subdl"

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

    /** Searches by title. [episode] narrows a series result to that episode when it is set. */
    suspend fun search(
        query: String,
        language: String,
        apiKey: String,
        season: Int? = null,
        episode: Int? = null,
    ): List<SubtitleHit> = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isEmpty()) return@withContext emptyList()
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return@withContext emptyList()

        val url = buildString {
            append("https://api.subdl.com/api/v1/subtitles")
            append("?api_key=").append(java.net.URLEncoder.encode(key, Charsets.UTF_8))
            append("&film_name=").append(java.net.URLEncoder.encode(cleaned, Charsets.UTF_8))
            append("&languages=").append(subdlLanguage(language))
            append("&subs_per_page=30")
            // SubDL understands season/episode only on an id search, so the title carries it.
            if (season != null && episode != null) {
                append("&season_number=").append(season)
                append("&episode_number=").append(episode)
            }
        }
        val body = runCatching { Http.get(url) }.getOrNull() ?: return@withContext emptyList()
        parseSubdl(body)
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
     * Fetches the archive and returns the subtitle file it holds, normalised to UTF-8 in the
     * subtitle cache. Null when the archive had no readable subtitle in it.
     */
    suspend fun download(hit: SubtitleHit): Path? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = Http.getBytes(hit.url)
            val (name, content) = extractSubtitle(bytes) ?: return@runCatching null
            val extension = name.substringAfterLast('.', "srt").lowercase(Locale.US)
                .takeIf { it in Subtitles.extensions } ?: "srt"
            val target = Subtitles.cacheDir().resolve(
                "sub-${Integer.toHexString(hit.url.hashCode())}.$extension",
            )
            Files.createDirectories(target.parent)
            val text = Subtitles.decodeSubtitle(content)
            check(text.isNotBlank()) { "empty subtitle file" }
            Files.writeString(target, text, Charsets.UTF_8)
            target
        }.getOrNull()
    }

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
}
