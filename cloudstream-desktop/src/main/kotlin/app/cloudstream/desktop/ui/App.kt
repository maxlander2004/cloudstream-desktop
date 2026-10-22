package app.cloudstream.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudstream.desktop.api.Catalog
import app.cloudstream.desktop.api.CatalogItem
import app.cloudstream.desktop.api.StreamLink
import app.cloudstream.desktop.core.ExtensionHost
import app.cloudstream.desktop.core.ExtensionLoadResult
import app.cloudstream.desktop.core.ExtensionRepository
import app.cloudstream.desktop.core.RepositoryPlugin
import app.cloudstream.desktop.core.RepositorySet
import app.cloudstream.desktop.core.IptvSource
import app.cloudstream.desktop.core.Player
import app.cloudstream.desktop.core.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Files
import java.nio.file.Paths

private enum class Screen(val label: String) {
    Home("Home"),
    Search("Search"),
    Extensions("Extensions"),
    Settings("Settings"),
}

private data class SourceEntry(
    val name: String,
    val description: String,
    val origin: String,
    val catalog: Catalog,
)

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val settings = remember { Settings() }
    val builtin = remember { IptvSource() }

    var extensions by remember { mutableStateOf(ExtensionLoadResult()) }
    var screen by remember { mutableStateOf(Screen.Home) }
    var sourceIndex by remember { mutableStateOf(0) }
    var items by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Loading…") }

    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }

    var repos by remember { mutableStateOf<RepositorySet?>(null) }
    var repoLoading by remember { mutableStateOf(false) }
    var repoError by remember { mutableStateOf<String?>(null) }
    var repoQuery by remember { mutableStateOf("") }
    var installing by remember { mutableStateOf<String?>(null) }
    var fetchedFrom by remember { mutableStateOf<List<String>?>(null) }

    var activeItem by remember { mutableStateOf<CatalogItem?>(null) }
    var streams by remember { mutableStateOf<List<StreamLink>>(emptyList()) }
    var resolving by remember { mutableStateOf(false) }

    val sources = remember(extensions) {
        listOf(SourceEntry(builtin.name, builtin.description, "built-in", builtin)) +
            extensions.loaded.map {
                SourceEntry(
                    name = it.manifest.name ?: it.catalog.name,
                    description = it.catalog.description,
                    origin = it.file.fileName.toString(),
                    catalog = it.catalog,
                )
            }
    }
    val current = sources.getOrNull(sourceIndex.coerceIn(0, sources.lastIndex.coerceAtLeast(0)))

    fun installExtension() {
        val dialog = FileDialog(
            null as Frame?,
            "Select an extension (.cs3, .cs, .apk or .jar)",
            FileDialog.LOAD,
        )
        dialog.isVisible = true
        val file = dialog.file
        val directory = dialog.directory
        dialog.dispose()
        if (file == null || directory == null) return

        val source = Paths.get(directory, file)
        scope.launch {
            status = "Installing ${source.fileName}…"
            val result = withContext(Dispatchers.IO) { ExtensionHost.install(source) }
            extensions = withContext(Dispatchers.IO) { ExtensionHost.loadAll() }
            sourceIndex = 0
            status = result.fold(
                onSuccess = { "Installed ${it.manifest.name ?: it.catalog.name}" },
                onFailure = { "Install failed: ${it.message}" },
            )
        }
    }

    fun reloadExtensions() {
        scope.launch {
            status = "Loading extensions…"
            extensions = withContext(Dispatchers.IO) { ExtensionHost.loadAll() }
            sourceIndex = 0
            status = "${extensions.loaded.size} extension(s) loaded"
        }
    }

    fun refreshRepository() {
        scope.launch {
            repoLoading = true
            repoError = null
            status = "Loading repositories…"
            runCatching {
                withContext(Dispatchers.IO) { ExtensionRepository.fetchAll(settings.repositoryUrls) }
            }.onSuccess {
                repos = it
                fetchedFrom = settings.repositoryUrls
                status = "${it.pluginCount} extension(s) from ${it.repositories.size} repositories" +
                    if (it.failures.isEmpty()) "" else " (${it.failures.size} unreachable)"
            }.onFailure {
                repoError = it.message ?: "Could not reach the repositories"
                status = "Repository load failed"
            }
            repoLoading = false
        }
    }

    fun installFromRepository(plugin: RepositoryPlugin) {
        scope.launch {
            installing = plugin.internalName
            status = "Downloading ${plugin.name} v${plugin.version}…"
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val file = ExtensionRepository.install(plugin, ExtensionHost.ensureExtensionsDir())
                    // Same rule as a manual install: a copy is only kept if it actually loads,
                    // so a stale extension targeting an older app version cannot pile up.
                    val load = ExtensionHost.loadAll()
                    load.loaded.firstOrNull { it.file == file }
                        ?: run {
                            Files.deleteIfExists(file)
                            val reason = load.errors
                                .firstOrNull { it.startsWith(file.fileName.toString()) }
                                ?.substringAfter(": ")
                                ?: "does not contain a loadable extension"
                            error(reason)
                        }
                }
            }
            extensions = withContext(Dispatchers.IO) { ExtensionHost.loadAll() }
            sourceIndex = 0
            installing = null
            status = result.fold(
                onSuccess = { "Installed ${plugin.name} v${plugin.version}" },
                onFailure = { "Install failed: ${it.message}" },
            )
        }
    }

    fun loadBrowse() {
        val target = current ?: return
        scope.launch {
            loading = true
            failure = null
            items = emptyList()
            runCatching { withContext(Dispatchers.IO) { target.catalog.browse() } }
                .onSuccess {
                    items = it
                    status = "${it.size} items from ${target.name}"
                }
                .onFailure {
                    failure = it.message ?: "Could not load ${target.name}"
                    status = "Failed to load ${target.name}"
                }
            loading = false
        }
    }

    fun runSearch() {
        val target = current ?: return
        if (query.isBlank()) return
        scope.launch {
            searching = true
            failure = null
            items = emptyList()
            runCatching { withContext(Dispatchers.IO) { target.catalog.search(query) } }
                .onSuccess {
                    items = it
                    status = "${it.size} result(s) for \"$query\""
                }
                .onFailure {
                    failure = it.message ?: "Search failed"
                    status = "Search failed"
                }
            searching = false
        }
    }

    fun openStreams(item: CatalogItem) {
        val target = current ?: return
        scope.launch {
            resolving = true
            activeItem = item
            streams = emptyList()
            runCatching { withContext(Dispatchers.IO) { target.catalog.streams(item) } }
                .onSuccess {
                    streams = it
                    if (it.isEmpty()) status = "No streams returned for ${item.title}"
                }
                .onFailure {
                    failure = it.message ?: "Could not resolve streams"
                    activeItem = null
                }
            resolving = false
        }
    }

    LaunchedEffect(Unit) {
        extensions = withContext(Dispatchers.IO) { ExtensionHost.loadAll() }
        status = "${extensions.loaded.size} extension(s) loaded"
        refreshRepository()
    }

    LaunchedEffect(sourceIndex, extensions) {
        if (screen == Screen.Home) loadBrowse()
    }

    // Re-fetch when the configured repositories changed, e.g. after editing them in Settings.
    LaunchedEffect(screen) {
        if (screen == Screen.Extensions && fetchedFrom != settings.repositoryUrls) refreshRepository()
    }

    Row(Modifier.fillMaxSize().background(CsBackground)) {
        NavigationRail(containerColor = CsSurface) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "CS",
                color = CsPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            Spacer(Modifier.height(16.dp))
            Screen.entries.forEach { entry ->
                NavigationRailItem(
                    selected = screen == entry,
                    onClick = {
                        screen = entry
                        if (entry == Screen.Home) loadBrowse()
                    },
                    icon = {
                        Text(
                            text = entry.label.take(1),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                    },
                    label = { Text(entry.label, fontSize = 11.sp) },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = androidx.compose.ui.graphics.Color.White,
                        selectedTextColor = CsOnBackground,
                        indicatorColor = CsPrimary,
                        unselectedIconColor = CsSecondaryText,
                        unselectedTextColor = CsSecondaryText,
                    ),
                )
            }
        }

        Box(Modifier.width(1.dp).fillMaxHeight().background(CsSurfaceVariant))

        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp)) {
            when (screen) {
                Screen.Home -> HomeScreen(
                    sources = sources,
                    sourceIndex = sourceIndex.coerceIn(0, sources.lastIndex.coerceAtLeast(0)),
                    onSelectSource = {
                        sourceIndex = it
                        loadBrowse()
                    },
                    items = items,
                    loading = loading,
                    failure = failure,
                    onRetry = ::loadBrowse,
                    onOpen = ::openStreams,
                )

                Screen.Search -> SearchScreen(
                    query = query,
                    onQueryChange = { query = it },
                    onSubmit = ::runSearch,
                    searching = searching,
                    items = items,
                    failure = failure,
                    onOpen = ::openStreams,
                )

                Screen.Extensions -> ExtensionsScreen(
                    extensions = extensions,
                    repos = repos,
                    repoLoading = repoLoading,
                    repoError = repoError,
                    repoQuery = repoQuery,
                    onQueryChange = { repoQuery = it },
                    installing = installing,
                    onReload = ::reloadExtensions,
                    onInstall = ::installExtension,
                    onRefreshRepository = ::refreshRepository,
                    onInstallRepository = ::installFromRepository,
                )

                Screen.Settings -> SettingsScreen(settings = settings, status = status)
            }

            Spacer(Modifier.height(8.dp))
            Text(status, color = CsSecondaryText, fontSize = 11.sp)
        }
    }

    val item = activeItem
    if (item != null) {
        AlertDialog(
            onDismissRequest = { activeItem = null },
            title = { Text(item.title) },
            text = {
                if (resolving) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Resolving streams…")
                    }
                } else {
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(streams) { stream ->
                            TextButton(
                                onClick = {
                                    status = Player.open(stream, settings.playerCommand)
                                    activeItem = null
                                },
                            ) {
                                Text(stream.title)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeItem = null }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun HomeScreen(
    sources: List<SourceEntry>,
    sourceIndex: Int,
    onSelectSource: (Int) -> Unit,
    items: List<CatalogItem>,
    loading: Boolean,
    failure: String?,
    onRetry: () -> Unit,
    onOpen: (CatalogItem) -> Unit,
) {
    Text("CloudStream", style = MaterialTheme.typography.headlineMedium, color = CsOnBackground)
    Text(
        "Native desktop build · ${sources.size} source(s)",
        color = CsSecondaryText,
        fontSize = 12.sp,
    )
    Spacer(Modifier.height(14.dp))

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sources.forEachIndexed { index, source ->
            FilterChip(
                selected = index == sourceIndex,
                onClick = { onSelectSource(index) },
                label = { Text(source.name) },
            )
        }
    }

    Spacer(Modifier.height(14.dp))

    val description = sources.getOrNull(sourceIndex)?.description.orEmpty()
    if (description.isNotEmpty()) {
        Text(description, color = CsSecondaryText, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
    }

    when {
        loading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text("Loading content…", color = CsSecondaryText)
        }

        failure != null -> Column {
            Text(failure, color = CsAccent)
            Spacer(Modifier.height(10.dp))
            Button(onClick = onRetry) { Text("Retry") }
        }

        else -> ItemList(items = items, onOpen = onOpen)
    }
}

@Composable
private fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    searching: Boolean,
    items: List<CatalogItem>,
    failure: String?,
    onOpen: (CatalogItem) -> Unit,
) {
    Text("Search", style = MaterialTheme.typography.headlineMedium, color = CsOnBackground)
    Spacer(Modifier.height(14.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Query") },
            singleLine = true,
            modifier = Modifier.width(420.dp),
        )
        Spacer(Modifier.width(12.dp))
        Button(onClick = onSubmit, enabled = !searching) { Text("Search") }
    }

    Spacer(Modifier.height(16.dp))

    when {
        searching -> CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
        failure != null -> Text(failure, color = CsAccent)
        items.isEmpty() -> Text("No results yet.", color = CsSecondaryText)
        else -> ItemList(items = items, onOpen = onOpen)
    }
}

@Composable
private fun ExtensionsScreen(
    extensions: ExtensionLoadResult,
    repos: RepositorySet?,
    repoLoading: Boolean,
    repoError: String?,
    repoQuery: String,
    onQueryChange: (String) -> Unit,
    installing: String?,
    onReload: () -> Unit,
    onInstall: () -> Unit,
    onRefreshRepository: () -> Unit,
    onInstallRepository: (RepositoryPlugin) -> Unit,
) {
    Text("Extensions", style = MaterialTheme.typography.headlineMedium, color = CsOnBackground)
    Spacer(Modifier.height(8.dp))
    Text(
        "The CloudStream extension repositories: install any provider below, the same .cs3 " +
            "archives the Android app uses, verified against the repository's published hashes. " +
            "Their Dalvik bytecode is translated and linked against a JVM build of CloudStream's " +
            "own library. Desktop .jar extensions implementing Catalog work too.",
        color = CsSecondaryText,
        fontSize = 12.sp,
    )
    Spacer(Modifier.height(14.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = onInstall) { Text("Install file…") }
        Button(onClick = onReload) { Text("Reload") }
        Button(
            onClick = {
                runCatching { Desktop.getDesktop().open(ExtensionHost.ensureExtensionsDir().toFile()) }
            },
        ) { Text("Open extensions folder") }
    }

    Spacer(Modifier.height(18.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Available extensions", color = CsOnBackground, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(10.dp))
        Text(
            when {
                repos == null -> "CloudStream repository"
                else -> "${repos.repositories.size} repositories · ${repos.pluginCount} extensions"
            },
            color = CsSecondaryText,
            fontSize = 11.sp,
        )
        Spacer(Modifier.width(10.dp))
        if (repoLoading) {
            CircularProgressIndicator(Modifier.height(14.dp).width(14.dp), strokeWidth = 2.dp)
        } else {
            Button(onClick = onRefreshRepository) { Text("Refresh") }
        }
    }
    Spacer(Modifier.height(8.dp))

    OutlinedTextField(
        value = repoQuery,
        onValueChange = onQueryChange,
        label = { Text("Filter extensions") },
        singleLine = true,
        modifier = Modifier.width(420.dp),
    )
    Spacer(Modifier.height(8.dp))

    when {
        repoError != null -> Text(repoError, color = CsAccent, fontSize = 12.sp)
        repos == null && repoLoading -> Text("Loading repositories…", color = CsSecondaryText)
        repos != null -> {
            val query = repoQuery.trim().lowercase()
            val filtered = repos.repositories.mapNotNull { state ->
                state.copy(
                    plugins = state.plugins.filter { plugin ->
                        query.isEmpty() ||
                            plugin.name.lowercase().contains(query) ||
                            plugin.internalName.lowercase().contains(query) ||
                            plugin.description?.lowercase()?.contains(query) == true
                    },
                ).takeIf { it.plugins.isNotEmpty() }
            }

            if (filtered.sumOf { it.plugins.size } == 0) {
                Text("No extension matches \"$repoQuery\".", color = CsSecondaryText)
            } else {
                LazyColumn(
                    Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    filtered.forEach { state ->
                        item(key = "repo:${state.name}") {
                            Text(
                                state.name,
                                color = CsSecondaryText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        items(state.plugins, key = { "${state.name}/${it.internalName}" }) { plugin ->
                            RepositoryRow(
                                plugin = plugin,
                                repoName = state.name,
                                installedVersion = extensions.installedVersion(plugin),
                                installing = installing == plugin.internalName,
                                onInstall = { onInstallRepository(plugin) },
                            )
                        }
                    }
                }
            }

            if (repos.failures.isNotEmpty()) {
                Text(
                    "${repos.failures.size} repositories unreachable — Refresh to try again",
                    color = CsSecondaryText,
                    fontSize = 11.sp,
                )
            }
        }
    }

    Spacer(Modifier.height(18.dp))
    Text("Installed", color = CsOnBackground, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(8.dp))

    if (extensions.loaded.isEmpty()) {
        Text("No extensions installed.", color = CsSecondaryText)
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(extensions.loaded) { extension ->
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CsSurface)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Column {
                        Text(
                            extension.manifest.name ?: extension.catalog.name,
                            color = CsOnBackground,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            buildString {
                                append("v${extension.manifest.version ?: 0}")
                                append(" · ")
                                append(extension.file.fileName)
                                if (extension.legacy) append(" · CloudStream")
                            },
                            color = CsSecondaryText,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }

    if (extensions.errors.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Text("Failed to load:", color = CsAccent, fontWeight = FontWeight.Medium)
        extensions.errors.forEach { Text(it, color = CsAccent, fontSize = 11.sp) }
    }
}

@Composable
private fun RepositoryRow(
    plugin: RepositoryPlugin,
    repoName: String?,
    installedVersion: Int?,
    installing: Boolean,
    onInstall: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CsSurface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(plugin.name, color = CsOnBackground, fontWeight = FontWeight.Medium)
                Text(
                    buildString {
                        append("v${plugin.version}")
                        append(" · ")
                        append(plugin.statusLabel)
                        if (plugin.authors.isNotEmpty()) {
                            append(" · by ")
                            append(plugin.authors.joinToString(", "))
                        }
                        if (!repoName.isNullOrBlank()) append(" · $repoName")
                        if (installedVersion != null) append(" · installed v$installedVersion")
                    },
                    color = CsSecondaryText,
                    fontSize = 11.sp,
                )
                plugin.description?.let {
                    Text(it, color = CsSecondaryText, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.width(10.dp))
            val label = when {
                installing -> "…"
                installedVersion == null -> "Install"
                plugin.version > installedVersion -> "Update"
                else -> "Installed"
            }
            Button(onClick = onInstall, enabled = !installing && label != "Installed") {
                Text(label)
            }
        }
    }
}

/** Matches a repository entry to its installed extension, whatever the local file is called. */
private fun ExtensionLoadResult.installedVersion(plugin: RepositoryPlugin): Int? {
    val match = loaded.firstOrNull { extension ->
        extension.file.fileName.toString().substringBeforeLast('.') == plugin.internalName ||
            extension.manifest.name == plugin.internalName ||
            extension.manifest.name == plugin.name
    } ?: return null
    // A version we do not know counts as older than anything the repository offers.
    return match.manifest.version ?: 0
}

@Composable
private fun SettingsScreen(settings: Settings, status: String) {
    Text("Settings", style = MaterialTheme.typography.headlineMedium, color = CsOnBackground)
    Spacer(Modifier.height(14.dp))

    Text("Player command", color = CsOnBackground, fontWeight = FontWeight.Medium)
    Text(
        "Streams are handed to an external player, e.g. mpv or the full path to vlc.exe. " +
            "Leave empty to open links in your default browser.",
        color = CsSecondaryText,
        fontSize = 12.sp,
    )
    Spacer(Modifier.height(8.dp))

    var command by remember { mutableStateOf(settings.playerCommand) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = command,
            onValueChange = { command = it },
            label = { Text("Command") },
            singleLine = true,
            modifier = Modifier.width(420.dp),
        )
        Spacer(Modifier.width(12.dp))
        Button(onClick = { settings.playerCommand = command }) { Text("Save") }
    }

    Spacer(Modifier.height(18.dp))
    Text("Extension repositories", color = CsOnBackground, fontWeight = FontWeight.Medium)
    Text(
        "The indexes the Extensions screen lists and installs from, one per line. Any " +
            "compatible repo.json works, and shortcodes work too — type megarepo for every " +
            "known community repository, like in the Android app.",
        color = CsSecondaryText,
        fontSize = 12.sp,
    )
    Spacer(Modifier.height(8.dp))

    var repoUrls by remember { mutableStateOf(settings.repositoryUrls.joinToString("\n")) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = repoUrls,
            onValueChange = { repoUrls = it },
            label = { Text("Repository urls (one per line)") },
            modifier = Modifier.width(420.dp).heightIn(min = 72.dp),
        )
        Spacer(Modifier.width(12.dp))
        Button(
            onClick = {
                settings.repositoryUrls = repoUrls.lines().map { it.trim() }.filter { it.isNotEmpty() }
            },
        ) { Text("Save") }
    }

    Spacer(Modifier.height(18.dp))
    Text("Saved to ~/.cloudstream-desktop/settings.properties", color = CsSecondaryText, fontSize = 11.sp)
    Text(status, color = CsSecondaryText, fontSize = 11.sp)
}

@Composable
private fun ItemList(items: List<CatalogItem>, onOpen: (CatalogItem) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(items, key = { it.url + it.title }) { item ->
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CsSurface)
                    .clickable { onOpen(item) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Column {
                    Text(item.title, color = CsOnBackground, fontWeight = FontWeight.Medium)
                    val subtitle = item.subtitle
                    if (!subtitle.isNullOrBlank()) {
                        Text(subtitle, color = CsSecondaryText, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
