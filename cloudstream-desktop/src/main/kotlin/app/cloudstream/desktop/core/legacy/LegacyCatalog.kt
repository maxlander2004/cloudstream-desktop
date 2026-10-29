package app.cloudstream.desktop.core.legacy

import app.cloudstream.desktop.api.Catalog
import app.cloudstream.desktop.api.CatalogItem
import app.cloudstream.desktop.api.ItemType
import app.cloudstream.desktop.api.StreamLink
import app.cloudstream.desktop.api.SubtitleTrack
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
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

    /** Loads the upstream [LoadResponse] for [url] and wraps it for the result screen. */
    suspend fun loadResult(url: String): ResultItem? = runCatching {
        val response = loaded[url] ?: provider.load(url)
            .also { if (it != null) loaded[url] = it }
        if (response == null) return@runCatching null
        ResultItem(response, pluginName)
    }.getOrNull()

    /**
     * Upstream's home rows, keeping the row names the original draws as rail headers. Each entry
     * is one `HomePageResponse.items` element.
     */
    suspend fun browseSections(): List<CatalogSection> {
        val page = provider.mainPage.firstOrNull()
        val response = try {
            provider.getMainPage(
                1,
                MainPageRequest(
                    page?.name ?: "",
                    page?.data ?: "",
                    page?.horizontalImages ?: false,
                ),
            )
        } catch (e: NotImplementedError) {
            return emptyList()
        }

        return response?.items.orEmpty().map { row ->
            CatalogSection(
                name = row.name,
                items = row.list.orEmpty().map { item ->
                    CatalogItem(
                        title = item.name.ifBlank { item.url },
                        url = item.url,
                        // The home card in `home_result_grid.xml` has no provider label; the
                        // original only shows the reading's own episode text there.
                        subtitle = null,
                        posterUrl = item.posterUrl,
                        type = itemTypeFor(item.type),
                    )
                },
            )
        }
    }

    override suspend fun browse(): List<CatalogItem> = browseSections().flatMap { it.items }

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

        return results.orEmpty().map { item ->
            CatalogItem(
                title = item.name.ifBlank { item.url },
                url = item.url,
                subtitle = item.apiName.ifBlank { null },
                posterUrl = item.posterUrl,
                type = itemTypeFor(item.type),
            )
        }
    }

    override suspend fun streams(item: CatalogItem): List<StreamLink> {
        val response = loaded[item.url] ?: runCatching { provider.load(item.url) }
            .getOrNull()
            ?.also { if (it != null) loaded[item.url] = it }
            ?: return emptyList()

        val episode: Episode? = when (response) {
            is MovieLoadResponse -> null
            is LiveStreamLoadResponse -> null
            is TvSeriesLoadResponse -> response.episodes.firstOrNull()
            is AnimeLoadResponse -> response.episodes.values.firstNotNullOfOrNull { it.firstOrNull() }
            else -> null
        }

        val data: String? = when {
            response is MovieLoadResponse -> response.dataUrl
            response is LiveStreamLoadResponse -> response.dataUrl
            else -> episode?.data
        }

        return streamsFor(data, episode?.name)
    }

    /**
     * Resolves playable links for an upstream episode's `data` string — the same `loadLinks`
     * call the Android player makes. The result screen resolves one episode at a time with
     * this, so every season/episode in the picker is playable. Upstream reports subtitles
     * through a callback rather than on the link, so they are collected and attached to every
     * link of the episode.
     */
    suspend fun streamsFor(data: String?, episodeName: String? = null): List<StreamLink> {
        if (data.isNullOrBlank()) return emptyList()

        val subtitles = mutableListOf<SubtitleTrack>()
        val links = mutableListOf<StreamLink>()
        provider.loadLinks(data, false, { file ->
            val label = file.lang.takeIf { it.isNotBlank() } ?: return@loadLinks
            if (file.url.isBlank()) return@loadLinks
            subtitles += SubtitleTrack(
                label = label,
                language = file.langTag ?: file.lang,
                url = file.url,
                headers = file.headers.orEmpty(),
            )
        }) { link ->
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

        // The subtitle callback may arrive after the links, so attach them at the end.
        return links.map { link -> link.copy(subtitles = subtitles.toList()) }
    }

    /**
     * Returns the episode map the result screen uses for episode-based items. Each key is a
     * [DubStatus], each value is a season → episodes map. Mirrors how `ResultViewModel2` builds
     * the season/episode UI in the Android app.
     */
    suspend fun episodeMapFor(url: String): Map<DubStatus, Map<Int, List<DesktopEpisode>>>? = runCatching {
        val response = loadResult(url)?.response
        if (response == null) return@runCatching null
        ResultItem.episodeMapFor(response)
    }.getOrNull()

    private fun itemTypeFor(type: TvType?): ItemType {
        if (type == null) return ItemType.OTHER
        return when (type.name.lowercase()) {
            "movie" -> ItemType.MOVIE
            "tvseries", "tvshow" -> ItemType.SERIES
            "anime", "ova", "animeothers" -> ItemType.ANIME
            "live", "livestream" -> ItemType.LIVE
            "nsfw" -> ItemType.NSFW
            else -> ItemType.OTHER
        }
    }
}

/** One home row from a provider, mirroring upstream's `HomePageList`. */
data class CatalogSection(
    val name: String,
    val items: List<CatalogItem>,
)
