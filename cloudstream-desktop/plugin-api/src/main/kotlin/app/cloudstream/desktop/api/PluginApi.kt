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

    /**
     * The seasons and episodes of a series — the list the original app's result screen shows
     * after you tap a poster. Movies, live streams and sources that cannot report episodes keep
     * the default empty list and play straight through [streams].
     */
    suspend fun episodeGroups(item: CatalogItem): List<EpisodeGroup> = emptyList()

    /** Playable links for an item. */
    suspend fun streams(item: CatalogItem): List<StreamLink>

    /** Playable links for one [episode] of a series; the default covers sources without episodes. */
    suspend fun streams(item: CatalogItem, episode: Episode): List<StreamLink> = streams(item)
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

/**
 * One episode of a series, the desktop counterpart of upstream's `ResultEpisode`. The fields
 * are exactly the ones the original lays out on an episode row.
 */
data class Episode(
    /** Opaque id handed back to [Catalog.streams] so the source can resolve this episode. */
    val data: String,
    /** Episode number; drives the "Episode 1" / "1. Jobless" label. */
    val episode: Int,
    /** Episode title when the source has one, as upstream's `Episode.name`. */
    val name: String? = null,
    val season: Int? = null,
    /** Episode still, shown in the large row. */
    val posterUrl: String? = null,
    /** Episode synopsis, the four-line description under the row. */
    val overview: String? = null,
    /** Rating out of 10, rendered as the original's "Rated: 8.8". */
    val rating: Double? = null,
    /** Air date in epoch milliseconds. */
    val airDate: Long? = null,
    /** Runtime in minutes, rendered as the original's "80min". */
    val runTime: Int? = null,
    /** Marks the row with the original's "Filler" chip. */
    val isFiller: Boolean = false,
) {
    /** The label the original builds: "Episode 1" without a name, "1. Jobless" with one. */
    val label: String
        get() = if (name.isNullOrBlank()) "Episode $episode" else "$episode. $name"
}

/**
 * A season of a series, or the Sub/Dub list of an anime — one entry of the episode picker.
 */
data class EpisodeGroup(
    /** "Season 1" for a series, "Sub"/"Dub" for an anime, as the original's selector shows. */
    val label: String,
    val episodes: List<Episode>,
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
