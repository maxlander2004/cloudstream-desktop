package app.cloudstream.desktop.core

import app.cloudstream.desktop.api.SubtitleTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.Locale

/**
 * A subtitle that can be turned on in the player: a file on disk, a URL to fetch, or one of
 * the tracks already embedded in the video ([vlcTrack]).
 */
data class SubtitleOption(
    val label: String,
    val language: String? = null,
    val url: String? = null,
    val file: Path? = null,
    val headers: Map<String, String> = emptyMap(),
    /** VLC's own track id, for a subtitle packed into the media itself. */
    val vlcTrack: Int? = null,
) {
    /** Stable identity, so the same track is not listed twice. */
    val key: String get() = file?.toString() ?: url ?: vlcTrack?.toString() ?: label
}

/**
 * Subtitle handling shared by the player and the download queue.
 *
 * Upstream leans on ExoPlayer's `SubtitleView` and juniversalchardet; here VLC renders the
 * subtitles, so this only has to find the tracks, fetch them and hand VLC a local file. Every
 * file is normalised to UTF-8 on the way in, because VLC's default is UTF-8 while plenty of
 * `.srt` files in the wild are Windows-1252.
 */
object Subtitles {
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) CloudStreamDesktop/1.0"

    /** Extensions VLC can open as a subtitle slave. */
    val extensions = listOf("srt", "vtt", "ass", "ssa", "sub", "ttml", "dfxp", "smi")

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    /** Fetched subtitles live here; the folder is a cache and safe to delete. */
    fun cacheDir(): Path =
        Paths.get(System.getProperty("user.home"), ".cloudstream-desktop", "subtitles")

    /** `srt`/`vtt`/… from the URL's file name; `srt` when the URL carries no usable suffix. */
    fun extensionFor(url: String): String {
        val name = runCatching { URI.create(url).path }.getOrNull()
            ?.substringAfterLast('/')
            .orEmpty()
        val extension = name.substringAfterLast('.', "").lowercase(Locale.US)
        return extension.takeIf { it in extensions } ?: "srt"
    }

    /**
     * The local file behind a `file:` URI or a plain path. Null for anything remote, which is
     * how the player tells a downloaded episode from a streamed one.
     */
    fun localPathOf(url: String): Path? {
        val raw = url.trim()
        if (raw.isEmpty()) return null
        if (raw.contains("://") && !raw.startsWith("file:", ignoreCase = true)) return null
        val path = runCatching {
            if (raw.startsWith("file:", ignoreCase = true)) Paths.get(URI.create(raw)) else Paths.get(raw)
        }.getOrNull() ?: return null
        return path.takeIf { Files.isRegularFile(it) }
    }

    /**
     * Subtitle files sitting next to [video] — what the Downloads screen saves, and what a user
     * gets by dropping a `.srt` beside a file. `Show S01E02.mp4` matches `Show S01E02.en.srt`;
     * the `en` part becomes the track's language.
     */
    fun sidecarsFor(video: Path): List<SubtitleOption> {
        val dir = video.parent ?: return emptyList()
        val base = video.fileName.toString().substringBeforeLast('.').lowercase(Locale.US)
        if (base.isEmpty() || !Files.isDirectory(dir)) return emptyList()

        val files = runCatching {
            Files.list(dir).use { stream ->
                stream.filter { Files.isRegularFile(it) }
                    .filter { path ->
                        val name = path.fileName.toString().lowercase(Locale.US)
                        val stem = name.substringBeforeLast('.', "")
                        name.substringAfterLast('.', "") in extensions &&
                            (stem == base || stem.startsWith("$base."))
                    }
                    .sorted()
                    .collect(java.util.stream.Collectors.toList())
            }
        }.getOrDefault(emptyList())

        return files.map { path ->
            val stem = path.fileName.toString().substringBeforeLast('.')
            val suffix = stem.lowercase(Locale.US)
                .takeIf { it.startsWith(base) }
                ?.substring(base.length)
                ?.trim('.', '_', '-', ' ')
                .orEmpty()
            SubtitleOption(
                label = suffix.ifBlank { stem },
                language = suffix.takeIf { it.isNotBlank() },
                file = path,
            )
        }
    }

    /** The track the user asked for by [language]; the first one when nothing matches. */
    fun preferred(options: List<SubtitleOption>, language: String): SubtitleOption? {
        if (options.isEmpty()) return null
        val wanted = language.trim().lowercase(Locale.US)
        if (wanted.isEmpty()) return options.first()
        return options.firstOrNull { option ->
            val tag = option.language?.trim()?.lowercase(Locale.US).orEmpty()
            val label = option.label.lowercase(Locale.US)
            tag == wanted || tag.startsWith("$wanted-") || label == wanted ||
                label.startsWith(wanted)
        } ?: options.first()
    }

    /** Converts a source track into a player option. */
    fun optionOf(track: SubtitleTrack): SubtitleOption = SubtitleOption(
        label = track.label,
        language = track.language,
        url = track.url,
        headers = track.headers,
    )

    /**
     * Makes [option] available on disk and returns the file VLC should read: the local file
     * itself, or a UTF-8 copy of the downloaded one. Null when the download failed.
     */
    suspend fun download(option: SubtitleOption): Path? = withContext(Dispatchers.IO) {
        option.file?.let { file -> return@withContext file.takeIf { Files.isRegularFile(it) } }
        val url = option.url ?: return@withContext null

        runCatching {
            val target = cacheDir().resolve("sub-${digest(url)}.${extensionFor(url)}")
            if (Files.isRegularFile(target) && Files.size(target) > 0) return@runCatching target

            Files.createDirectories(target.parent)
            val builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", UA)
                .GET()
            option.headers.forEach { (key, value) -> builder.header(key, value) }
            val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
            check(response.statusCode() in 200..299) { "HTTP ${response.statusCode()}" }

            val text = decodeSubtitle(response.body())
            check(text.isNotBlank()) { "empty subtitle file" }
            Files.writeString(target, text, Charsets.UTF_8)
            target
        }.getOrNull()
    }

    /**
     * Saves [option] next to [video] as `<name>.<language>.<ext>` — the file the player finds
     * again through [sidecarsFor] on the next playback.
     */
    suspend fun saveBeside(video: Path, option: SubtitleOption): Path? {
        val source = download(option) ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val extension = source.fileName.toString().substringAfterLast('.', "srt")
                val suffix = sanitize(option.language ?: option.label).ifBlank { "sub" }
                val base = video.fileName.toString().substringBeforeLast('.')
                val target = video.resolveSibling("$base.$suffix.$extension")
                if (source.toAbsolutePath() != target.toAbsolutePath()) {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                }
                target
            }.getOrNull()
        }
    }

    /**
     * Subtitle text as UTF-8. A BOM is honoured, strict UTF-8 is tried first and Windows-1252
     * is the fallback that covers the legacy `.srt` files the sources still hand out.
     */
    fun decodeSubtitle(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        decodeStrict(bytes, Charsets.UTF_8)?.let { return it.removePrefix("\uFEFF") }
        return decodeStrict(bytes, Charset.forName("windows-1252"))
            ?: String(bytes, Charsets.ISO_8859_1)
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String? = runCatching {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }.getOrNull()

    private fun sanitize(value: String): String =
        value.replace(Regex("[^A-Za-z0-9_-]"), "_").trim('_').take(24)

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)
}
