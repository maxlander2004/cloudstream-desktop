package app.cloudstream.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudstream.desktop.api.CatalogItem
import app.cloudstream.desktop.api.ItemType
import app.cloudstream.desktop.core.Library
import app.cloudstream.desktop.core.Settings
import app.cloudstream.desktop.core.WatchRecord
import app.cloudstream.desktop.core.watchLabel

/** The original's two library lists; upstream fills them from a sync account. */
private enum class LibraryTab(val label: String) {
    ContinueWatching("Continue watching"),
    Bookmarks("Bookmarks"),
}

/**
 * The original's Library: a filter field, media-type chips, tabs, a sort menu and a random
 * button over a poster grid. Upstream reads its lists from a sync account (AniList/MAL); this
 * build reads the local bookmarks and watch history instead.
 */
@Composable
fun LibraryScreen(
    settings: Settings,
    onOpen: (CatalogItem) -> Unit,
    onResume: (WatchRecord) -> Unit,
    onStatus: (String) -> Unit,
) {
    val version by Library.changes.collectAsState()
    val history = remember(version) { Library.history() }
    val bookmarks = remember(version) { Library.bookmarks() }

    var tab by remember { mutableStateOf(LibraryTab.ContinueWatching) }
    var filter by remember { mutableStateOf("") }
    var sortByName by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }

    // The same chips Home and the Providers settings use; empty means every media type.
    fun visible(items: List<CatalogItem>): List<CatalogItem> = items.filter { item ->
        (settings.preferredMedia.isEmpty() || item.type.name in settings.preferredMedia ||
            item.type == ItemType.OTHER) && (settings.nsfwEnabled || item.type != ItemType.NSFW)
    }

    val records: List<CatalogItem> = history.map { record ->
        CatalogItem(
            title = record.title,
            url = record.itemUrl,
            subtitle = watchLabel(record).takeIf { it.isNotBlank() },
            posterUrl = record.posterUrl,
            type = ItemType.entries.firstOrNull { it.name == record.type } ?: ItemType.OTHER,
        )
    }
    val marked: List<CatalogItem> = bookmarks.map { bookmark ->
        CatalogItem(
            title = bookmark.title,
            url = bookmark.url,
            subtitle = bookmark.apiName.takeIf { it.isNotBlank() },
            posterUrl = bookmark.posterUrl,
            type = ItemType.entries.firstOrNull { it.name == bookmark.type } ?: ItemType.OTHER,
        )
    }

    val shown = visible(if (tab == LibraryTab.ContinueWatching) records else marked)
        .filter { filter.isBlank() || it.title.contains(filter, ignoreCase = true) }
        .let { list -> if (sortByName) list.sortedBy { it.title.lowercase() } else list }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            "Library",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.weight(1f))
        if (shown.isNotEmpty()) {
            TextButton(onClick = { showSort = true }) {
                Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (sortByName) "A–Z" else "Newest")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onOpen(shown.random()) }) { Text("Random") }
        }
    }
    Spacer(Modifier.height(10.dp))

    // Upstream's library search field, which filters the current list.
    OutlinedTextField(
        value = filter,
        onValueChange = { filter = it },
        placeholder = { Text("Search your library") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (filter.isNotEmpty()) {
                IconButton(onClick = { filter = "" }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { }),
        modifier = Modifier.width(520.dp),
    )
    Spacer(Modifier.height(10.dp))

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LibraryTab.entries.forEach { entry ->
            FilterChip(
                selected = tab == entry,
                onClick = { tab = entry },
                label = { Text(entry.label) },
            )
        }
        Spacer(Modifier.width(8.dp))
        MediaTypeChips.forEach { (media, label) ->
            FilterChip(
                selected = settings.preferredMedia.isEmpty() || media in settings.preferredMedia,
                onClick = {
                    val selected = settings.preferredMedia
                    settings.preferredMedia = when {
                        selected.isEmpty() -> MediaTypeChips.map { it.first } - media
                        media in selected -> selected - media
                        else -> selected + media
                    }
                },
                label = { Text(label, fontSize = 11.sp) },
            )
        }
    }
    Spacer(Modifier.height(12.dp))

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            "${bookmarks.size} bookmark(s) · ${history.size} in history",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
        Spacer(Modifier.weight(1f))
        if (history.isNotEmpty()) {
            TextButton(
                onClick = {
                    Library.clearHistory()
                    onStatus("Watch history cleared")
                },
            ) { Text("Clear history", fontSize = 12.sp) }
        }
        if (bookmarks.isNotEmpty()) {
            TextButton(
                onClick = {
                    bookmarks.forEach { Library.removeBookmark(it.url) }
                    onStatus("Bookmarks cleared")
                },
            ) { Text("Clear bookmarks", fontSize = 12.sp) }
        }
    }
    Spacer(Modifier.height(10.dp))

    if (shown.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            Text(
                when {
                    tab == LibraryTab.Bookmarks && bookmarks.isEmpty() ->
                        "Nothing bookmarked yet — open an item and tap the bookmark button."
                    tab == LibraryTab.ContinueWatching && history.isEmpty() ->
                        "Nothing watched yet — play something and it will show up here."
                    else -> "Nothing matches that filter."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
    } else {
        PosterGrid(
            items = shown,
            posterSize = settings.posterSize,
            titleUnder = settings.posterTitleUnder,
            onOpen = { item -> openLibraryItem(item, tab, history, onOpen, onResume) },
        )
    }

    if (showSort) {
        AlertDialog(
            onDismissRequest = { showSort = false },
            title = { Text("Sort by") },
            text = {
                Column {
                    listOf(
                        true to "Name (A–Z)",
                        false to "Newest first",
                    ).forEach { (byName, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    sortByName = byName
                                    showSort = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                        ) {
                            Text(
                                label,
                                color = if (sortByName == byName) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                fontWeight = if (sortByName == byName) FontWeight.Medium else FontWeight.Normal,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSort = false }) { Text("Close") } },
        )
    }
}

/** A history entry reopens its item with the episode it was left on; a bookmark opens plain. */
private fun openLibraryItem(
    item: CatalogItem,
    tab: LibraryTab,
    history: List<WatchRecord>,
    onOpen: (CatalogItem) -> Unit,
    onResume: (WatchRecord) -> Unit,
) {
    val record = history.firstOrNull { it.itemUrl == item.url }
    if (tab == LibraryTab.ContinueWatching && record != null) onResume(record) else onOpen(item)
}
