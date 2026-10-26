package app.cloudstream.desktop.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** One row of the original's search history (`SearchHistoryItem`, search/SearchHistoryAdaptor.kt). */
@Serializable
data class SearchHistoryEntry(
    val searchText: String,
    val searchedAt: Long,
)

/**
 * The original's `SEARCH_HISTORY_KEY` DataStore list: recent searches, newest first, shown
 * under the search bar while the field is empty, each removable, with a "Clear history" footer.
 */
object SearchHistory {
    /** Newest first, and a repeated query moves back to the top instead of appearing twice. */
    private const val LIMIT = 20

    private val dir: Path = Paths.get(System.getProperty("user.home"), ".cloudstream-desktop")
    private val file: Path = dir.resolve("search_history.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<SearchHistoryEntry>> = _entries

    fun record(query: String) {
        val text = query.trim()
        if (text.isEmpty()) return
        val next = (listOf(SearchHistoryEntry(text, System.currentTimeMillis())) +
            _entries.value.filterNot { it.searchText.equals(text, ignoreCase = true) })
            .take(LIMIT)
        _entries.value = next
        save()
    }

    fun remove(searchText: String) {
        val next = _entries.value.filterNot { it.searchText == searchText }
        if (next.size == _entries.value.size) return
        _entries.value = next
        save()
    }

    fun clear() {
        if (_entries.value.isEmpty()) return
        _entries.value = emptyList()
        save()
    }

    private fun load(): List<SearchHistoryEntry> = runCatching {
        if (!Files.exists(file)) emptyList()
        else json.decodeFromString<List<SearchHistoryEntry>>(Files.readString(file))
    }.getOrElse { emptyList() }

    private fun save() {
        runCatching {
            Files.createDirectories(dir)
            Files.writeString(file, json.encodeToString(_entries.value))
        }
    }
}
