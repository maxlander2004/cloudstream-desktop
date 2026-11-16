package com.lagradost.cloudstream3.actions

import android.content.Context
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.UiText

/**
 * Upstream's `VideoClickAction`: a way to play a link that a plugin adds to the app.
 *
 * Extensions use it to hand playback to their own player — a cast receiver, an external app,
 * a torrent streamer. Upstream this class also wraps activity results and UI thread hops; those
 * live in the Android app shell, so they are not part of this file. What matters for loading is
 * the shape: extensions subclass it, register it from `Plugin.registerVideoClickAction`, and
 * override [name], [shouldShow] and [runAction].
 *
 * The desktop host keeps a registry (see [VideoClickActionHolder]) so a plugin's actions load
 * without error. Wiring them into the Compose player is a separate piece of work: nothing
 * offers them on a link row yet.
 */
abstract class VideoClickAction {
    /** Label shown when the user picks a player. */
    abstract val name: UiText

    /** If true the app shows a source picker before running the action. */
    open val oneSource: Boolean = false

    /** If true, this action can be chosen as the default player. */
    open val isPlayer: Boolean = false

    /** Which link types this action can handle. */
    open val sourceTypes: Set<ExtractorLinkType> = ExtractorLinkType.entries.toSet()

    /** Full path of the plugin that registered this action, set by the host. */
    var sourcePlugin: String? = null

    /** Whether this action is offered for a given episode. */
    abstract fun shouldShow(context: Context?, video: ResultEpisode?): Boolean

    /** Plays the selected link. */
    abstract suspend fun runAction(
        context: Context?,
        video: ResultEpisode,
        result: LinkLoadingResult,
        index: Int?,
    )
}

/** Upstream's registry of plugin-supplied click actions. */
object VideoClickActionHolder {
    val allVideoClickActions = mutableListOf<VideoClickAction>()
}
