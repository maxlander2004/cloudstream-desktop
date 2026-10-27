package app.cloudstream.desktop.core

import app.cloudstream.desktop.api.SubtitleTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.RandomAccessFile
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.deleteIfExists
import kotlin.random.Random

enum class DownloadState { QUEUED, RUNNING, DONE, FAILED }

data class DownloadTask(
    val id: String,
    val title: String,
    val url: String,
    val fileName: String,
    val state: DownloadState = DownloadState.QUEUED,
    val totalBytes: Long = -1,
    val doneBytes: Long = 0,
    val error: String? = null,
    /** Subtitle files saved beside the finished download, e.g. `Movie.en.srt`. */
    val subtitles: List<String> = emptyList(),
) {
    val progress: Float
        get() = if (totalBytes > 0) (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    /** Done and a file a player can open. */
    val playable: Boolean
        get() = state == DownloadState.DONE
}

/**
 * The desktop counterpart of the original's Downloads screen: a queue of direct-file
 * downloads. Each file may use several ranged connections (the original's *Concurrent
 * connections*) and up to *Parallel downloads* files run at once — the same two sliders the
 * Android General settings expose.
 */
object DownloadManager {
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) CloudStreamDesktop/1.0"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(20))
        .build()

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks

    /** Running jobs, so a queued download can be cancelled. */
    private val jobs = java.util.concurrent.ConcurrentHashMap<String, Job>()

    /** Permits for parallel downloads; replaced whenever a download is queued. */
    @Volatile
    private var semaphore = Semaphore(3)

    private fun mutate(transform: (List<DownloadTask>) -> List<DownloadTask>) {
        while (true) {
            val current = _tasks.value
            val updated = transform(current)
            if (_tasks.compareAndSet(current, updated)) return
        }
    }

    private fun patch(id: String, transform: (DownloadTask) -> DownloadTask) {
        mutate { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    fun clearFinished() {
        mutate { list -> list.filterNot { it.state == DownloadState.DONE || it.state == DownloadState.FAILED } }
    }

    /** Stops a running or queued download; the task stays in the list as failed/cancelled. */
    fun cancel(id: String) {
        jobs.remove(id)?.cancel()
        patch(id) { it.copy(state = DownloadState.FAILED, error = "Cancelled") }
    }

    /** Removes a task, deleting the file of a finished download when [dir] is given. */
    fun remove(id: String, dir: Path? = null) {
        val task = _tasks.value.firstOrNull { it.id == id }
        jobs.remove(id)?.cancel()
        if (task != null && task.state == DownloadState.DONE && dir != null) {
            runCatching { Files.deleteIfExists(dir.resolve(task.fileName)) }
        }
        mutate { list -> list.filterNot { it.id == id } }
    }

    /**
     * Queues a download into [Settings.downloadsDir]. Returns the queued task's id.
     */
    fun add(
        settings: Settings,
        title: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        subtitles: List<SubtitleTrack> = emptyList(),
    ): String {
        val id = "dl-${Random.nextLong(Long.MAX_VALUE)}"
        val fileName = guessFileName(title, url)
        mutate { it + DownloadTask(id = id, title = title, url = url, fileName = fileName) }

        semaphore = Semaphore(settings.parallelDownloads.coerceIn(1, 10))
        val connections = settings.concurrentConnections.coerceIn(1, 10)
        val dir = Paths.get(settings.downloadsDir)

        val job = scope.launch {
            semaphore.withPermit {
                patch(id) { it.copy(state = DownloadState.RUNNING) }
                val result = runCatching { download(id, url, headers, dir.resolve(fileName), connections) }
                result.fold(
                    onSuccess = { total ->
                        patch(id) { it.copy(state = DownloadState.DONE, totalBytes = total, doneBytes = total) }
                        // The file is complete, so the subtitle tracks can be stored beside it —
                        // that is what "Download subtitles" in General turns on.
                        if (settings.downloadSubtitles && subtitles.isNotEmpty()) {
                            val saved = subtitles.mapNotNull { track ->
                                Subtitles.saveBeside(
                                    dir.resolve(fileName),
                                    Subtitles.optionOf(track),
                                )
                            }
                            patch(id) {
                                it.copy(subtitles = saved.map { path -> path.fileName.toString() })
                            }
                        }
                    },
                    onFailure = { error ->
                        patch(id) {
                            it.copy(state = DownloadState.FAILED, error = error.message ?: "Download failed")
                        }
                    },
                )
            }
        }
        jobs[id] = job
        return id
    }

    /** Downloads with ranged parallel connections when the server supports them. */
    private suspend fun download(
        id: String,
        url: String,
        headers: Map<String, String>,
        target: Path,
        connections: Int,
    ): Long {
        Files.createDirectories(target.parent)

        // Probe: ask for the first byte. A 206 tells us the total size and that ranges work.
        val probeRequest = applyHeaders(
            HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", UA)
                .header("Range", "bytes=0-0"),
            headers,
        ).GET().build()
        val probe = client.send(probeRequest, HttpResponse.BodyHandlers.ofInputStream())
        val totalFromRange = if (probe.statusCode() == 206) {
            probe.headers().firstValue("content-range").orElse("")
                .substringAfter("/", "").trim().toLongOrNull()
        } else {
            null
        }
        probe.body().close()

        return if (totalFromRange != null && totalFromRange > 0 && connections > 1) {
            downloadRanged(id, url, headers, target, totalFromRange, connections)
        } else {
            downloadSingle(id, url, headers, target)
        }
    }

    private fun applyHeaders(
        builder: HttpRequest.Builder,
        headers: Map<String, String>,
    ): HttpRequest.Builder {
        headers.forEach { (key, value) -> builder.header(key, value) }
        return builder
    }

    private suspend fun downloadSingle(
        id: String,
        url: String,
        headers: Map<String, String>,
        target: Path,
    ): Long {
        val request = applyHeaders(
            HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(30))
                .header("User-Agent", UA),
            headers,
        ).GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        check(response.statusCode() in 200..299) { "HTTP ${response.statusCode()}" }
        val declared = response.headers().firstValue("content-length").orElse("").toLongOrNull() ?: -1
        patch(id) { it.copy(totalBytes = declared) }

        var done = 0L
        val buffer = ByteArray(64 * 1024)
        Files.newOutputStream(target).use { out ->
            response.body().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    done += read
                    patch(id) { it.copy(doneBytes = done) }
                }
            }
        }
        return done
    }

    private suspend fun downloadRanged(
        id: String,
        url: String,
        headers: Map<String, String>,
        target: Path,
        total: Long,
        connections: Int,
    ): Long {
        java.nio.file.Files.newByteChannel(target, java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.WRITE).use { }
        RandomAccessFile(target.toFile(), "rw").use { file -> file.setLength(total) }

        val counter = AtomicLong(0)
        patch(id) { it.copy(totalBytes = total) }

        val chunkSize = total / connections
        // Children of the caller's job, so cancelling the download stops every connection.
        coroutineScope {
        val chunks = (0 until connections).map { index ->
            val start = index * chunkSize
            val end = if (index == connections - 1) total - 1 else start + chunkSize - 1
            launch {
                val request = applyHeaders(
                    HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofMinutes(30))
                        .header("User-Agent", UA)
                        .header("Range", "bytes=$start-$end"),
                    headers,
                ).GET().build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
                check(response.statusCode() == 206) { "HTTP ${response.statusCode()} (ranges rejected)" }

                RandomAccessFile(target.toFile(), "rw").use { file ->
                    file.seek(start)
                    val buffer = ByteArray(64 * 1024)
                    response.body().use { input ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            file.write(buffer, 0, read)
                            val soFar = counter.addAndGet(read.toLong())
                            patch(id) { it.copy(doneBytes = soFar) }
                        }
                    }
                }
            }
        }
        chunks.forEach { it.join() }
        }
        return total
    }

    /** Content-ish name from the URL path, falling back to the item title. */
    internal fun guessFileName(title: String, url: String): String {
        val last = runCatching { URI.create(url).path.substringAfterLast('/') }.getOrNull().orEmpty()
        val fromUrl = last.substringBefore('?').substringBefore('#')
        val name = if (fromUrl.contains('.') && fromUrl.length <= 120) fromUrl else {
            val cleaned = title.replace(Regex("[^A-Za-z0-9 ._\\-]"), "_").trim('_').ifBlank { "download" }
            val extension = last.substringAfterLast('.').takeIf {
                it.length in 2..5 && last.contains('.')
            }?.let { ".$it" } ?: ".mp4"
            cleaned + extension
        }
        return name.replace('/', '_').ifBlank { "download.mp4" }
    }

    /** Disk usage of the downloads folder, mirroring the original's storage bar. */
    data class Usage(val diskTotal: Long, val diskUsed: Long, val diskFree: Long, val folderBytes: Long)

    fun usage(dir: Path): Usage = runCatching {
        Files.createDirectories(dir)
        val store = Files.getFileStore(dir)
        val total = store.totalSpace
        val usable = store.usableSpace
        val folderBytes = Files.walk(dir).use { stream ->
            stream.filter { Files.isRegularFile(it) }.mapToLong { runCatching { Files.size(it) }.getOrDefault(0L) }.sum()
        }
        Usage(diskTotal = total, diskUsed = total - usable, diskFree = usable, folderBytes = folderBytes)
    }.getOrElse { Usage(0, 0, 0, 0) }
}
