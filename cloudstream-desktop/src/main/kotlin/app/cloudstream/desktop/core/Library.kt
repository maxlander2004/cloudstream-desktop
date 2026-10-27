package app.cloudstream.desktop.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * One bookmarked item.
 *
 * The original's Library is backed by a sync account (AniList/MAL lists); this desktop build
 * keeps the same structure — a grid of poster cards with search, tabs and sorting — over a
 * local store, because a sync account needs OAuth credentials this app does not have.
 */
@Serializable
data class Bookmark(
    val url: String,
    val apiName: String,
    val title: String,
    val posterUrl: String? = null,
    val type: String = "OTHER",
    val addedAt: Long = System.currentTimeMillis(),
)

/** The last episode the user opened for an item — upstream's continue-watching entry. */
@Serializable
data class WatchRecord(
    val itemUrl: String,
    val apiName: String,
    val title: String,
    val posterUrl: String? = null,
    val type: String = "OTHER",
    /** Upstream `Episode.data` for the episode that was started; null for movies. */
    val episodeData: String? = null,
    val episodeLabel: String = "",
    val season: Int? = null,
    val episode: Int? = null,
    val watchedAt: Long = System.currentTimeMillis(),
)

/**
 * Where playback was left, per item and episode — the counterpact of the original's
 * DataStore `watchedPosition`/`watchedDuration` pair.
 */
@Serializable
data class WatchProgress(
    val itemUrl: String,
    val episodeData: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    /** 0..1; 0 when the length is not known yet. */
    val fraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

@Serializable
private data class LibraryFile(
    val bookmarks: List<Bookmark> = emptyList(),
    val history: List<WatchRecord> = emptyList(),
    val watchedEpisodes: List<String> = emptyList(),
    val searches: List<String> = emptyList(),
    val progress: List<WatchProgress> = emptyList(),
)

/**
 * Bookmarks, watch history, watched episodes and recent searches, stored as JSON next to the
 * settings file — the desktop counterpart of the original's DataStore-backed library.
 */
object Library {
    private const val MAX_HISTORY = 200
    private const val MAX_SEARCHES = 15

    private val file: Path =
        Paths.get(System.getProperty("user.home"), ".cloudstream-desktop", "library.json")

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val lock = Any()
    private var data: LibraryFile = read()

    /** Bumped on every change so the screens re-read the lists. */
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes

    private fun read(): LibraryFile = runCatching {
        if (Files.isRegularFile(file)) {
            json.decodeFromString<LibraryFile>(Files.readString(file))
        } else {
            LibraryFile()
        }
    }.getOrElse { LibraryFile() }

    private fun write() {
        runCatching {
            Files.createDirectories(file.parent)
            Files.writeString(file, json.encodeToString(data))
        }
    }

    private fun commit(updated: LibraryFile): LibraryFile {
        data = updated
        write()
        _changes.value += 1
        return updated
    }

    // ------------------------------------------------------------------ bookmarks

    fun bookmarks(): List<Bookmark> = synchronized(lock) {
        data.bookmarks.sortedByDescending { it.addedAt }
    }

    fun isBookmarked(url: String): Boolean = synchronized(lock) {
        data.bookmarks.any { it.url == url }
    }

    /** Adds or removes [bookmark]; returns true when it is now bookmarked. */
    fun toggleBookmark(bookmark: Bookmark): Boolean = synchronized(lock) {
        val existing = data.bookmarks.firstOrNull { it.url == bookmark.url }
        if (existing != null) {
            commit(data.copy(bookmarks = data.bookmarks - existing))
            false
        } else {
            commit(data.copy(bookmarks = listOf(bookmark) + data.bookmarks))
            true
        }
    }

    fun removeBookmark(url: String) {
        synchronized(lock) {
            commit(data.copy(bookmarks = data.bookmarks.filterNot { it.url == url }))
        }
    }

    // ------------------------------------------------------------------ watch history

    /** Continue watching: one entry per item, most recently watched first. */
    fun history(): List<WatchRecord> = synchronized(lock) {
        data.history.sortedByDescending { it.watchedAt }
    }

    fun lastWatched(itemUrl: String): WatchRecord? = synchronized(lock) {
        data.history.filter { it.itemUrl == itemUrl }.maxByOrNull { it.watchedAt }
    }

    fun isEpisodeWatched(episodeData: String): Boolean = synchronized(lock) {
        episodeData in data.watchedEpisodes
    }

    /** Records an episode as started, refreshing the item's continue-watching entry. */
    fun recordWatch(record: WatchRecord) {
        synchronized(lock) {
            val rest = data.history.filterNot { it.itemUrl == record.itemUrl }
            val trimmed = (listOf(record) + rest).take(MAX_HISTORY)
            val watched = record.episodeData?.let { url ->
                (listOf(url) + data.watchedEpisodes.filterNot { it == url })
            } ?: data.watchedEpisodes
            commit(data.copy(history = trimmed, watchedEpisodes = watched))
        }
    }

    fun forget(itemUrl: String) {
        synchronized(lock) {
            val removed = data.history.filter { it.itemUrl == itemUrl }.mapNotNull { it.episodeData }
            commit(
                data.copy(
                    history = data.history.filterNot { it.itemUrl == itemUrl },
                    watchedEpisodes = data.watchedEpisodes - removed.toSet(),
                ),
            )
        }
    }

    fun clearHistory() {
        synchronized(lock) {
            commit(data.copy(history = emptyList(), watchedEpisodes = emptyList()))
        }
    }

    // ------------------------------------------------------------------ playback progress

    /** The saved position for an item, and for one episode of it when it has episodes. */
    fun progressFor(itemUrl: String, episodeData: String? = null): WatchProgress? = synchronized(lock) {
        data.progress
            .filter { it.itemUrl == itemUrl && it.episodeData == episodeData }
            .maxByOrNull { it.updatedAt }
    }

    fun saveProgress(progress: WatchProgress) {
        synchronized(lock) {
            val rest = data.progress.filterNot {
                it.itemUrl == progress.itemUrl && it.episodeData == progress.episodeData
            }
            commit(data.copy(progress = (listOf(progress) + rest).take(MAX_HISTORY * 2)))
        }
    }

    fun clearProgress(itemUrl: String) {
        synchronized(lock) {
            commit(data.copy(progress = data.progress.filterNot { it.itemUrl == itemUrl }))
        }
    }

    fun clearProgress(itemUrl: String, episodeData: String?) {
        synchronized(lock) {
            commit(
                data.copy(
                    progress = data.progress.filterNot {
                        it.itemUrl == itemUrl && it.episodeData == episodeData
                    },
                ),
            )
        }
    }

    // ------------------------------------------------------------------ searches

    fun searches(): List<String> = synchronized(lock) { data.searches }

    fun addSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        synchronized(lock) {
            val rest = data.searches.filterNot { it.equals(trimmed, ignoreCase = true) }
            commit(data.copy(searches = (listOf(trimmed) + rest).take(MAX_SEARCHES)))
        }
    }

    fun removeSearch(query: String) {
        synchronized(lock) {
            commit(data.copy(searches = data.searches.filterNot { it == query }))
        }
    }

    fun clearSearches() {
        synchronized(lock) {
            commit(data.copy(searches = emptyList()))
        }
    }
}
