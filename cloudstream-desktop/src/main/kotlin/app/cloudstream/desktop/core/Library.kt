package app.cloudstream.desktop.core

import app.cloudstream.desktop.api.CatalogItem
import app.cloudstream.desktop.api.ItemType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The original's `WatchType` (app/…/ui/WatchType.kt), by the numeric id DataStore stores, so a
 * bookmark set here means the same thing the Android app stores for the same poster.
 */
object WatchStatus {
    const val WATCHING = 0
    const val COMPLETED = 1
    const val ON_HOLD = 2
    const val DROPPED = 3
    const val PLAN_TO_WATCH = 4
    const val NONE = 5

    /** Every status a bookmark can have, in the original enum's order (NONE excluded). */
    val bookmarks = listOf(WATCHING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_WATCH)

    /** The upstream strings: type_watching … type_none. */
    fun label(status: Int): String = when (status) {
        WATCHING -> "Watching"
        COMPLETED -> "Completed"
        ON_HOLD -> "On-Hold"
        DROPPED -> "Dropped"
        PLAN_TO_WATCH -> "Plan to Watch"
        else -> "None"
    }
}

/**
 * One item in the local library — what `DataStoreHelper` keeps for a bookmark, a favorite or a
 * subscription in the original app.
 */
@Serializable
data class LibraryEntry(
    val title: String,
    val url: String,
    /** Source the item came from, so the library opens it in the provider that found it. */
    val source: String = "",
    val posterUrl: String? = null,
    val subtitle: String? = null,
    /** [ItemType] name, kept as a string so the file stays readable. */
    val type: String = ItemType.OTHER.name,
    /** `WatchType` id; [WatchStatus.NONE] when the entry is only a favorite or a subscription. */
    val status: Int = WatchStatus.NONE,
    val favorite: Boolean = false,
    val subscribed: Boolean = false,
    /** Epoch millis, the "Updated" sort's key. */
    val addedAt: Long = 0L,
) {
    fun toCatalogItem(): CatalogItem = CatalogItem(
        title = title,
        url = url,
        subtitle = subtitle,
        posterUrl = posterUrl,
        type = ItemType.entries.firstOrNull { it.name == type } ?: ItemType.OTHER,
    )
}

/**
 * The desktop counterpart of the original's `LocalList` sync provider: the local pages the
 * Library screen's tabs list, stored next to the settings file. Writing is synchronous and the
 * state is a flow, so the Library tab and the result screen's bookmark button stay in step.
 */
object Library {
    private val dir: Path = Paths.get(System.getProperty("user.home"), ".cloudstream-desktop")
    private val file: Path = dir.resolve("library.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<LibraryEntry>> = _entries

    fun entry(url: String): LibraryEntry? = _entries.value.firstOrNull { it.url == url }

    fun status(url: String): Int = entry(url)?.status ?: WatchStatus.NONE

    fun isFavorite(url: String): Boolean = entry(url)?.favorite == true

    fun isSubscribed(url: String): Boolean = entry(url)?.subscribed == true

    /** The result screen's bookmark button: set the watch status, NONE clears the bookmark. */
    fun setStatus(item: CatalogItem, source: String, status: Int) =
        update(item, source) { it.copy(status = status) }

    /** The result screen's favorite button; returns the new state for the status line. */
    fun toggleFavorite(item: CatalogItem, source: String): Boolean {
        val next = !isFavorite(item.url)
        update(item, source) { it.copy(favorite = next) }
        return next
    }

    /** "New episode notification" — the Subscribed page of the local library. */
    fun toggleSubscription(item: CatalogItem, source: String): Boolean {
        val next = !isSubscribed(item.url)
        update(item, source) { it.copy(subscribed = next) }
        return next
    }

    fun remove(url: String) {
        val next = _entries.value.filterNot { it.url == url }
        if (next.size == _entries.value.size) return
        _entries.value = next
        save()
    }

    private fun update(item: CatalogItem, source: String, transform: (LibraryEntry) -> LibraryEntry) {
        val existing = entry(item.url)
        val base = existing ?: LibraryEntry(
            title = item.title,
            url = item.url,
            source = source,
            posterUrl = item.posterUrl,
            subtitle = item.subtitle,
            type = item.type.name,
            addedAt = System.currentTimeMillis(),
        )
        val next = transform(base)
        // An entry with no bookmark, no favorite and no subscription is not in any list, which
        // is exactly how LocalList decides what to show.
        val keep = next.status != WatchStatus.NONE || next.favorite || next.subscribed
        _entries.value = _entries.value.filterNot { it.url == next.url } + if (keep) listOf(next) else emptyList()
        save()
    }

    private fun load(): List<LibraryEntry> = runCatching {
        if (!Files.exists(file)) emptyList()
        else json.decodeFromString<List<LibraryEntry>>(Files.readString(file))
    }.getOrElse { emptyList() }

    private fun save() {
        runCatching {
            Files.createDirectories(dir)
            Files.writeString(file, json.encodeToString(_entries.value))
        }
    }
}
