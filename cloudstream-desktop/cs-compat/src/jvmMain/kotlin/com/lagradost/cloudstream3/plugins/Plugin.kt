package com.lagradost.cloudstream3.plugins

import android.content.Context
import android.content.res.Resources

/**
 * Upstream's `Plugin` base class, as extensions are compiled against it.
 *
 * On Android this class lives in the app module, so it is not part of the `library` sources
 * that [com.lagradost.cloudstream3.plugins.BasePlugin] comes from; the desktop host has to
 * provide it for archives whose plugin class extends `Plugin` instead of `BasePlugin`.
 *
 * `load(context)` is the current entry point — `PluginManager.loadPlugin` calls it for every
 * `Plugin` instance and it falls back to the cross-platform `load()`. Reproducing that fallback
 * is what makes both generations of extensions work: newer archives override `load(context)`,
 * older ones override `load()`. Without the overload the newer ones load, register nothing and
 * look installed-but-broken.
 */
abstract class Plugin : BasePlugin() {
    /**
     * Called when the plugin is loaded.
     *
     * The default calls [load], so a plugin written against whichever API existed when it was
     * built is initialised either way.
     */
    @Throws(Throwable::class)
    open fun load(context: Context) {
        load()
    }

    /**
     * Populated when the archive's `manifest.json` sets `requiresResources`.
     *
     * The desktop host has no Android asset manager, so this is an empty resource set: enough
     * for a plugin that merely holds the reference, not for one that reads APK resources.
     */
    var resources: Resources? = null

    /** Upstream's hook for a plugin-supplied settings screen; nothing shows it here. */
    var openSettings: ((context: Context) -> Unit)? = null

    /**
     * Registers a playback action supplied by this plugin, like `Plugin.registerVideoClickAction`.
     *
     * The action is stored with the plugin's path so the host can attribute it. The desktop
     * player does not offer plugin actions yet — registering one is accepted and kept, so a
     * plugin that adds a cast receiver still loads and can be listed later.
     */
    fun registerVideoClickAction(element: com.lagradost.cloudstream3.actions.VideoClickAction) {
        element.sourcePlugin = filename
        com.lagradost.cloudstream3.actions.VideoClickActionHolder.allVideoClickActions.add(element)
    }

    companion object {
        /**
         * Compose's stability marker.
         *
         * Archives built with the Compose compiler read `$stable` off the classes it visited,
         * including this one; without the field the plugin dies with
         * `NoSuchFieldError: Class ... Plugin does not have member field 'int $stable'` before
         * it can register anything. The value is irrelevant to non-Compose code.
         */
        @JvmField
        val `$stable`: Int = 0
    }
}
