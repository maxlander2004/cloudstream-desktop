package com.lagradost.cloudstream3.ui.result

import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SeasonData
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Upstream's `VideoWatchState`: whether the user has finished an episode. */
enum class VideoWatchState {
    /** Default value when no key is set. */
    None,
    Watched,
}

/**
 * Upstream's `ResultEpisode`: everything the app knows about one episode.
 *
 * Copied from the Android app module. Extensions construct these when they hand a link to a
 * player, and video-click actions receive one so they can tell which episode is playing —
 * which is why the field set has to match upstream exactly.
 */
@Serializable
data class ResultEpisode(
    @SerialName("headerName") val headerName: String,
    @SerialName("name") val name: String?,
    @SerialName("poster") val poster: String?,
    @SerialName("episode") val episode: Int,
    @SerialName("seasonIndex") val seasonIndex: Int?,
    @SerialName("season") val season: Int?,
    @SerialName("data") val data: String,
    @SerialName("apiName") val apiName: String,
    @SerialName("id") val id: Int,
    @SerialName("index") val index: Int,
    @SerialName("position") val position: Long,
    @SerialName("duration") val duration: Long,
    @SerialName("score") val score: Score?,
    @SerialName("description") val description: String?,
    @SerialName("isFiller") val isFiller: Boolean?,
    @SerialName("tvType") val tvType: TvType,
    @SerialName("parentId") val parentId: Int,
    @SerialName("videoWatchState") val videoWatchState: VideoWatchState,
    @SerialName("totalEpisodeIndex") val totalEpisodeIndex: Int? = null,
    @SerialName("airDate") val airDate: Long? = null,
    @SerialName("runTime") val runTime: Int? = null,
    @SerialName("seasonData") val seasonData: SeasonData? = null,
)

/** Position to resume from, ignoring the first and last 5% like upstream. */
fun ResultEpisode.getRealPosition(): Long {
    if (duration <= 0) return 0
    val percentage = position * 100 / duration
    if (percentage <= 5 || percentage >= 95) return 0
    return position
}

/** Position to show on the progress bar, clamped like upstream. */
fun ResultEpisode.getDisplayPosition(): Long {
    if (duration <= 0) return 0
    val percentage = position * 100 / duration
    if (percentage <= 1) return 0
    if (percentage <= 5) return 5 * duration / 100
    if (percentage >= 95) return duration
    return position
}

/**
 * Upstream's `LinkLoadingResult`: the links and subtitles a provider extracted.
 *
 * A video-click action receives one so it can pick a link and start its own player.
 */
data class LinkLoadingResult(
    val links: List<ExtractorLink>,
    val subs: List<SubtitleData>,
    val syncData: HashMap<String, String>,
)
