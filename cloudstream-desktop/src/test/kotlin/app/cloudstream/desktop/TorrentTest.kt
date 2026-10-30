package app.cloudstream.desktop

import app.cloudstream.desktop.api.StreamLink
import app.cloudstream.desktop.core.TorrentEngine
import app.cloudstream.desktop.core.bestDownloadLink
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The parts of torrent playback that do not need aria2 or a network. */
class TorrentTest {

    @Test
    fun `magnet links are recognised as torrents`() {
        assertTrue(TorrentEngine.isTorrentUrl("magnet:?xt=urn:btih:abcdef&dn=Show"))
        assertTrue(TorrentEngine.isTorrentUrl("  MAGNET:?xt=urn:btih:abcdef"))
        assertFalse(TorrentEngine.isTorrentUrl("https://example.com/video.mp4"))
        assertFalse(TorrentEngine.isTorrentUrl("file:///tmp/video.mkv"))
        assertFalse(TorrentEngine.isTorrentUrl(""))
    }

    @Test
    fun `the display name comes out of the magnet's dn parameter`() {
        assertEquals(
            "Show S01E02 1080p",
            TorrentEngine.magnetName(
                "magnet:?xt=urn:btih:abcdef&dn=Show%20S01E02%201080p&tr=udp%3A%2F%2Ftracker",
            ),
        )
        assertNull(TorrentEngine.magnetName("magnet:?xt=urn:btih:abcdef"))
    }

    @Test
    fun `a missing range means the whole file`() {
        assertEquals(0L to null, TorrentEngine.parseRange(null, 1000))
        assertEquals(0L to null, TorrentEngine.parseRange("", 1000))
    }

    @Test
    fun `open ended and closed ranges are read`() {
        assertEquals(100L to null, TorrentEngine.parseRange("bytes=100-", 1000))
        assertEquals(0L to 499L, TorrentEngine.parseRange("bytes=0-499", 1000))
        assertEquals(500L to 999L, TorrentEngine.parseRange("bytes=500-999", 1000))
    }

    @Test
    fun `a range past the end is refused`() {
        assertNull(TorrentEngine.parseRange("bytes=2000-", 1000))
        assertNull(TorrentEngine.parseRange("bytes=500-100", 1000))
    }

    @Test
    fun `content types follow the file extension`() {
        assertEquals("video/mp4", TorrentEngine.contentType(Paths.get("a/b/movie.mp4")))
        assertEquals("video/x-matroska", TorrentEngine.contentType(Paths.get("show.mkv")))
        assertEquals("video/webm", TorrentEngine.contentType(Paths.get("show.WEBM")))
        assertEquals("application/octet-stream", TorrentEngine.contentType(Paths.get("show.bin")))
    }

    @Test
    fun `a magnet is never chosen as a download link`() {
        val links = listOf(
            StreamLink("Torrent", "magnet:?xt=urn:btih:abc"),
            StreamLink("Direct", "https://example.com/episode.mp4"),
        )
        assertEquals("https://example.com/episode.mp4", links.bestDownloadLink()?.url)
        assertEquals(null, listOf(StreamLink("Torrent", "magnet:?xt=urn:btih:abc")).bestDownloadLink())
    }
}
