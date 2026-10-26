package app.cloudstream.desktop

import app.cloudstream.desktop.core.DownloadManager
import app.cloudstream.desktop.core.DownloadState
import app.cloudstream.desktop.core.Settings
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories

/**
 * Exercises the queue's pause/resume/cancel controls against a slow local stream, which is the
 * only way to check that a paused transfer really stops and resumes where it left off.
 */
class DownloadQueueTest {
    private val bodySize = 160 * 1024
    private lateinit var server: HttpServer
    private lateinit var downloads: Path
    private val previousHome = System.getProperty("user.home")

    @BeforeEach
    fun setUp() {
        System.setProperty("user.home", Files.createTempDirectory("cs-download-home").toString())
        downloads = Files.createTempDirectory("cs-downloads").also { it.createDirectories() }

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/slow") { exchange -> serveSlow(exchange) }
        server.createContext("/ranged") { exchange -> serveRanged(exchange) }
        server.executor = java.util.concurrent.Executors.newFixedThreadPool(8)
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
        DownloadManager.clearFinished()
        DownloadManager.tasks.value.forEach { DownloadManager.cancel(it.id) }
        System.setProperty("user.home", previousHome)
    }

    /** A body of [bodySize] bytes written in 8 KiB pieces with a small delay after each. */
    private fun serveSlow(exchange: HttpExchange) {
        exchange.sendResponseHeaders(200, bodySize.toLong())
        val chunk = ByteArray(8 * 1024) { 'a'.code.toByte() }
        runCatching {
            exchange.responseBody.use { out ->
                var written = 0
                while (written < bodySize) {
                    val size = minOf(chunk.size, bodySize - written)
                    out.write(chunk, 0, size)
                    out.flush()
                    written += size
                    Thread.sleep(10)
                }
            }
        }
        exchange.close()
    }

    /** The same slow body, but honouring Range so the manager opens several connections. */
    private fun serveRanged(exchange: HttpExchange) {
        val match = Regex("bytes=(\\d+)-(\\d+)").find(exchange.requestHeaders.getFirst("Range") ?: "")
        val start = match?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val end = match?.groupValues?.get(2)?.toIntOrNull()?.coerceAtMost(bodySize - 1) ?: (bodySize - 1)
        val length = end - start + 1

        exchange.responseHeaders.add("Content-Range", "bytes $start-$end/$bodySize")
        exchange.sendResponseHeaders(206, length.toLong())
        val chunk = ByteArray(8 * 1024) { 'b'.code.toByte() }
        runCatching {
            exchange.responseBody.use { out ->
                var written = 0
                while (written < length) {
                    val size = minOf(chunk.size, length - written)
                    out.write(chunk, 0, size)
                    out.flush()
                    written += size
                    Thread.sleep(10)
                }
            }
        }
        exchange.close()
    }

    private fun settings(): Settings = Settings().apply {
        downloadsDir = downloads.toString()
        parallelDownloads = 1
        concurrentConnections = 1
    }

    private fun awaitState(id: String, state: DownloadState, timeoutMs: Long = 10_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (DownloadManager.tasks.value.firstOrNull { it.id == id }?.state == state) return true
            Thread.sleep(20)
        }
        return false
    }

    private fun awaitProgress(id: String, atLeast: Long, timeoutMs: Long = 10_000): Long {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val task = DownloadManager.tasks.value.firstOrNull { it.id == id }
            val done = task?.doneBytes ?: 0
            if (done >= atLeast || task?.state == DownloadState.DONE) return done
            Thread.sleep(20)
        }
        return DownloadManager.tasks.value.firstOrNull { it.id == id }?.doneBytes ?: 0
    }

    @Test
    fun `pause stops the transfer and resume finishes the same file`() {
        val id = DownloadManager.add(settings(), "Slow stream", "http://127.0.0.1:${server.address.port}/slow")
        assertTrue(awaitProgress(id, 32L * 1024) > 0, "the download should start")

        DownloadManager.pause(id)
        Thread.sleep(200)
        val atPause = DownloadManager.tasks.value.first { it.id == id }
        assertTrue(atPause.state == DownloadState.PAUSED, "pausing should mark the row paused")

        Thread.sleep(400)
        val stillPaused = DownloadManager.tasks.value.first { it.id == id }
        assertTrue(
            stillPaused.doneBytes == atPause.doneBytes,
            "a paused download must not keep writing: ${atPause.doneBytes} -> ${stillPaused.doneBytes}",
        )

        DownloadManager.resume(id)
        assertTrue(awaitState(id, DownloadState.DONE), "resuming should finish the download")

        val file = downloads.resolve(DownloadManager.guessFileName("Slow stream", "http://127.0.0.1/slow"))
        assertTrue(Files.isRegularFile(file), "the finished download should be on disk")
        assertTrue(Files.size(file) == bodySize.toLong(), "the whole body should have been written")
    }

    @Test
    fun `pause and resume stop every connection of a ranged download`() {
        val settings = settings().apply { concurrentConnections = 3 }
        val id = DownloadManager.add(settings, "Ranged stream", "http://127.0.0.1:${server.address.port}/ranged")
        assertTrue(awaitProgress(id, 32L * 1024) > 0, "the ranged download should start")

        // Three connections writing at once means the total has to be observed per file.
        val atPause = DownloadManager.tasks.value.first { it.id == id }
        DownloadManager.pause(id)
        Thread.sleep(250)
        val paused = DownloadManager.tasks.value.first { it.id == id }
        assertTrue(paused.state == DownloadState.PAUSED, "pausing should mark the row paused")
        Thread.sleep(400)
        val stillPaused = DownloadManager.tasks.value.first { it.id == id }
        assertTrue(
            stillPaused.doneBytes == paused.doneBytes,
            "no connection may keep writing while paused: ${paused.doneBytes} -> ${stillPaused.doneBytes} (was ${atPause.doneBytes})",
        )

        DownloadManager.resume(id)
        assertTrue(awaitState(id, DownloadState.DONE), "resuming should finish the ranged download")
        val file = downloads.resolve(DownloadManager.guessFileName("Ranged stream", "http://127.0.0.1/ranged"))
        assertTrue(Files.size(file) == bodySize.toLong(), "every chunk should have been written")
    }

    @Test
    fun `cancel drops the row and deletes the partial file`() {
        val id = DownloadManager.add(settings(), "Slow stream", "http://127.0.0.1:${server.address.port}/slow")
        assertTrue(awaitProgress(id, 32L * 1024) > 0, "the download should start")

        val file = downloads.resolve(DownloadManager.guessFileName("Slow stream", "http://127.0.0.1/slow"))
        DownloadManager.cancel(id)
        assertTrue(
            DownloadManager.tasks.value.none { it.id == id },
            "a cancelled download should leave the queue",
        )

        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && Files.exists(file)) Thread.sleep(20)
        assertFalse(Files.exists(file), "the partial file should be deleted")
    }
}
