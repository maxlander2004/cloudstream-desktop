package app.cloudstream.desktop.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlin.io.path.deleteIfExists
import kotlin.random.Random

enum class DownloadState { QUEUED, RUNNING, PAUSED, DONE, FAILED }

data class DownloadTask(
    val id: String,
    val title: String,
    val url: String,
    val fileName: String,
    val state: DownloadState = DownloadState.QUEUED,
    val totalBytes: Long = -1,
    val doneBytes: Long = 0,
    val error: String? = null,
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

    /** Permits for parallel downloads; replaced whenever a download is queued. */
    @Volatile
    private var semaphore = Semaphore(3)

    /** Everything needed to (re)start a task's download, kept so resume and retry can relaunch it. */
    private class Registration(
        val url: String,
        val headers: Map<String, String>,
        val target: Path,
        val connections: Int,
    )

    private val registrations = ConcurrentHashMap<String, Registration>()
    private val jobs = ConcurrentHashMap<String, Job>()
    /** Per-chunk byte counts of a ranged download, kept across pause/resume cycles. */
    private val chunkProgress = ConcurrentHashMap<String, ConcurrentHashMap<Int, Long>>()
    /**
     * Files a cancelled download should delete once its coroutine lets go of them. Windows
     * cannot unlink a file another handle still has open, so cancel's first attempt can fail
     * there and the deletion is retried when the job completes.
     */
    private val pendingDeletes = ConcurrentHashMap<String, Path>()

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

    /**
     * Queues a download into [Settings.downloadsDir]. Returns the queued task's id.
     */
    fun add(settings: Settings, title: String, url: String, headers: Map<String, String> = emptyMap()): String {
        val id = "dl-${Random.nextLong(Long.MAX_VALUE)}"
        val fileName = guessFileName(title, url)
        val target = Paths.get(settings.downloadsDir).resolve(fileName)
        val connections = settings.concurrentConnections.coerceIn(1, 10)

        mutate { it + DownloadTask(id = id, title = title, url = url, fileName = fileName) }
        registrations[id] = Registration(url, headers, target, connections)
        semaphore = Semaphore(settings.parallelDownloads.coerceIn(1, 10))
        launchDownload(id)
        return id
    }

    /**
     * Pauses a download: the running coroutine stops at the next buffer boundary, keeping
     * every byte already written so [resume] can continue from there.
     */
    fun pause(id: String) {
        patch(id) { if (it.state == DownloadState.RUNNING) it.copy(state = DownloadState.PAUSED) else it }
        jobs.remove(id)?.cancel()
    }

    /** Resumes a paused download from the byte it stopped at. */
    fun resume(id: String) {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        if (task.state != DownloadState.PAUSED) return
        patch(id) { it.copy(state = DownloadState.RUNNING, error = null) }
        launchDownload(id)
    }

    /**
     * The queue row's cancel button. An unfinished download leaves the queue and its partial
     * file is deleted; a finished one is only dropped from the list, its file is kept.
     */
    fun cancel(id: String) {
        val registration = registrations.remove(id)
        chunkProgress.remove(id)
        // Register the pending delete before cancelling: the job's completion handler is then
        // guaranteed to see it and retry once the coroutine has closed its file handles.
        if (registration != null) pendingDeletes[id] = registration.target
        jobs.remove(id)?.cancel()
        mutate { list -> list.filterNot { it.id == id } }
        if (registration != null && _tasks.value.none { it.id == id }) {
            if (runCatching { Files.deleteIfExists(registration.target) }.getOrDefault(false)) {
                pendingDeletes.remove(id)
            }
        }
    }

    /**
     * Retries a failed download from scratch: the partial file is deleted and the task is
     * re-queued through the normal download path.
     */
    fun retry(id: String, settings: Settings) {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        if (task.state != DownloadState.FAILED) return
        val registration = registrations[id]
        if (registration == null) {
            // No registration (e.g. restored queue): fall back to a fresh add.
            add(settings, task.title, task.url)
            cancel(id)
            return
        }
        chunkProgress.remove(id)
        runCatching { registration.target.deleteIfExists() }
        patch(id) { it.copy(state = DownloadState.QUEUED, totalBytes = -1, doneBytes = 0, error = null) }
        launchDownload(id)
    }

    /** Starts (or resumes) the download coroutine for [id] and records it in [jobs]. */
    private fun launchDownload(id: String) {
        val registration = registrations[id] ?: return
        val job = scope.launch {
            semaphore.withPermit {
                patch(id) { current ->
                    when (current.state) {
                        DownloadState.QUEUED, DownloadState.PAUSED ->
                            current.copy(state = DownloadState.RUNNING)
                        else -> current
                    }
                }
                try {
                    val total = download(id, registration)
                    patch(id) { task ->
                        // Only finish the task if it was not paused or cancelled mid-flight.
                        if (task.state == DownloadState.RUNNING) {
                            task.copy(state = DownloadState.DONE, totalBytes = total, doneBytes = total)
                        } else {
                            task
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    patch(id) { task ->
                        if (task.state == DownloadState.RUNNING || task.state == DownloadState.QUEUED) {
                            task.copy(state = DownloadState.FAILED, error = e.message ?: "Download failed")
                        } else {
                            task
                        }
                    }
                }
            }
        }
        jobs[id] = job
        job.invokeOnCompletion {
            jobs.remove(id, job)
            // A cancelled download's first delete may have failed on Windows while the
            // coroutine still held the file open; now the handles are closed, retry it.
            pendingDeletes.remove(id)?.let { target -> runCatching { Files.deleteIfExists(target) } }
        }
    }

    /** Downloads with ranged parallel connections when the server supports them. */
    private suspend fun download(id: String, registration: Registration): Long {
        val url = registration.url
        val headers = registration.headers
        val target = registration.target
        val connections = registration.connections
        Files.createDirectories(target.parent)
        val already = _tasks.value.firstOrNull { it.id == id }?.doneBytes ?: 0L

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
            downloadSingle(id, url, headers, target, already)
        }
    }

    private fun applyHeaders(
        builder: HttpRequest.Builder,
        headers: Map<String, String>,
    ): HttpRequest.Builder {
        headers.forEach { (key, value) -> builder.header(key, value) }
        return builder
    }

    /**
     * One connection writing the whole body, continuing at [resumeAt] when the server honours
     * ranges and restarting from zero when it does not.
     */
    private suspend fun downloadSingle(
        id: String,
        url: String,
        headers: Map<String, String>,
        target: Path,
        resumeAt: Long,
    ): Long {
        val request = applyHeaders(
            HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(30))
                .header("User-Agent", UA)
                .apply { if (resumeAt > 0) header("Range", "bytes=$resumeAt-") },
            headers,
        ).GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        check(response.statusCode() in 200..299) { "HTTP ${response.statusCode()}" }
        val resumed = resumeAt > 0 && response.statusCode() == 206
        var done = if (resumed) resumeAt else 0L
        val declared = response.headers().firstValue("content-length").orElse("").toLongOrNull() ?: -1L
        if (declared >= 0) patch(id) { it.copy(totalBytes = if (resumed) done + declared else declared) }

        val buffer = ByteArray(64 * 1024)
        RandomAccessFile(target.toFile(), "rw").use { file ->
            file.setLength(done)
            file.seek(done)
            response.body().use { input ->
                while (true) {
                    // Stop at the next buffer boundary when paused or cancelled.
                    if (!coroutineContext.isActive) break
                    val read = input.read(buffer)
                    if (read < 0) break
                    file.write(buffer, 0, read)
                    done += read
                    patch(id) { it.copy(doneBytes = done) }
                }
            }
        }
        return done
    }

    /** Several ranged connections sharing one file; each chunk resumes where it stopped. */
    private suspend fun downloadRanged(
        id: String,
        url: String,
        headers: Map<String, String>,
        target: Path,
        total: Long,
        connections: Int,
    ): Long {
        Files.createDirectories(target.parent)
        if (!Files.exists(target)) Files.createFile(target)
        RandomAccessFile(target.toFile(), "rw").use { file -> file.setLength(total) }

        val progress = chunkProgress.getOrPut(id) { ConcurrentHashMap() }
        val resumed = progress.values.sum()
        val counter = AtomicLong(resumed)
        patch(id) { it.copy(totalBytes = total, doneBytes = resumed) }

        val chunkSize = total / connections
        val connectionJobs = (0 until connections).map { index ->
            val chunkStart = index * chunkSize
            val chunkEnd = if (index == connections - 1) total - 1 else chunkStart + chunkSize - 1
            val already = progress[index] ?: 0L
            val start = chunkStart + already
            scope.launch {
                if (start > chunkEnd) return@launch // this chunk is already complete
                val request = applyHeaders(
                    HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofMinutes(30))
                        .header("User-Agent", UA)
                        .header("Range", "bytes=$start-$chunkEnd"),
                    headers,
                ).GET().build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
                check(response.statusCode() == 206) { "HTTP ${response.statusCode()} (ranges rejected)" }

                RandomAccessFile(target.toFile(), "rw").use { file ->
                    file.seek(start)
                    val buffer = ByteArray(64 * 1024)
                    var chunkDone = already
                    response.body().use { input ->
                        while (true) {
                            // Stop at the next buffer boundary when paused or cancelled.
                            if (_tasks.value.none { it.id == id && it.state == DownloadState.RUNNING }) break
                            val read = input.read(buffer)
                            if (read < 0) break
                            file.write(buffer, 0, read)
                            chunkDone += read
                            progress[index] = chunkDone
                            val soFar = counter.addAndGet(read.toLong())
                            patch(id) { it.copy(doneBytes = soFar) }
                        }
                    }
                }
            }
        }
        connectionJobs.forEach { it.join() }
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
