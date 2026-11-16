package app.cloudstream.desktop

import android.content.Context
import app.cloudstream.desktop.core.PlaybackSession
import app.cloudstream.desktop.core.PluginPlayback
import app.cloudstream.desktop.core.SessionEpisode
import app.cloudstream.desktop.core.legacy.LegacyCatalog
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.actions.VideoClickAction
import com.lagradost.cloudstream3.actions.VideoClickActionHolder
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.VideoWatchState
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.UiText
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The desktop side of plugin playback actions: which ones a player offers for an episode and
 * what an action receives when it runs.
 */
class PluginPlaybackTest {
    private class StubProvider : MainAPI() {
        override var name: String = "Stub"
        override var mainUrl: String = "https://stub.example"
    }

    private val legacyCatalog = LegacyCatalog(StubProvider(), "StubPlugin")

    private val session = PlaybackSession(
        itemUrl = "https://example.com/watch/1",
        title = "Test Show",
        apiName = "TestProvider",
        posterUrl = "https://example.com/p.jpg",
        type = "SERIES",
        episodes = listOf(
            SessionEpisode(label = "S1E1", data = "episode-one", season = 1, episode = 1),
            SessionEpisode(label = "S1E2", data = "episode-two", season = 1, episode = 2),
        ),
    )

    /** Plugin actions are only offered for sessions backed by a loaded extension catalog. */
    private val legacySession = session.copy(catalog = legacyCatalog)

    private class FakeAction(
        private val label: String,
        private val shown: Boolean,
        private val explode: Boolean = false,
    ) : VideoClickAction() {
        var ran: ResultEpisode? = null

        override val name: UiText = UiText.DynamicString(label)

        override val isPlayer: Boolean = true

        override fun shouldShow(context: Context?, video: ResultEpisode?): Boolean {
            if (explode) error("provider state missing")
            return shown
        }

        override suspend fun runAction(
            context: Context?,
            video: ResultEpisode,
            result: LinkLoadingResult,
            index: Int?,
        ) {
            ran = video
        }
    }

    @BeforeTest
    fun clear() = VideoClickActionHolder.allVideoClickActions.clear()

    @AfterTest
    fun cleanup() = VideoClickActionHolder.allVideoClickActions.clear()

    @Test
    fun `only actions the plugin allows are offered`() {
        VideoClickActionHolder.allVideoClickActions += FakeAction("Cast", shown = true)
        VideoClickActionHolder.allVideoClickActions += FakeAction("Hidden", shown = false)

        val choices = PluginPlayback.choicesFor(legacySession, 0)

        assertEquals(listOf("Cast"), choices.map { it.label })
    }

    @Test
    fun `an action that throws while checking is skipped`() {
        VideoClickActionHolder.allVideoClickActions +=
            FakeAction("Broken", shown = true, explode = true)
        VideoClickActionHolder.allVideoClickActions += FakeAction("Good", shown = true)

        assertEquals(listOf("Good"), PluginPlayback.choicesFor(legacySession, 0).map { it.label })
    }

    @Test
    fun `a session without an extension offers no plugin players`() {
        VideoClickActionHolder.allVideoClickActions += FakeAction("Cast", shown = true)

        // No catalog at all: there is no extension behind the links to hand to an action.
        assertTrue(session.catalog !is LegacyCatalog)
        assertTrue(PluginPlayback.choicesFor(session, 0).isEmpty())
    }

    @Test
    fun `the episode is handed over the way upstream models it`() {
        val episode = PluginPlayback.resultEpisodeOf(session, 1)

        assertEquals("Test Show", episode.headerName)
        assertEquals("S1E2", episode.name)
        assertEquals("episode-two", episode.data)
        assertEquals("TestProvider", episode.apiName)
        assertEquals(1, episode.season)
        assertEquals(2, episode.episode)
        assertEquals(TvType.TvSeries, episode.tvType)
        assertEquals(VideoWatchState.None, episode.videoWatchState)
        assertEquals("https://example.com/p.jpg", episode.poster)
    }

    @Test
    fun `running an action without an extension fails instead of throwing`() = runBlocking {
        val result = PluginPlayback.run(session, 0, FakeAction("Cast", shown = true))

        assertTrue(result.isFailure)
    }

    @Test
    fun `an action can restrict itself to some link types`() {
        val restricted = VideoClickActionHolder.allVideoClickActions
        restricted += FakeAction("Cast", shown = true)
        assertEquals(ExtractorLinkType.entries.toSet(), restricted.first().sourceTypes)
    }
}
