package app.cloudstream.desktop

import app.cloudstream.desktop.core.Bookmark
import app.cloudstream.desktop.core.Library
import app.cloudstream.desktop.core.WatchProgress
import app.cloudstream.desktop.core.WatchRecord
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The store behind the Library screen, Continue watching, bookmarks and recent searches. */
class LibraryTest {
    private val itemUrl = "https://library-test.example/item"
    private val searches = listOf("library-test-query", "library-test-other", "Library-Test-Query")

    @AfterTest
    fun cleanUp() {
        Library.removeBookmark(itemUrl)
        Library.forget(itemUrl)
        Library.clearProgress(itemUrl)
        searches.forEach { Library.removeSearch(it) }
    }

    @Test
    fun `toggling a bookmark adds and removes it`() {
        assertFalse(Library.isBookmarked(itemUrl))

        assertTrue(Library.toggleBookmark(Bookmark(url = itemUrl, apiName = "Test", title = "Bookmark Test")))
        assertTrue(Library.isBookmarked(itemUrl))
        assertEquals("Bookmark Test", Library.bookmarks().first { it.url == itemUrl }.title)

        assertFalse(Library.toggleBookmark(Bookmark(url = itemUrl, apiName = "Test", title = "Bookmark Test")))
        assertFalse(Library.isBookmarked(itemUrl))
    }

    @Test
    fun `watch history keeps one entry per item and remembers watched episodes`() {
        Library.recordWatch(record("episode-1", "S1E1", episode = 1))
        Library.recordWatch(record("episode-2", "S1E2", episode = 2))

        val entries = Library.history().filter { it.itemUrl == itemUrl }
        assertEquals(1, entries.size)
        assertEquals("episode-2", entries.first().episodeData)
        assertEquals("S1E2", entries.first().episodeLabel)
        assertEquals("episode-2", Library.lastWatched(itemUrl)?.episodeData)
        assertTrue(Library.isEpisodeWatched("episode-2"))

        Library.forget(itemUrl)
        assertNull(Library.lastWatched(itemUrl))
        assertFalse(Library.isEpisodeWatched("episode-2"))
    }

    @Test
    fun `recent searches are deduplicated case-insensitively`() {
        Library.addSearch("library-test-query")
        Library.addSearch("library-test-other")
        Library.addSearch("Library-Test-Query")

        assertEquals(
            listOf("Library-Test-Query", "library-test-other"),
            Library.searches().filter { it.startsWith("library-test", ignoreCase = true) },
        )

        Library.removeSearch("library-test-other")
        assertEquals(
            listOf("Library-Test-Query"),
            Library.searches().filter { it.startsWith("library-test", ignoreCase = true) },
        )
    }

    @Test
    fun `playback progress round-trips per episode`() {
        Library.clearProgress(itemUrl)

        Library.saveProgress(
            WatchProgress(
                itemUrl = itemUrl,
                episodeData = "progress-episode",
                positionMs = 120_000,
                durationMs = 600_000,
            ),
        )

        val saved = Library.progressFor(itemUrl, "progress-episode")!!
        assertEquals(120_000, saved.positionMs)
        assertEquals(600_000, saved.durationMs)
        assertEquals(0.2f, saved.fraction, 0.0001f)
        assertNull(Library.progressFor(itemUrl, "another-episode"))

        Library.clearProgress(itemUrl, "progress-episode")
        assertNull(Library.progressFor(itemUrl, "progress-episode"))
    }

    private fun record(data: String, label: String, episode: Int) = WatchRecord(
        itemUrl = itemUrl,
        apiName = "Test",
        title = "History Test",
        episodeData = data,
        episodeLabel = label,
        season = 1,
        episode = episode,
    )
}
