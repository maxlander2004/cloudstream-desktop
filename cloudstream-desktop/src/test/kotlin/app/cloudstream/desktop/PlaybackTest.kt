package app.cloudstream.desktop

import app.cloudstream.desktop.api.StreamLink
import app.cloudstream.desktop.core.Library
import app.cloudstream.desktop.core.PlaybackSession
import app.cloudstream.desktop.core.ResumePolicy
import app.cloudstream.desktop.core.SessionEpisode
import app.cloudstream.desktop.core.WatchProgress
import app.cloudstream.desktop.core.WatchRecord
import app.cloudstream.desktop.core.watchLabel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Playback sessions, episode stepping and the resume rules behind the in-app player. */
class PlaybackTest {
    private fun series(episodes: Int) = PlaybackSession(
        itemUrl = "https://playback-test.example/series",
        title = "Playback Test",
        apiName = "Test",
        episodes = (1..episodes).map { SessionEpisode("S1E$it", "data-$it", 1, it) },
    )

    @Test
    fun `next episode walks the list and stops at the last one`() {
        val three = series(3)

        assertEquals(1, three.nextIndex(0))
        assertEquals(2, three.at(1).nextIndex())
        assertNull(three.nextIndex(2))
        assertEquals(1, three.previousIndex(2))
        assertNull(three.previousIndex(0))
    }

    @Test
    fun `at keeps the index inside the episode list`() {
        val two = series(2)

        assertEquals(1, two.at(5).startIndex)
        assertEquals(0, two.at(-3).startIndex)
        assertFalse(two.at(0).previousIndex(0) != null)
    }

    @Test
    fun `a movie session has one entry holding the chosen link`() {
        val movie = PlaybackSession(
            itemUrl = "https://playback-test.example/movie",
            title = "Movie",
            apiName = "Test",
            directDataUrl = "https://example.com/movie.mp4",
        )

        assertFalse(movie.isEpisodeBased)
        assertNull(movie.nextIndex())

        val episodes = movie.copy(
            episodes = listOf(
                SessionEpisode(
                    label = "Movie",
                    data = movie.directDataUrl,
                    season = null,
                    episode = null,
                    streams = listOf(StreamLink("best", "https://example.com/movie.mp4")),
                ),
            ),
        )
        assertEquals("https://example.com/movie.mp4", runBlocking { episodes.resolveStreams(0) }.single().url)
    }

    @Test
    fun `resolved links are reused instead of being fetched again`() {
        val built = series(2).copy(
            episodes = listOf(
                SessionEpisode("S1E1", "data-1", 1, 1),
                SessionEpisode("S1E2", "data-2", 1, 2, streams = listOf(StreamLink("best", "https://example.com/2.mp4"))),
            ),
            startIndex = 1,
        )

        assertEquals("https://example.com/2.mp4", runBlocking { built.resolveStreams(1) }.single().url)
    }

    @Test
    fun `resume starts fresh when disabled, near the beginning or once watched`() {
        val progress = WatchProgress(itemUrl = "x", positionMs = 600_000, durationMs = 1_200_000)

        assertEquals(600_000, ResumePolicy.startPositionMs(progress, resumeEnabled = true))
        assertEquals(0, ResumePolicy.startPositionMs(progress, resumeEnabled = false))
        assertEquals(0, ResumePolicy.startPositionMs(progress.copy(positionMs = 5_000), resumeEnabled = true))
        assertEquals(0, ResumePolicy.startPositionMs(progress.copy(positionMs = 1_150_000), resumeEnabled = true))
        assertEquals(0, ResumePolicy.startPositionMs(null, resumeEnabled = true))
        assertEquals(0, ResumePolicy.startPositionMs(WatchProgress(itemUrl = "x"), resumeEnabled = true))
    }

    @Test
    fun `episodes count as watched past ninety percent`() {
        assertTrue(ResumePolicy.isWatched(WatchProgress(itemUrl = "x", positionMs = 950, durationMs = 1000)))
        assertFalse(ResumePolicy.isWatched(WatchProgress(itemUrl = "x", positionMs = 500, durationMs = 1000)))
        assertFalse(ResumePolicy.isWatched(WatchProgress(itemUrl = "x")))
        assertFalse(ResumePolicy.isWatched(null))
    }

    @Test
    fun `continue watching labels carry the resume percentage`() {
        val url = "https://playback-test.example/continue"
        Library.forget(url)
        Library.clearProgress(url)
        Library.recordWatch(
            WatchRecord(
                itemUrl = url,
                apiName = "Test",
                title = "Playback Test",
                episodeData = "continue-2",
                episodeLabel = "S1E2",
                season = 1,
                episode = 2,
            ),
        )

        assertEquals("S1E2", watchLabel(Library.history().first { it.itemUrl == url }))

        Library.saveProgress(
            WatchProgress(itemUrl = url, episodeData = "continue-2", positionMs = 120_000, durationMs = 600_000),
        )
        assertEquals("S1E2 · 20%", watchLabel(Library.history().first { it.itemUrl == url }))

        Library.forget(url)
        Library.clearProgress(url)
    }

    @Test
    fun `times are formatted for the player controls`() {
        assertEquals("0:00", ResumePolicy.formatTime(0))
        assertEquals("0:07", ResumePolicy.formatTime(7_400))
        assertEquals("12:34", ResumePolicy.formatTime(754_000))
        assertEquals("1:02:03", ResumePolicy.formatTime(3_723_000))
    }
}
