package app.cloudstream.desktop

import app.cloudstream.desktop.api.CatalogItem
import app.cloudstream.desktop.core.legacy.LegacyCatalog
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SeasonData
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The data behind the original's result screen: [LegacyCatalog.episodeGroups] has to reproduce
 * the season/dub grouping and the "Episode 1" / "2. Jobless" labels `EpisodeAdapter` builds,
 * and resolving one episode has to request that episode's data.
 */
class LegacyEpisodeTest {
    private class FakeProvider : MainAPI() {
        override var name = "Fake"
        override var mainUrl = "https://fake.example"
        override val mainPage = emptyList<MainPageData>()

        var response: LoadResponse? = null
        val requested = mutableListOf<String>()

        override suspend fun load(url: String): LoadResponse? = response

        override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit,
        ): Boolean {
            requested += data
            callback(newExtractorLink("Fake", "1080p", "https://cdn.example/$data.mp4"))
            return true
        }
    }

    private val item = CatalogItem(title = "Show", url = "https://fake.example/show")

    private fun seriesProvider() = FakeProvider().apply {
        response = runBlocking {
            newTvSeriesLoadResponse(
                name = "Show",
                url = "https://fake.example/show",
                type = TvType.TvSeries,
                episodes = listOf(
                    newEpisode("s1e1", { season = 1; episode = 1 }, fix = false),
                    newEpisode(
                        "s1e2",
                        { season = 1; episode = 2; name = "Jobless"; score = Score.from(8.8, 10) },
                        fix = false,
                    ),
                    newEpisode("s2e1", { season = 2; episode = 1 }, fix = false),
                ),
            )
        }
    }

    @Test
    fun `series episodes are grouped by season and labelled like the adapter`() = runBlocking {
        val groups = LegacyCatalog(seriesProvider(), "Fake").episodeGroups(item)

        assertEquals(listOf("Season 1", "Season 2"), groups.map { it.label })
        assertEquals(listOf("Episode 1", "2. Jobless"), groups[0].episodes.map { it.label })
        assertEquals(listOf("s1e1", "s1e2"), groups[0].episodes.map { it.data })
        assertEquals(8.8, groups[0].episodes[1].rating!!, absoluteTolerance = 0.01)
    }

    @Test
    fun `anime episodes are grouped by sub and dub`() = runBlocking {
        val provider = FakeProvider()
        provider.response = provider.newAnimeLoadResponse(
            name = "Anime",
            url = "https://fake.example/anime",
            type = TvType.Anime,
        ) {
            episodes = mutableMapOf(
                DubStatus.Subbed to listOf(provider.newEpisode("sub1", { episode = 1 }, fix = false)),
                DubStatus.Dubbed to listOf(provider.newEpisode("dub1", { episode = 1 }, fix = false)),
            )
        }

        val groups = LegacyCatalog(provider, "Fake").episodeGroups(item)

        assertEquals(listOf("Sub", "Dub"), groups.map { it.label })
        assertEquals(listOf("sub1", "dub1"), groups.flatMap { it.episodes }.map { it.data })
    }

    @Test
    fun `a movie has no episode list`() = runBlocking {
        val provider = FakeProvider()
        provider.response = provider.newMovieLoadResponse(
            name = "Movie",
            url = "https://fake.example/movie",
            type = TvType.Movie,
            dataUrl = "movie-data",
        )

        assertTrue(LegacyCatalog(provider, "Fake").episodeGroups(item).isEmpty())
    }

    @Test
    fun `season labels follow the original's seasonToTxt`() = runBlocking {
        val provider = FakeProvider()
        provider.response = provider.newTvSeriesLoadResponse(
            name = "Show",
            url = "https://fake.example/show",
            type = TvType.TvSeries,
            episodes = listOf(
                provider.newEpisode("a", { season = 1; episode = 1 }, fix = false),
                provider.newEpisode("b", { season = 2; episode = 1 }, fix = false),
                provider.newEpisode("c", { episode = 1 }, fix = false),
            ),
        ) {
            seasonNames = listOf(
                SeasonData(season = 1, name = "Part One"),
                SeasonData(season = 2, name = "Saga", displaySeason = 5),
            )
        }

        val labels = LegacyCatalog(provider, "Fake").episodeGroups(item).map { it.label }

        // A name without a display number is shown on its own, a display number is prefixed
        // with "Season" and the name appended, and a missing season is upstream's "No Season".
        assertEquals(listOf("No Season", "Part One", "Season 5 Saga"), labels)
    }

    @Test
    fun `an episode named Episode N keeps the plain label`() = runBlocking {
        val provider = FakeProvider()
        provider.response = provider.newTvSeriesLoadResponse(
            name = "Show",
            url = "https://fake.example/show",
            type = TvType.TvSeries,
            episodes = listOf(
                provider.newEpisode(
                    "e3",
                    { season = 1; episode = 3; name = "Episode 3" },
                    fix = false,
                ),
            ),
        )

        val labels = LegacyCatalog(provider, "Fake").episodeGroups(item)[0].episodes.map { it.label }

        assertEquals(listOf("Episode 3"), labels)
    }

    @Test
    fun `resolving an episode requests that episode's data`() = runBlocking {
        val provider = seriesProvider()
        val catalog = LegacyCatalog(provider, "Fake")
        val jobless = catalog.episodeGroups(item)[0].episodes[1]

        val links = catalog.streams(item, jobless)

        assertEquals(listOf("s1e2"), provider.requested)
        assertEquals(listOf("Jobless · 1080p"), links.map { it.title })
        assertEquals(listOf("https://cdn.example/s1e2.mp4"), links.map { it.url })
    }
}
