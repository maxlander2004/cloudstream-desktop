package app.cloudstream.desktop

import app.cloudstream.desktop.core.TorrentEngine
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end torrent test against a real magnet: aria2 fetches it, the bridge serves it.
 *
 * This needs a torrent client and the network, so it only runs when it is asked for:
 *
 *     TORRENT_SMOKE=true ARIA2=/path/to/aria2c TORRENT_LINK='magnet:?xt=…' \
 *       ./gradlew test --tests '*TorrentSmoke*'
 *
 * TORRENT_LINK takes a magnet or a path to a `.torrent` file.
 *
 * Without those variables the test passes without touching anything, which is what CI does.
 */
class TorrentSmokeTest {

    private val enabled = System.getenv("TORRENT_SMOKE") == "true"

    @Test
    fun `a real magnet is fetched and streamed through the bridge`() {
        if (!enabled) {
            println("TorrentSmokeTest skipped: set TORRENT_SMOKE=true to run it")
            return
        }

        val aria2 = System.getenv("ARIA2")
            ?: error("ARIA2 must point at the aria2c binary")
        val link = System.getenv("TORRENT_LINK")
            ?: System.getenv("TORRENT_MAGNET")
            ?: error("TORRENT_LINK must hold a magnet link or a .torrent path")

        val directory = Files.createTempDirectory("torrent-smoke")
        val prepared = TorrentEngine.prepare(link, directory.toString(), aria2)
        assertTrue(
            prepared.isSuccess,
            "prepare failed: ${prepared.exceptionOrNull()?.message}",
        )
        val url = prepared.getOrThrow()
        assertTrue(url.startsWith("http://127.0.0.1:${TorrentEngine.PORT}/torrent/"), url)

        try {
            // The first bytes must arrive over HTTP, which is what VLC asks for.
            val head = fetch(url, "bytes=0-")
            assertTrue(head > 0, "the bridge returned no bytes for the head of the file")

            // A mid-file range must be honoured too, or seeking would break.
            val middle = fetch(url, "bytes=${head * 8}-")
            assertTrue(middle > 0, "the bridge returned no bytes for a range request")

            val served = verifyContentType(url)
            assertTrue(served, "the bridge did not report a content type")
        } finally {
            TorrentEngine.stopAll()
        }
    }

    private fun fetch(url: String, range: String): Int {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.setRequestProperty("Range", range)
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        val code = connection.responseCode
        if (code !in 200..299) error("bridge answered HTTP $code for $range")
        val buffer = ByteArray(64 * 1024)
        val read = connection.inputStream.use { it.read(buffer) }
        connection.disconnect()
        return read.coerceAtLeast(0)
    }

    private fun verifyContentType(url: String): Boolean {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Range", "bytes=0-")
            connectTimeout = 15_000
            readTimeout = 60_000
        }
        val type = connection.contentType
        val ok = !type.isNullOrBlank()
        connection.disconnect()
        return ok
    }
}
