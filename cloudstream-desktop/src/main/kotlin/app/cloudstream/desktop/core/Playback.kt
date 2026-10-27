package app.cloudstream.desktop.core

import app.cloudstream.desktop.api.Catalog
import app.cloudstream.desktop.api.StreamLink
import app.cloudstream.desktop.core.legacy.DesktopEpisode
import app.cloudstream.desktop.core.legacy.LegacyCatalog
import com.lagradost.cloudstream3.DubStatus
import java.util.Locale

/** One episode of a playback session: what to play, plus links once they have been resolved. */
data class SessionEpisode(
    val label: String,
    val data: String?,
    val season: Int?,
    val episode: Int?,
    val streams: List<StreamLink> = emptyList(),
)

/**
 * Everything the in-app player needs: the item, its ordered episode list and where to start.
 * Movies and live streams carry a [directDataUrl] instead of episodes.
 *
 * This is the desktop equivalent of the original's player intent extras (`id`, `episode`,
 * `dub`, `subs`), with the episode list kept so *Autoplay next episode* knows where to go.
 */
data class PlaybackSession(
    val itemUrl: String,
    val title: String,
    val apiName: String,
    val posterUrl: String? = null,
    val type: String = "OTHER",
    val episodes: List<SessionEpisode> = emptyList(),
    val startIndex: Int = 0,
    val directDataUrl: String? = null,
    val catalog: Catalog? = null,
) {
    val current: SessionEpisode? get() = episodes.getOrNull(startIndex)
    val isEpisodeBased: Boolean get() = episodes.isNotEmpty()

    fun at(index: Int): PlaybackSession =
        copy(startIndex = index.coerceIn(0, (episodes.size - 1).coerceAtLeast(0)))

    /** The episode Autoplay next episode moves to. */
    fun nextIndex(from: Int = startIndex): Int? = (from + 1).takeIf { it in episodes.indices }

    fun previousIndex(from: Int = startIndex): Int? = (from - 1).takeIf { it in episodes.indices }

    /** Links for [index]: the resolved ones when known, otherwise resolved from the source. */
    suspend fun resolveStreams(index: Int): List<StreamLink> {
        val episode = episodes.getOrNull(index)
        episode?.streams?.takeIf { it.isNotEmpty() }?.let { return it }
        val data = episode?.data ?: directDataUrl ?: return emptyList()
        val legacy = catalog as? LegacyCatalog
        if (legacy != null) return legacy.streamsFor(data, episode?.label)
        // Native catalogs (the built-in IPTV source) hand out one playable URL.
        return listOf(StreamLink(episode?.label ?: title, data))
    }
}

/** "S1E4 · 35%": the continue-watching label plus how far into the episode playback is. */
fun watchLabel(record: WatchRecord): String {
    val percent = Library.progressFor(record.itemUrl, record.episodeData)
        ?.fraction
        ?.takeIf { it > 0f }
        ?.let { "${(it * 100).toInt()}%" }
    return listOfNotNull(record.episodeLabel.takeIf { it.isNotBlank() }, percent).joinToString(" · ")
}

/** "S1E4 · Name", the label upstream shows for an episode. */
fun episodeLabelOf(episode: DesktopEpisode): String {
    val number = episode.episode ?: 0
    val prefix = (episode.season ?: 0).takeIf { it > 0 }
        ?.let { "S${it}E$number" }
        ?: "Episode $number"
    return episode.name?.takeIf { it.isNotBlank() }?.let { "$prefix · $it" } ?: prefix
}

/** Flattens a dub status' seasons into the player's ordered episode list. */
fun sessionEpisodes(seasons: Map<Int, List<DesktopEpisode>>): List<SessionEpisode> =
    seasons.entries
        .sortedBy { it.key }
        .flatMap { (_, list) -> list.sortedBy { it.episode ?: 0 } }
        .map { episode ->
            SessionEpisode(
                label = episodeLabelOf(episode),
                data = episode.data,
                season = episode.season,
                episode = episode.episode,
            )
        }

/** Every season of [dubStatus] in order, for the detail screen's play button. */
fun sessionEpisodes(
    episodeMap: Map<DubStatus, Map<Int, List<DesktopEpisode>>>,
    dubStatus: DubStatus,
): List<SessionEpisode> = sessionEpisodes(episodeMap[dubStatus] ?: emptyMap())

/**
 * Resume rules, matching the original: a saved position in the middle of an episode resumes,
 * while the first seconds and anything past the watched threshold start from the beginning.
 */
object ResumePolicy {
    /** Upstream marks an episode watched at 90% and moves on to the next one. */
    const val WATCHED_FRACTION = 0.9f

    /** Never resume into the opening seconds of an episode. */
    const val MIN_RESUME_MS = 15_000L

    fun startPositionMs(progress: WatchProgress?, resumeEnabled: Boolean): Long {
        if (!resumeEnabled || progress == null || progress.durationMs <= 0) return 0
        if (progress.fraction >= WATCHED_FRACTION) return 0
        return progress.positionMs.takeIf { it >= MIN_RESUME_MS } ?: 0
    }

    fun isWatched(progress: WatchProgress?): Boolean =
        progress != null && progress.durationMs > 0 && progress.fraction >= WATCHED_FRACTION

    /** "12:34" or "1:02:03", the time format both players show. */
    fun formatTime(millis: Long): String {
        val total = millis.coerceAtLeast(0) / 1000
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }
}
