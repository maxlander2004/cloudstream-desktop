package app.cloudstream.desktop.core

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Torrent playback, the desktop answer to upstream's torrent extractors.
 *
 * BitTorrent itself is not reimplemented here: the app drives **aria2c**, which is the same
 * idea as the Android build depending on the platform's torrent stack. aria2 has no
 * sequential-download mode, so the head pieces of each file are prioritised instead — enough
 * for a player to start while the rest arrives. A small HTTP bridge on localhost serves the
 * growing file to VLC, blocking on reads until the bytes it needs exist.
 *
 * When aria2c is not installed the player says so instead of pretending the stream failed —
 * there is no way to fetch a magnet link without a torrent client.
 */
object TorrentEngine {
    /** Where the local bridge listens; only ever bound to 127.0.0.1. */
    const val PORT = 47601

    private const val VIDEO_EXTENSIONS =
        "mp4,mkv,avi,webm,mov,m4v,ts,mpg,mpeg,flv,wmv"

    private val streams = ConcurrentHashMap<String, StreamState>()

    private data class StreamState(val file: Path, val process: Process?)

    private val server: HttpServer by lazy {
        HttpServer.create(InetSocketAddress("127.0.0.1", PORT), 0).apply {
            createContext("/torrent") { exchange -> handle(exchange) }
            executor = java.util.concurrent.Executors.newCachedThreadPool { runnable ->
                Thread(runnable, "torrent-bridge").apply { isDaemon = true }
            }
            start()
        }
    }

    // ------------------------------------------------------------------ detection

    /** A magnet link, or a URL/file that points at a `.torrent` — both open the same way. */
    fun isTorrentUrl(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.startsWith("magnet:", ignoreCase = true) ||
            trimmed.substringBefore('?').substringBefore('#').endsWith(".torrent", ignoreCase = true)
    }

    /**
     * The name a magnet carries in its `dn` parameter. A magnet is an opaque URI, so its query
     * is read from the text — [java.net.URI] reports no query part for `magnet:?…`.
     */
    fun magnetName(magnet: String): String? = runCatching {
        magnet.trim()
            .substringAfter('?', "")
            .split('&')
            .firstOrNull { it.startsWith("dn=") }
            ?.substringAfter("dn=")
            ?.let { URLDecoder.decode(it, Charsets.UTF_8) }
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /** Path to aria2c: the configured file/folder first, then whatever is on PATH. */
    fun binary(configuredPath: String): String? {
        val configured = configuredPath.trim()
        if (configured.isNotEmpty()) {
            val candidate = runCatching { Paths.get(configured) }.getOrNull()
            if (candidate != null) {
                if (Files.isRegularFile(candidate)) return candidate.toString()
                if (Files.isDirectory(candidate)) {
                    listOf("aria2c", "aria2c.exe")
                        .map { candidate.resolve(it) }
                        .firstOrNull { Files.isRegularFile(it) }
                        ?.let { return it.toString() }
                }
            }
        }
        return listOf("aria2c", "aria2c.exe").firstOrNull { onPath(it) }
    }

    private fun onPath(name: String): Boolean {
        val path = System.getenv("PATH").orEmpty()
        return path.split(java.io.File.pathSeparator)
            .asSequence()
            .filter { it.isNotBlank() }
            .map { runCatching { Paths.get(it, name) }.getOrNull() }
            .any { it != null && Files.isRegularFile(it) && Files.isExecutable(it) }
    }

    /** One line for the settings screen: where aria2c is, or why torrents are off. */
    fun status(configuredPath: String): String =
        binary(configuredPath)?.let { "aria2c found at $it" }
            ?: "aria2c was not found — install it (or point the path below at it) to play torrents"

    fun unavailableMessage(configuredPath: String): String =
        if (binary(configuredPath) == null) {
            "This link is a torrent, and aria2c was not found. Install aria2 and set its path in " +
                "Settings → Player, then try again."
        } else {
            "The torrent did not start"
        }

    // ------------------------------------------------------------------ arguments

    /**
     * The aria2c invocation. Every flag here is checked against a real aria2 — `--bt-sequential`
     * looks like the right option but does not exist, and aria2 exits immediately on an option it
     * does not know, which is exactly the failure this file used to produce.
     */
    internal fun commandFor(executable: String, folder: Path, magnet: String): List<String> = listOf(
        executable,
        "--dir=${folder.toAbsolutePath()}",
        // aria2 downloads pieces in order it does not know about, so pull the head of each file
        // first: that is what a player needs to open the stream.
        "--bt-prioritize-piece=head=16M",
        "--seed-time=0",
        "--file-allocation=none",
        "--summary-interval=0",
        "--console-log-level=warn",
        "--bt-save-metadata=true",
        "--auto-file-renaming=false",
        "--allow-overwrite=true",
        "--disk-cache=64M",
        magnet,
    )

    // ------------------------------------------------------------------ streaming

    /**
     * Starts fetching the magnet and returns the localhost URL VLC should open. Fails fast when
     * aria2 cannot run at all — an unknown option or a broken binary shows up within a second,
     * and waiting out the whole file timeout would only hide the reason.
     */
    fun prepare(magnet: String, downloadDir: String, aria2Path: String): Result<String> = runCatching {
        val executable = binary(aria2Path)
            ?: error(unavailableMessage(aria2Path))
        val root = Paths.get(downloadDir.ifBlank { defaultDir() })
        Files.createDirectories(root)

        val key = Integer.toHexString(magnet.hashCode())
        if (streams.containsKey(key)) return Result.success(urlFor(key))

        val folder = root.resolve(key)
        Files.createDirectories(folder)
        val log = folder.resolve("aria2.log")

        val process = ProcessBuilder(commandFor(executable, folder, magnet))
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()))
            .start()

        // aria2 dies instantly on an option it does not recognise; say why instead of spinning.
        if (process.waitFor(1500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            val exit = process.exitValue()
            val reason = logTail(log).takeIf { it.isNotBlank() }
                ?: "aria2c exited with code $exit"
            error("aria2c could not start: $reason")
        }

        val file = waitForFile(folder, process, timeoutMs = 60_000)
            ?: run {
                val reason = logTail(log)
                process.destroyForcibly()
                error(
                    if (reason.isBlank()) "aria2c did not produce a file within a minute"
                    else "aria2c gave up: $reason",
                )
            }

        streams[key] = StreamState(file, process)
        // Make sure the bridge is listening before the player is handed the URL.
        server
        urlFor(key)
    }

    private fun logTail(log: Path): String = runCatching {
        if (!Files.isRegularFile(log)) return@runCatching ""
        Files.readAllLines(log)
            .last { it.isNotBlank() }
            .trim()
    }.getOrDefault("")

    private fun urlFor(key: String) = "http://127.0.0.1:$PORT/torrent/$key"

    fun defaultDir(): String =
        Paths.get(System.getProperty("user.home"), ".cloudstream-desktop", "torrents").toString()

    /** Stops every fetch and drops the bridge entries; called when the player closes. */
    fun stopAll() {
        streams.values.forEach { state -> runCatching { state.process?.destroyForcibly() } }
        streams.clear()
    }

    private fun waitForFile(folder: Path, process: Process?, timeoutMs: Long): Path? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val candidate = runCatching { largestVideoFile(folder) }.getOrNull()
            if (candidate != null) return candidate
            if (process != null && !process.isAlive && System.currentTimeMillis() > deadline - timeoutMs + 8000) {
                // It survived startup but stopped before writing anything — give up early.
                return null
            }
            Thread.sleep(400)
        }
        return null
    }

    private fun largestVideoFile(folder: Path): Path? {
        if (!Files.isDirectory(folder)) return null
        return Files.walk(folder, 3).use { walk ->
            walk.filter { Files.isRegularFile(it) }
                .filter { path ->
                    path.fileName.toString().substringAfterLast('.', "")
                        .lowercase(Locale.US) in VIDEO_EXTENSIONS.split(',')
                }
                .filter { runCatching { Files.size(it) > 0 }.getOrDefault(false) }
                .max(compareBy { runCatching { Files.size(it) }.getOrDefault(0L) })
                .orElse(null)
        }
    }

    /** Still downloading while aria2's control file is beside the media file. */
    private fun downloading(file: Path, process: Process?): Boolean =
        Files.exists(Paths.get(file.toString() + ".aria2")) ||
            (process?.isAlive == true && runCatching { Files.size(file) }.getOrDefault(0L) == 0L)

    // ------------------------------------------------------------------ HTTP bridge

    private fun handle(exchange: HttpExchange) {
        val key = exchange.requestURI.path.substringAfterLast('/')
        val state = streams[key] ?: return notFound(exchange)
        val file = state.file
        val size = runCatching { Files.size(file) }.getOrDefault(0L)
        val range = parseRange(exchange.requestHeaders.getFirst("Range"), size)
            ?: return notFound(exchange)

        val start = range.first
        if (!waitForBytes(file, start + 1, 60_000)) return notFound(exchange)

        exchange.responseHeaders.add("Content-Type", contentType(file))
        exchange.responseHeaders.add("Accept-Ranges", "bytes")
        exchange.responseHeaders.add("Connection", "close")
        val end = range.second
        if (end != null) {
            exchange.responseHeaders.add("Content-Range", "bytes $start-$end/*")
        }
        // 0 means chunked: the file is still growing, so there is no length to declare.
        exchange.sendResponseHeaders(if (end != null) 206 else 200, 0)

        runCatching {
            exchange.responseBody.use { output ->
                RandomAccessFile(file.toFile(), "r").use { raf ->
                    raf.seek(start)
                    var position = start
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (end != null && position > end) break
                        val available = runCatching { Files.size(file) }.getOrDefault(0L)
                        if (position < available) {
                            val wanted = if (end != null) {
                                minOf(buffer.size.toLong(), end - position + 1).toInt()
                            } else {
                                buffer.size
                            }
                            val read = raf.read(buffer, 0, wanted.coerceAtLeast(1))
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            position += read
                            continue
                        }
                        if (!downloading(file, state.process)) break
                        Thread.sleep(250)
                    }
                }
            }
        }
        runCatching { exchange.close() }
    }

    private fun notFound(exchange: HttpExchange) {
        runCatching {
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
    }

    private fun waitForBytes(file: Path, needed: Long, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (runCatching { Files.size(file) }.getOrDefault(0L) >= needed) return true
            if (!downloading(file, null)) {
                // aria2 stopped early (bad magnet, no peers) — do not hold the player open.
                return runCatching { Files.size(file) }.getOrDefault(0L) >= needed
            }
            Thread.sleep(200)
        }
        return false
    }

    /**
     * `Range: bytes=start-` or `bytes=start-end`, clamped to what is on disk. Null when the
     * range starts past the end of the data.
     */
    internal fun parseRange(header: String?, size: Long): Pair<Long, Long?>? {
        if (header.isNullOrBlank()) return 0L to null
        val value = header.substringAfter("bytes=", "").trim()
        if (value.isEmpty()) return 0L to null
        val start = value.substringBefore('-').trim().toLongOrNull() ?: 0L
        val end = value.substringAfter('-', "").trim().toLongOrNull()
        if (start < 0 || (size > 0 && start >= size)) return null
        if (end != null && end < start) return null
        return start to end
    }

    internal fun contentType(file: Path): String = when (
        file.fileName.toString().substringAfterLast('.', "").lowercase(Locale.US)
    ) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "ts" -> "video/mp2t"
        "flv" -> "video/x-flv"
        "wmv" -> "video/x-ms-wmv"
        "mpg", "mpeg" -> "video/mpeg"
        else -> "application/octet-stream"
    }
}
