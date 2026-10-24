package app.cloudstream.desktop.core.legacy

import app.cloudstream.desktop.api.Catalog
import app.cloudstream.desktop.api.CatalogItem
import app.cloudstream.desktop.api.StreamLink
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
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

    override suspend fun browse(): List<CatalogItem> {
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

        return response?.items.orEmpty().flatMap { it.list }.map { it.toCatalogItem() }
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

    override suspend fun streams(item: CatalogItem): List<StreamLink> {
        val response = loaded[item.url] ?: runCatching { provider.load(item.url) }
            .getOrNull()
            ?.also { loaded[item.url] = it }
            ?: return emptyList()

        val episode: Episode? = when (response) {
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
                title = episode?.name?.takeIf { it.isNotBlank() }?.let { "$it · $label" } ?: label,
                url = link.url,
                headers = headers,
            )
        }

        return links
    }

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
