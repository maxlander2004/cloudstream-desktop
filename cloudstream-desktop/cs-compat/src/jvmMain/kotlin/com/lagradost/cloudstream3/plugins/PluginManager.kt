package com.lagradost.cloudstream3.plugins

/**
 * Upstream's `PluginManager`, reduced to the plugin registry extensions read.
 *
 * Extensions use it to look themselves up — to find their own version, their install path, or
 * to check whether another plugin is present. The desktop host installs archives from a
 * repository and keeps that list itself, so this registry is deliberately empty: a plugin that
 * looks for itself here simply does not find an online record, which is the truth on a desktop
 * that installed it from a file.
 */
object PluginManager {
    /** Upstream's online plugin list (`PluginManager.pluginsOnline`). */
    var pluginsOnline: Array<PluginData> = emptyArray()
}
