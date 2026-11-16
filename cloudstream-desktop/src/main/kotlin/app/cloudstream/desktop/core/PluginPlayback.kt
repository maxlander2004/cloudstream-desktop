package app.cloudstream.desktop.core

import app.cloudstream.desktop.core.legacy.LegacyCatalog
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.actions.VideoClickAction
import com.lagradost.cloudstream3.actions.VideoClickActionHolder
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.VideoWatchState

/**
 * Playback the extensions supply themselves.
 *
 * CloudStream plugins can register a `VideoClickAction` — "Play with cast", "Play in external
 * player", a provider's own player — and the Android app lists them on the link sheet. This is
 * the desktop counterpart: which of them apply to the episode being played, and how to run one.
 *
 * Only extensions loaded as original `.cs3` archives can offer these, because an action needs
 * the upstream `ExtractorLink`s that `loadLinks` produced; a desktop `.jar` catalog hands out
 * plain urls and has no plugin behind it.
 */
object PluginPlayback {
    /** One action to offer, with a label that is already resolved for display. */
    data class Choice(
        val action: VideoClickAction,
        val label: String,
    )

    /**
     * The actions to offer for this episode, in registration order.
     *
     * `shouldShow` belongs to the plugin and can throw (it may read provider state that does not
     * exist on a desktop), so a failing action is skipped instead of breaking the player.
     */
    fun choicesFor(session: PlaybackSession, index: Int): List<Choice> {
        if (session.catalog !is LegacyCatalog) return emptyList()
        val actions = VideoClickActionHolder.allVideoClickActions
        if (actions.isEmpty()) return emptyList()

        val episode = resultEpisodeOf(session, index)
        return actions.mapNotNull { action ->
            val applies = runCatching {
                action.shouldShow(appContext(), episode)
            }.getOrDefault(false)
            if (!applies) return@mapNotNull null
            Choice(action, labelOf(action))
        }
    }

    /** Runs [action] for the session's [index], handing it freshly resolved links. */
    suspend fun run(session: PlaybackSession, index: Int, action: VideoClickAction): Result<Unit> {
        val catalog = session.catalog as? LegacyCatalog
            ?: return Result.failure(IllegalStateException("plugin players need a loaded extension"))
        val episode = session.episodes.getOrNull(index)
        val data = episode?.data ?: session.directDataUrl
        return catalog.playWith(action, data, resultEpisodeOf(session, index))
    }

    /** The plugin's name for the action, falling back to its class when it has no text. */
    fun labelOf(action: VideoClickAction): String {
        val text = runCatching { action.name.asString(appContext()) }.getOrNull()
        return text?.takeIf { it.isNotBlank() }
            ?: action.sourcePlugin?.let { "Plugin player" }
            ?: "Plugin player"
    }

    /**
     * The episode as upstream models it.
     *
     * Actions are written against `ResultEpisode`, so they receive the same fields the Android
     * app fills in: the id/data pair that identifies the episode, its season and number, where
     * playback is, and the item's type.
     */
    fun resultEpisodeOf(session: PlaybackSession, index: Int): ResultEpisode {
        val episode = session.episodes.getOrNull(index)
        val progress = Library.progressFor(session.itemUrl, episode?.data)
        return ResultEpisode(
            headerName = session.title,
            name = episode?.label,
            poster = session.posterUrl,
            episode = episode?.episode ?: 0,
            seasonIndex = episode?.season,
            season = episode?.season,
            data = episode?.data ?: session.directDataUrl ?: "",
            apiName = session.apiName,
            id = index,
            index = index,
            position = (progress?.positionMs ?: 0L) / 1000,
            duration = (progress?.durationMs ?: 0L) / 1000,
            score = null,
            description = null,
            isFiller = null,
            tvType = tvTypeOf(session.type),
            parentId = 0,
            videoWatchState = if (ResumePolicy.isWatched(progress)) {
                VideoWatchState.Watched
            } else {
                VideoWatchState.None
            },
            totalEpisodeIndex = index,
            airDate = null,
            runTime = null,
            seasonData = null,
        )
    }

    /** The desktop item type back to the upstream enum, so `shouldShow` sees what it expects. */
    private fun tvTypeOf(type: String): TvType = when (type.uppercase()) {
        "MOVIE" -> TvType.Movie
        "SERIES" -> TvType.TvSeries
        "ANIME", "OVA" -> TvType.Anime
        "LIVE" -> TvType.Live
        "NSFW" -> TvType.NSFW
        else -> TvType.Others
    }

    private fun appContext(): android.content.Context =
        com.lagradost.cloudstream3.CommonActivity.activity ?: android.content.Context()
}
