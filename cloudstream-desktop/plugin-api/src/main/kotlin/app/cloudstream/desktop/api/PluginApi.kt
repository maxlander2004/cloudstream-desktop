package app.cloudstream.desktop.api

/**
 * Desktop counterpart of CloudStream's `BasePlugin` / `MainAPI` contract.
 *
 * The Android app loads extensions with `dalvik.system.PathClassLoader`, so published
 * extensions are Dalvik bytecode and cannot load in a JVM. A desktop extension is a plain
 * `.jar` that implements [Catalog] and ships a `manifest.json` with the same
 * `pluginClassName` key upstream uses.
 */
const val PLUGIN_API_VERSION: Int = 1

interface Catalog {
    /** Shown in the sidebar and in the extensions list. */
    val name: String

    /** One-line description shown under the source name. */
    val description: String
        get() = ""

    /** Featured/landing content for this source. */
    suspend fun browse(): List<CatalogItem>

    /** Query results; sources that cannot search may keep the default. */
    suspend fun search(query: String): List<CatalogItem> = emptyList()

    /** Playable links for an item. */
    suspend fun streams(item: CatalogItem): List<StreamLink>
}

data class CatalogItem(
    val title: String,
    val url: String,
    val subtitle: String? = null,
    val posterUrl: String? = null,
)

data class StreamLink(
    val title: String,
    val url: String,
    /** Request headers the source needs, e.g. `Referer`; passed on when the player accepts them. */
    val headers: Map<String, String> = emptyMap(),
)
