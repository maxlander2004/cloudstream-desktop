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

    /** Provider language as upstream reports it, e.g. "en"; null when unknown. */
    val language: String?
        get() = null

    /** Featured/landing content for this source. */
    suspend fun browse(): List<CatalogItem>

    /**
     * The home page rows of the original app: every section [MainAPI.mainPage] declares,
     * fetched the same way `HomeViewModel` fetches them, so the desktop home screen shows the
     * same titled, horizontally scrolling rows the phone app shows.
     */
    suspend fun browseSections(): List<HomeSection> = listOf(HomeSection(name, browse()))

    /** Query results; sources that cannot search may keep the default. */
    suspend fun search(query: String): List<CatalogItem> = emptyList()

    /** Playable links for an item. */
    suspend fun streams(item: CatalogItem): List<StreamLink>
}

/**
 * Content category, named after upstream's `TvType` so extension types map 1:1. Used by the
 * Providers settings (preferred media, NSFW filter) exactly like the Android app uses them.
 */
enum class ItemType { MOVIE, SERIES, ANIME, LIVE, NSFW, OTHER }

/** One home row: a section title and its posters, like upstream's `HomePageList`. */
data class HomeSection(
    val name: String,
    val items: List<CatalogItem>,
)

data class CatalogItem(
    val title: String,
    val url: String,
    val subtitle: String? = null,
    val posterUrl: String? = null,
    val type: ItemType = ItemType.OTHER,
)

data class StreamLink(
    val title: String,
    val url: String,
    /** Request headers the source needs, e.g. `Referer`; passed on when the player accepts them. */
    val headers: Map<String, String> = emptyMap(),
)
