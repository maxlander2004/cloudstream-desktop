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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudstream.desktop.core.ExtensionHost
import app.cloudstream.desktop.core.ExtensionLoadResult
import app.cloudstream.desktop.core.InstalledArchive
import app.cloudstream.desktop.core.RepositoryPlugin
import app.cloudstream.desktop.core.RepositorySet
import app.cloudstream.desktop.core.Settings
import java.awt.Desktop
import java.nio.file.Path

/** Which of the original's extension screens is open. */
private enum class Pane { Repositories, Installed }

/**
 * Settings → Extensions, following the original app's flow rather than a single settings page:
 *
 *  - the repository list, each row opening that repository (`repository_item.xml`);
 *  - inside a repository, its plugins with the content-type chips and one download/delete
 *    button per row (`ExtensionsScreen.Plugin` upstream);
 *  - a separate *Installed extensions* screen, as the original's overflow menu provides;
 *  - the `+` button that adds a repository, docked bottom-end like the original's FAB.
 */
@Composable
fun ExtensionsScreen(
    settings: Settings,
    extensions: ExtensionLoadResult,
    installed: List<InstalledArchive>,
    repos: RepositorySet?,
    repoLoading: Boolean,
    repoError: String?,
    repoQuery: String,
    onQueryChange: (String) -> Unit,
    installing: String?,
    onReload: () -> Unit,
    onInstallFile: () -> Unit,
    onInstallUrl: (String) -> Unit,
    onRefreshRepository: () -> Unit,
    onUpdateAll: () -> Unit,
    onInstallRepository: (RepositoryPlugin) -> Unit,
    onUninstall: (Path) -> Unit,
    onToggleEnabled: (Path, Boolean) -> Unit,
    onStatus: (String) -> Unit,
) {
    var pane by remember { mutableStateOf(Pane.Repositories) }
    /** Url of the repository whose plugins are listed; null shows the repository list. */
    var openRepository by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var showAddRepository by remember { mutableStateOf(false) }
    var showInstallUrl by remember { mutableStateOf(false) }

    val repository = openRepository?.let { url -> repos?.repositories?.firstOrNull { it.url == url } }
    val title = when {
        openRepository != null -> repository?.name ?: settings.repositoryUrls
            .firstOrNull { it == openRepository } ?: "Extensions"
        pane == Pane.Installed -> "Installed extensions"
        else -> "Extensions"
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ---------------------------------------------------------------- top bar
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (openRepository != null || pane == Pane.Installed) {
                    IconButton(
                        onClick = {
                            openRepository = null
                            pane = Pane.Repositories
                            onQueryChange("")
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.weight(1f))
                if (repoLoading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                }
                // The original's overflow menu on the extensions toolbar.
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Add repository") },
                            onClick = {
                                menu = false
                                showAddRepository = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Installed extensions") },
                            onClick = {
                                menu = false
                                openRepository = null
                                pane = Pane.Installed
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Update plugins manually") },
                            onClick = {
                                menu = false
                                onUpdateAll()
                            },
                        )
                        HorizontalDivider()
                        // Desktop-only install paths: Android receives these through file
                        // intents and `.cs3` links instead of a picker.
                        DropdownMenuItem(
                            text = { Text("Install from file…") },
                            onClick = {
                                menu = false
                                onInstallFile()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Install from URL…") },
                            onClick = {
                                menu = false
                                showInstallUrl = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Reload extensions") },
                            onClick = {
                                menu = false
                                onReload()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Open extensions folder") },
                            onClick = {
                                menu = false
                                runCatching {
                                    Desktop.getDesktop().open(ExtensionHost.ensureExtensionsDir().toFile())
                                }
                                onStatus("Opened ${ExtensionHost.ensureExtensionsDir()}")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Refresh repositories") },
                            onClick = {
                                menu = false
                                onRefreshRepository()
                            },
                        )
                    }
                }
            }

            // ---------------------------------------------------------------- search
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = repoQuery,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(if (openRepository != null) "Search extensions" else "Search repositories")
                },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            when {
                pane == Pane.Installed -> InstalledList(
                    installed = installed,
                    extensions = extensions,
                    onUninstall = onUninstall,
                    onToggleEnabled = onToggleEnabled,
                )

                openRepository != null || repository != null -> RepositoryPlugins(
                    repository = repository,
                    repositoryUrl = openRepository,
                    query = repoQuery,
                    installed = installed,
                    installing = installing,
                    repoLoading = repoLoading,
                    repoError = repoError,
                    onInstall = onInstallRepository,
                    onUninstall = onUninstall,
                )

                else -> RepositoryList(
                    settings = settings,
                    repos = repos,
                    repoLoading = repoLoading,
                    repoError = repoError,
                    query = repoQuery,
                    onOpen = { url -> openRepository = url },
                    onRemove = { url ->
                        settings.repositoryUrls = settings.repositoryUrls - url
                        onStatus("Repository removed")
                        onRefreshRepository()
                    },
                    onRefreshRepository = onRefreshRepository,
                )
            }
        }

        if (pane == Pane.Repositories && openRepository == null) {
            ExtendedFloatingActionButton(
                onClick = { showAddRepository = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add repository") },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
            )
        }
    }

    if (showAddRepository) {
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddRepository = false },
            title = { Text("Add repository") },
            text = {
                Column {
                    Text(
                        "Paste a repository url (repo.json) — shortcodes like megarepo work too.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        placeholder = { Text("https://…/repo.json or megarepo") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val entry = url.trim()
                        if (entry.isNotEmpty()) {
                            settings.repositoryUrls = settings.repositoryUrls + entry
                            onStatus("Repository added")
                            onRefreshRepository()
                        }
                        showAddRepository = false
                    },
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddRepository = false }) { Text("Cancel") } },
        )
    }

    if (showInstallUrl) {
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showInstallUrl = false },
            title = { Text("Install from URL") },
            text = {
                Column {
                    Text(
                        "Paste a link to a .cs3 archive (or a desktop .jar). It is downloaded, " +
                            "verified by loading it, and kept in the extensions folder.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        placeholder = { Text("https://…/provider.cs3") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val entry = url.trim()
                        if (entry.isNotEmpty()) onInstallUrl(entry)
                        showInstallUrl = false
                    },
                ) { Text("Install") }
            },
            dismissButton = { TextButton(onClick = { showInstallUrl = false }) { Text("Cancel") } },
        )
    }
}

/** The repository list, as `repository_item.xml` draws it: name, url, count, arrow. */
@Composable
private fun RepositoryList(
    settings: Settings,
    repos: RepositorySet?,
    repoLoading: Boolean,
    repoError: String?,
    query: String,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRefreshRepository: () -> Unit,
) {
    val urls = settings.repositoryUrls
    val expanded = repos?.repositories.orEmpty()
    val expandedUrls = expanded.map { it.url }.toSet()
    // A configured entry that is not a url is a shortcode such as `megarepo`. It expands to
    // repositories that carry their own urls, so the shortcode keeps a row of its own above
    // them — that is also the only row that can remove the expansion again.
    val shortcodes = urls.filter { entry -> !entry.startsWith("http") && entry !in expandedUrls }
    val failures = repos?.failures.orEmpty().filter { it.first !in expandedUrls }
    fun matchesQuery(text: String) = query.isBlank() || text.contains(query, ignoreCase = true)

    when {
        repoError != null -> Column {
            Text(repoError, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onRefreshRepository) { Text("Retry") }
        }

        repos == null && repoLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text("Loading repositories…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        urls.isEmpty() -> Column {
            Text(
                "No repositories. Add one to browse the extensions it offers.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        else -> {
            val visibleShortcodes = shortcodes.filter(::matchesQuery)
            val visibleRepos = expanded.filter { matchesQuery(it.name) || matchesQuery(it.url.orEmpty()) }
            val visibleFailures = failures.filter { matchesQuery(it.first) }

            if (visibleShortcodes.isEmpty() && visibleRepos.isEmpty() && visibleFailures.isEmpty()) {
                Text(
                    "No repository matches \"$query\".",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(visibleShortcodes, key = { "shortcode:$it" }) { entry ->
                        RepositoryRowItem(
                            name = entry,
                            subtitle = "Shortcode",
                            detail = "expands to the repositories below",
                            failed = false,
                            onClick = null,
                            onRemove = { onRemove(entry) },
                        )
                    }
                    items(visibleRepos, key = { it.url.orEmpty() }) { state ->
                        RepositoryRowItem(
                            name = state.name,
                            subtitle = state.url.orEmpty(),
                            detail = if (state.plugins.size == 1) {
                                "1 extension"
                            } else {
                                "${state.plugins.size} extensions"
                            },
                            failed = false,
                            onClick = { onOpen(state.url.orEmpty()) },
                            onRemove = state.url?.takeIf { it in urls }?.let { url -> { onRemove(url) } },
                        )
                    }
                    items(visibleFailures, key = { "failed:${it.first}" }) { (url, reason) ->
                        RepositoryRowItem(
                            name = url,
                            subtitle = reason,
                            detail = "Unreachable",
                            failed = true,
                            onClick = null,
                            onRemove = url.takeIf { it in urls }?.let { entry -> { onRemove(entry) } },
                        )
                    }
                }
            }
        }
    }
}

/** One row of the repository list: icon, name, url, detail, and the optional arrow/delete. */
@Composable
private fun RepositoryRowItem(
    name: String,
    subtitle: String,
    detail: String,
    failed: Boolean,
    onClick: (() -> Unit)?,
    onRemove: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(
            Icons.Outlined.Extension,
            contentDescription = null,
            tint = if (failed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
            Text(
                subtitle,
                color = if (failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontSize = 12.sp,
                maxLines = 1,
            )
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Remove repository",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One repository's extensions, with the content-type chips and an install button per row. */
@Composable
private fun RepositoryPlugins(
    repository: app.cloudstream.desktop.core.RepositoryState?,
    repositoryUrl: String?,
    query: String,
    installed: List<InstalledArchive>,
    installing: String?,
    repoLoading: Boolean,
    repoError: String?,
    onInstall: (RepositoryPlugin) -> Unit,
    onUninstall: (Path) -> Unit,
) {
    if (repository == null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (repoLoading) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Loading ${repositoryUrl ?: "repository"}…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    repoError ?: "This repository could not be loaded.",
                    color = if (repoError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    var type by remember(repository.name) { mutableStateOf<String?>(null) }

    // The repository publishes raw TvType names; the original groups them under the labels
    // its filter chips use, so Anime/OVA/AnimeMovie share one chip instead of five.
    val types = repository.plugins
        .flatMap { it.tvTypes.orEmpty().map(::typeLabel) }
        .distinct()
        .sorted()

    val filtered = repository.plugins.filter { plugin ->
        val matchesQuery = query.isBlank() ||
            plugin.name.contains(query, ignoreCase = true) ||
            plugin.internalName.contains(query, ignoreCase = true) ||
            plugin.description?.contains(query, ignoreCase = true) == true
        val matchesType = type == null ||
            plugin.tvTypes.orEmpty().any { typeLabel(it) == type }
        matchesQuery && matchesType
    }

    Column(Modifier.fillMaxSize()) {
        if (types.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(types) { entry ->
                    FilterChip(
                        selected = entry == type,
                        onClick = { type = if (type == entry) null else entry },
                        label = { Text(entry, fontSize = 11.sp) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (filtered.isEmpty()) {
            Text(
                if (repoLoading) "Loading extensions…" else "No extensions match this filter.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
            items(filtered, key = { it.internalName }) { plugin ->
                PluginRow(
                    plugin = plugin,
                    installedVersion = installed.installedVersion(plugin),
                    installedFile = installed.installedFile(plugin),
                    installing = installing == plugin.internalName,
                    onInstall = { onInstall(plugin) },
                    onUninstall = { path -> onUninstall(path) },
                )
            }
        }
    }
}

/** A plugin row: icon, name, its meta line, description, and the download/delete button. */
@Composable
private fun PluginRow(
    plugin: RepositoryPlugin,
    installedVersion: Int?,
    installedFile: Path?,
    installing: Boolean,
    onInstall: () -> Unit,
    onUninstall: (Path) -> Unit,
) {
    val downByAuthor = plugin.status == 0

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Icon(
            Icons.Outlined.Extension,
            contentDescription = null,
            tint = if (downByAuthor) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (downByAuthor) "${plugin.name} (disabled by author)" else plugin.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
            )
            Text(
                buildString {
                    plugin.language?.takeIf { it.isNotBlank() }?.let { append(it.uppercase()).append(" · ") }
                    append("v${plugin.version}")
                    if (plugin.tvTypes.orEmpty().contains("NSFW")) append(" · 18+")
                    if (plugin.status != 1) append(" · ${plugin.statusLabel}")
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            plugin.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 3,
                )
            }
        }
        Spacer(Modifier.width(10.dp))

        when {
            installing -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            installedFile != null && installedVersion != null && installedVersion >= plugin.version ->
                // Installed: the original's button becomes delete.
                IconButton(onClick = { onUninstall(installedFile) }) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Remove ${plugin.name}",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }

            installedFile != null -> TextButton(onClick = onInstall) { Text("Update") }
            downByAuthor -> Text(
                "Down",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            else -> IconButton(onClick = onInstall, enabled = true) {
                Icon(
                    Icons.Outlined.Download,
                    contentDescription = "Install ${plugin.name}",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** The original's *Installed plugins* screen, reached from the overflow menu. */
@Composable
private fun InstalledList(
    installed: List<InstalledArchive>,
    extensions: ExtensionLoadResult,
    onUninstall: (Path) -> Unit,
    onToggleEnabled: (Path, Boolean) -> Unit,
) {
    if (installed.isEmpty()) {
        Text(
            "No extensions installed. Open a repository and press the download button.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(installed, key = { it.file.toString() }) { archive ->
            var confirm by remember(archive.file) { mutableStateOf(false) }
            val providers = extensions.loaded.count { it.file == archive.file }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Icon(
                    Icons.Outlined.Extension,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        archive.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                    )
                    Text(
                        buildString {
                            append("v${archive.version}")
                            append(" · ")
                            append(archive.file.fileName)
                            when {
                                providers == 1 -> append(" · 1 provider")
                                providers > 1 -> append(" · $providers providers")
                                !archive.enabled -> append(" · disabled")
                                else -> append(" · not loaded")
                            }
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = archive.enabled,
                    onCheckedChange = { onToggleEnabled(archive.file, it) },
                )
                IconButton(onClick = { confirm = true }) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Remove ${archive.name}",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (confirm) {
                AlertDialog(
                    onDismissRequest = { confirm = false },
                    title = { Text("Remove ${archive.name}?") },
                    text = {
                        Text(
                            "${archive.file.fileName} is deleted from the extensions folder and " +
                                "its providers disappear from the app.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                confirm = false
                                onUninstall(archive.file)
                            },
                        ) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
                )
            }
        }
    }
}

/** Raw repository TvType name → the label the original's filter chips show. */
private fun typeLabel(raw: String): String = when (raw.lowercase()) {
    "movie" -> "Movies"
    "tvseries", "tvshow" -> "TV Series"
    "anime", "animemovie", "ova", "animeothers" -> "Anime"
    "cartoon" -> "Cartoons"
    "asiandrama" -> "Asian Drama"
    "live", "livestream" -> "Livestreams"
    "documentary" -> "Documentaries"
    "torrent" -> "Torrent"
    "nsfw" -> "NSFW"
    "audio", "audiobook", "podcast", "music" -> "Audio"
    else -> "Other"
}

/** The version of the installable archive, whether it is enabled or not. */
internal fun List<InstalledArchive>.installedVersion(plugin: RepositoryPlugin): Int? =
    installedFile(plugin)?.let { file -> firstOrNull { it.file == file }?.version }

/** The archive a repository entry maps to, whatever the local file is called. */
internal fun List<InstalledArchive>.installedFile(plugin: RepositoryPlugin): Path? =
    firstOrNull { archive ->
        archive.file.fileName.toString().substringBeforeLast('.') == plugin.internalName ||
            archive.name == plugin.internalName ||
            archive.name == plugin.name
    }?.file
