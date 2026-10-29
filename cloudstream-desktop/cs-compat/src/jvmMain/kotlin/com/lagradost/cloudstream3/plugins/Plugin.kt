package com.lagradost.cloudstream3.plugins

/**
 * The plugin base class older extensions were compiled against, before the current
 * [BasePlugin] name.
 *
 * Archives built against that API extend `Plugin` directly, so the class has to exist for
 * their plugin entry point to load — otherwise the JVM fails with
 * `NoClassDefFoundError: com/lagradost/cloudstream3/plugins/Plugin`. Extending [BasePlugin]
 * keeps `registerMainAPI`, `load`, `beforeUnload` and `filename` available exactly as the old
 * extensions use them, and makes them valid plugins for the desktop loader.
 */
@Deprecated("Extensions are built against BasePlugin now; this exists for older archives.")
abstract class Plugin : BasePlugin()
