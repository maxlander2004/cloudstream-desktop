package app.cloudstream.desktop.core.legacy

import app.cloudstream.desktop.api.Catalog
import app.cloudstream.desktop.api.CatalogItem
import app.cloudstream.desktop.api.Episode
import app.cloudstream.desktop.api.EpisodeGroup
import app.cloudstream.desktop.api.HomeSection
import app.cloudstream.desktop.api.StreamLink
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode as CsEpisode
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SeasonData
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import java.util.concurrent.ConcurrentHashMap

/**
 * Presents one provider from a CloudStream extension as a desktop [Catalog].
 *
 * The mapping is the same one the Android app performs when the user taps a provider:
 * `mainPage`/`getMainPage` for the home rows, `search` for search, then `load` followed by
 * `loadLinks` to resolve playable URLs.
 */
class LegacyCatalog(
    private val provider: MainAPI,
    private val pluginName: String,
) : Catalog {
    override val name: String = provider.name

    override val description: String = buildString {
        append(pluginName)
        provider.mainUrl.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        provider.lang?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it.uppercase()) }
    }

    override val language: String?
        get() = provider.lang.takeIf { it.isNotBlank() }

    /** `load()` is the expensive step; the stream dialog may be reopened for the same item. */
    private val loaded = ConcurrentHashMap<String, LoadResponse>()

    override suspend fun browse(): List<CatalogItem> =
        browseSections().flatMap { it.items }

    /**
     * The same call chain the Android app's HomeViewModel runs: one `getMainPage` request per
     * declared main page, each response section keeping its own name for the row header.
     */
    override suspend fun browseSections(): List<HomeSection> {
        return provider.mainPage.mapNotNull { page ->
            val response = try {
                provider.getMainPage(
                    1,
                    MainPageRequest(page.name, page.data, page.horizontalImages ?: false),
                )
            } catch (e: NotImplementedError) {
                return@mapNotNull null
            }
            val items = response?.items.orEmpty().flatMap { it.list }.map { it.toCatalogItem() }
            HomeSection(page.name.ifBlank { provider.name }, items)
        }.filter { it.items.isNotEmpty() }
    }

    override suspend fun search(query: String): List<CatalogItem> {
        if (query.isBlank()) return emptyList()

        val results = try {
            provider.search(query, 1)?.items
        } catch (e: NotImplementedError) {
            null
        } ?: try {
            provider.quickSearch(query)
        } catch (e: NotImplementedError) {
            null
        }

        return results.orEmpty().map { it.toCatalogItem() }
    }

    /** `load()` is cached so the result screen and the source picker share one request. */
    private suspend fun loadResponse(item: CatalogItem): LoadResponse? =
        loaded[item.url] ?: runCatching { provider.load(item.url) }
            .getOrNull()
            ?.also { loaded[item.url] = it }

    override suspend fun episodeGroups(item: CatalogItem): List<EpisodeGroup> {
        val response = loadResponse(item) ?: return emptyList()
        return when (response) {
            // The same shape the Android app builds its season spinner and episode list from:
            // sorted by season and episode, keyed by season, labelled with seasonToTxt().
            is TvSeriesLoadResponse -> response.episodes
                .sortedBy { (it.season ?: 0) * 10_000 + (it.episode ?: 0) }
                .mapIndexed { index, episode -> episode.toDesktopEpisode(index) }
                .groupBy { it.season ?: 0 }
                .toSortedMap()
                .map { (season, episodes) ->
                    val data = response.seasonNames?.firstOrNull { it.season == season }
                    EpisodeGroup(seasonToTxt(data, season), episodes)
                }

            // Anime keys its episodes by dub status, which the original shows as a second picker.
            is AnimeLoadResponse -> response.episodes
                .filterValues { it.isNotEmpty() }
                .map { (status, episodes) ->
                    EpisodeGroup(
                        label = when (status) {
                            DubStatus.Subbed -> "Sub"
                            DubStatus.Dubbed -> "Dub"
                            else -> "None"
                        },
                        episodes = episodes.mapIndexed { index, episode ->
                            episode.toDesktopEpisode(index)
                        },
                    )
                }

            else -> emptyList()
        }
    }

    /** Upstream's `seasonToTxt`: "No Season", a custom name, or "Season 1 [name]". */
    private fun seasonToTxt(data: SeasonData?, season: Int): String {
        if (season == 0) return "No Season"
        val name = data?.name
        // If displaySeason is null then only show the name!
        if (data != null && name != null && data.displaySeason == null) return name
        val suffix = name?.let { " $it" } ?: ""
        return "Season ${data?.displaySeason ?: season}$suffix"
    }

    /** Upstream's `filterName`: an episode named just "Episode 5" falls back to the plain label. */
    private fun filterName(name: String?): String? {
        if (name == null) return null
        val remainder = Regex("^[eE]pisode [0-9]*(.*)").find(name)?.groupValues?.get(1)
        if (remainder != null && remainder.isEmpty()) return null
        return name
    }

    override suspend fun streams(item: CatalogItem): List<StreamLink> {
        val response = loadResponse(item) ?: return emptyList()

        val episode: CsEpisode? = when (response) {
            is MovieLoadResponse -> null
            is LiveStreamLoadResponse -> null
            is TvSeriesLoadResponse -> response.episodes.firstOrNull()
            is AnimeLoadResponse -> response.episodes.values.firstNotNullOfOrNull { it.firstOrNull() }
            else -> return emptyList()
        }

        val data: String? = when {
            response is MovieLoadResponse -> response.dataUrl
            response is LiveStreamLoadResponse -> response.dataUrl
            else -> episode?.data
        }

        if (data.isNullOrBlank()) return emptyList()

        return linksFor(data, episode?.name)
    }

    override suspend fun streams(item: CatalogItem, episode: Episode): List<StreamLink> {
        // Resolving the groups already ran load(), so the episode's data resolves immediately.
        if (loadResponse(item) == null) return emptyList()
        return linksFor(episode.data, episode.name)
    }

    /** `loadLinks` for one data id, labelled with the episode name the way the player does. */
    private suspend fun linksFor(data: String, episodeName: String?): List<StreamLink> {
        val links = mutableListOf<StreamLink>()
        provider.loadLinks(data, false, { }) { link ->
            val headers = if (link.headers.containsKey("Referer") || link.referer.isBlank()) {
                link.headers
            } else {
                link.headers + ("Referer" to link.referer)
            }

            val label = link.name.takeIf { it.isNotBlank() }
                ?: link.source.takeIf { it.isNotBlank() }
                ?: "Stream"

            links += StreamLink(
                title = episodeName?.takeIf { it.isNotBlank() }?.let { "$it · $label" } ?: label,
                url = link.url,
                headers = headers,
            )
        }

        return links
    }

    private fun CsEpisode.toDesktopEpisode(index: Int) =
        app.cloudstream.desktop.api.Episode(
            data = data,
            episode = episode ?: index + 1,
            name = filterName(name),
            season = season,
            posterUrl = posterUrl,
            overview = description,
            rating = score?.toFloat(10)?.toDouble(),
            airDate = date,
            runTime = runTime,
        )

    private fun SearchResponse.toCatalogItem() = CatalogItem(
        title = name.ifBlank { url },
        url = url,
        subtitle = apiName.ifBlank { null },
        posterUrl = posterUrl,
        type = type.toItemType(),
    )

    /** Upstream's `TvType` reduced to the desktop categories the Providers settings filter on. */
    private fun com.lagradost.cloudstream3.TvType?.toItemType() = when (this?.name?.lowercase()) {
        "movie" -> app.cloudstream.desktop.api.ItemType.MOVIE
        "tvseries", "tvshow" -> app.cloudstream.desktop.api.ItemType.SERIES
        "anime", "ova", "animeothers" -> app.cloudstream.desktop.api.ItemType.ANIME
        "live", "livestream" -> app.cloudstream.desktop.api.ItemType.LIVE
        "nsfw" -> app.cloudstream.desktop.api.ItemType.NSFW
        else -> app.cloudstream.desktop.api.ItemType.OTHER
    }
}
