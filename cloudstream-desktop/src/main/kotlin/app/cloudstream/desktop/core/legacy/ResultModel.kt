package app.cloudstream.desktop.core.legacy

import app.cloudstream.desktop.api.CatalogItem
import app.cloudstream.desktop.api.ItemType
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.EpisodeResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.isEpisodeBased

/** Desktop-facing model for an upstream [LoadResponse], used by the result/detail screen. */
data class ResultItem(
    val response: LoadResponse,
    val pluginName: String,
) {
    val title: String get() = response.name
    val url: String get() = response.url
    val apiName: String get() = response.apiName
    val type: ItemType get() = response.type.toItemType()
    val posterUrl: String? get() = response.posterUrl
    val backgroundPosterUrl: String? get() = response.backgroundPosterUrl
    val logoUrl: String? get() = response.logoUrl
    val plot: String? get() = response.plot
    val year: Int? get() = response.year
    /** 0..10, matches the upstream score UI. */
    val score: Float? get() = response.score?.toFloat(10)
    val tags: List<String> get() = response.tags.orEmpty()
    val durationMinutes: Int? get() = response.duration
    val comingSoon: Boolean get() = response.comingSoon
    /** Related items, drawn as upstream's recommendation rail on the result page. */
    val recommendations: List<CatalogItem> get() = response.recommendations.orEmpty().map { related ->
        CatalogItem(
            title = related.name,
            url = related.url,
            posterUrl = related.posterUrl,
            type = related.type.toItemType(),
        )
    }

    /** Trailer URLs the provider published. */
    val trailers: List<String>
        get() = response.trailers.orEmpty()
            .mapNotNull { it.extractorUrl.takeIf(String::isNotBlank) }

    /** Cast and roles, as upstream's `ActorData` carries them. */
    val cast: List<CastMember> get() = response.actors.orEmpty().mapNotNull { actor ->
        val name = actor.actor.name.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        CastMember(
            name = name,
            imageUrl = actor.actor.image,
            role = actor.roleString ?: actor.role?.name,
        )
    }

    val description: String?
        get() = when (response) {
            is AnimeLoadResponse -> response.engName
            is TvSeriesLoadResponse -> null
            is MovieLoadResponse -> null
            is LiveStreamLoadResponse -> null
            else -> null
        }

    /** Upstream's show status text, where available. */
    val showStatus: String?
        get() = if (response is EpisodeResponse) {
            when (response.showStatus) {
                com.lagradost.cloudstream3.ShowStatus.Ongoing -> "Ongoing"
                com.lagradost.cloudstream3.ShowStatus.Completed -> "Completed"
                else -> null
            }
        } else null

    /** Upstream's next airing info, where available. */
    val nextAiring: Pair<Int, Long>?
        get() = if (response is EpisodeResponse) {
            response.nextAiring?.let { it.episode to it.unixTime }
        } else null

    /** Upstream season display names, where the response exposes them (series/anime). */
    val seasonNames: List<Pair<Int, String?>>
        get() = (response as? EpisodeResponse)?.seasonNames.orEmpty()
            .map { it.season to it.name }

    /** Is this item episode-based? Movies, live streams and audio are not. Mirrors upstream
     * `LoadResponse?.isEpisodeBased()` / `TvType?.isEpisodeBased()` from `MainAPI.kt`. */
    val episodeBased: Boolean
        get() = response.isEpisodeBased()

    /** Live stream items carry a dataUrl and should be treated like direct streams. */
    val isLive: Boolean
        get() = response is LiveStreamLoadResponse

    /** Direct dataUrl for movies and live streams; null for episode-based items. */
    val directDataUrl: String?
        get() = when (response) {
            is MovieLoadResponse -> response.dataUrl
            is LiveStreamLoadResponse -> response.dataUrl
            else -> null
        }

    /** Episodes available for this item, keyed by dub/sub status then season.
     * Null when the item is not episode-based. */
    fun episodesByDubAndSeason(): Map<DubStatus, Map<Int, List<DesktopEpisode>>>? {
        val eps: Map<DubStatus, List<Episode>>? = when (response) {
            is AnimeLoadResponse -> response.episodes
            is TvSeriesLoadResponse -> mapOf(DubStatus.None to response.episodes)
            else -> null
        }
        if (eps == null) return null
        return eps.mapValues { (_, list) ->
            list.groupBy { it.season ?: 0 }.mapValues { (_, s) -> s.map { DesktopEpisode(it) } }
        }
    }

    /** Upstream's `TvType` reduced to the desktop categories the Providers settings filter on. */
    private fun com.lagradost.cloudstream3.TvType?.toItemType() = when (this) {
        TvType.Movie, TvType.AnimeMovie -> ItemType.MOVIE
        TvType.TvSeries -> ItemType.SERIES
        TvType.Anime, TvType.OVA -> ItemType.ANIME
        TvType.Live -> ItemType.LIVE
        TvType.NSFW -> ItemType.NSFW
        else -> ItemType.OTHER
    }

    companion object {
        /** Builds the episode map the result screen uses for episode-based items. */
        fun episodeMapFor(response: LoadResponse): Map<DubStatus, Map<Int, List<DesktopEpisode>>>? {
            if (!response.isEpisodeBased()) return null
            val episodes: Map<DubStatus, List<Episode>> = when (response) {
                is AnimeLoadResponse -> response.episodes
                is TvSeriesLoadResponse -> mapOf(DubStatus.None to response.episodes)
                else -> return null
            }
            return episodes.mapValues { (_, list) ->
                list.groupBy { it.season ?: 0 }.mapValues { (_, s) -> s.map { DesktopEpisode(it) } }
            }
        }
    }
}

/** One cast member on the result page. */
data class CastMember(
    val name: String,
    val imageUrl: String?,
    val role: String?,
)

/** Desktop-facing model for one upstream [Episode]. */
data class DesktopEpisode(
    val upstream: Episode,
) {
    val data: String get() = upstream.data
    val name: String? get() = upstream.name
    val season: Int? get() = upstream.season
    val episode: Int? get() = upstream.episode
    val posterUrl: String? get() = upstream.posterUrl
    /** 0..10 upstream score; `Episode.score` is also a `Score`, so convert through it. */
    val score: Float? get() = upstream.score?.toFloat(10)
    val description: String? get() = upstream.description
    val airDateUnix: Long? get() = upstream.date
    val runTimeMinutes: Int? get() = upstream.runTime?.let { it / 60 }
    val isFiller: Boolean get() = false
}

/** The user's current episode selection context for an episode-based item. */
data class EpisodeSelection(
    val dubStatus: DubStatus,
    val season: Int?,
    val episode: DesktopEpisode?,
) {
    val canResolve: Boolean
        get() = episode != null
}
