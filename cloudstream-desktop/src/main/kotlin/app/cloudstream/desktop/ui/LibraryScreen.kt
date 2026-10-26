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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudstream.desktop.core.LibraryEntry
import app.cloudstream.desktop.core.WatchStatus

/**
 * The original LibraryViewModel's `ListSorting`, limited to the keys a local library has
 * (alphabetical and by the time the entry was added — the app's "Updated" ordering).
 */
internal enum class LibrarySort(val label: String) {
    AlphabeticalA("Alphabetical (A to Z)"),
    AlphabeticalZ("Alphabetical (Z to A)"),
    UpdatedNew("Updated (New to Old)"),
    UpdatedOld("Updated (Old to New)");

    fun sorted(entries: List<LibraryEntry>): List<LibraryEntry> = when (this) {
        AlphabeticalA -> entries.sortedBy { it.title.lowercase() }
        AlphabeticalZ -> entries.sortedByDescending { it.title.lowercase() }
        UpdatedNew -> entries.sortedByDescending { it.addedAt }
        UpdatedOld -> entries.sortedBy { it.addedAt }
    }
}

/**
 * The pages `LocalList.library()` builds: one per watch status, then Favorites and Subscribed.
 * Same labels, same order, so the tab strip reads like the original's.
 */
internal enum class LibraryTab(val label: String) {
    Watching("Watching"),
    Completed("Completed"),
    OnHold("On-Hold"),
    Dropped("Dropped"),
    PlanToWatch("Plan to Watch"),
    Favorites("Favorites"),
    Subscribed("Subscribed");

    fun matches(entry: LibraryEntry): Boolean = when (this) {
        Watching -> entry.status == WatchStatus.WATCHING
        Completed -> entry.status == WatchStatus.COMPLETED
        OnHold -> entry.status == WatchStatus.ON_HOLD
        Dropped -> entry.status == WatchStatus.DROPPED
        PlanToWatch -> entry.status == WatchStatus.PLAN_TO_WATCH
        Favorites -> entry.favorite
        Subscribed -> entry.subscribed
    }
}

/**
 * fragment_library.xml on the desktop: the search bar and sort button at the top, the poster
 * grid in the middle, and the scrollable tab strip along the bottom — with the Random button
 * on the left when *Random Button* is enabled, exactly where the original floats it.
 */
@Composable
fun LibraryScreen(
    entries: List<LibraryEntry>,
    posterSize: Int,
    titleUnder: Boolean,
    randomButton: Boolean,
    onOpen: (LibraryEntry) -> Unit,
) {
    var tab by remember { mutableStateOf(LibraryTab.Watching) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(LibrarySort.AlphabeticalA) }
    var sortMenu by remember { mutableStateOf(false) }

    val visible = remember(entries, tab, query, sort) {
        sort.sorted(
            entries
                .filter { tab.matches(it) }
                .filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) },
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            // main_search: rounded field with the search icon and a clear button.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp),
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                "Search",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp,
                            )
                        }
                        inner()
                    },
                )
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.width(10.dp))

            // library_sort: the sort menu over the current ordering.
            Box {
                TextButton(onClick = { sortMenu = true }) {
                    Icon(Icons.Outlined.Sort, contentDescription = "Sort", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Sort", fontSize = 13.sp)
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    LibrarySort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    option.label,
                                    color = if (option == sort) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            },
                            onClick = {
                                sortMenu = false
                                sort = option
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (visible.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    if (query.isBlank()) "Nothing in ${tab.label} yet." else "No matches in ${tab.label}.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                ItemList(
                    items = visible.map { it.toCatalogItem() },
                    posterSize = posterSize,
                    titleUnder = titleUnder,
                    onOpen = { item -> visible.firstOrNull { it.url == item.url }?.let(onOpen) },
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Tab strip along the bottom, with the Random button where the original floats it.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (randomButton && visible.isNotEmpty()) {
                Button(onClick = { visible.randomOrNull()?.let(onOpen) }) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Random")
                }
                Spacer(Modifier.width(12.dp))
            }
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LibraryTab.entries.forEach { entry ->
                    LibraryTabButton(
                        label = entry.label,
                        selected = entry == tab,
                        onClick = { tab = entry },
                    )
                }
            }
        }
    }
}

/** One tab of the original's TabLayout: a pill indicator behind the selected label. */
@Composable
private fun LibraryTabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/**
 * The result screen's bookmark button (`result_bookmark_fab`): the current watch status, and a
 * menu with WatchType's entries — the original's "Set watch status" dialog.
 */
@Composable
internal fun WatchStatusButton(status: Int, onSetStatus: (Int) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val bookmarked = status != WatchStatus.NONE

    Box {
        TextButton(onClick = { menu = true }) {
            Icon(
                if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = "Set watch status",
                tint = if (bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                WatchStatus.label(status),
                fontSize = 13.sp,
                color = if (bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            WatchStatus.bookmarks.forEach { option ->
                DropdownMenuItem(
                    text = { Text(WatchStatus.label(option)) },
                    onClick = {
                        menu = false
                        onSetStatus(option)
                    },
                )
            }
            if (bookmarked) {
                DropdownMenuItem(
                    text = { Text("Remove bookmark") },
                    onClick = {
                        menu = false
                        onSetStatus(WatchStatus.NONE)
                    },
                )
            }
        }
    }
}
