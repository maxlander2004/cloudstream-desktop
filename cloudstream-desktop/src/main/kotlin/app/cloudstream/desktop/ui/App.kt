package app.cloudstream.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudstream.desktop.api.Catalog
import app.cloudstream.desktop.api.CatalogItem
import app.cloudstream.desktop.api.ItemType
import app.cloudstream.desktop.api.StreamLink
import app.cloudstream.desktop.core.AppUpdates
import app.cloudstream.desktop.core.AppVersion
import app.cloudstream.desktop.core.DownloadManager
import app.cloudstream.desktop.core.ExtensionHost
import app.cloudstream.desktop.core.ExtensionLoadResult
import app.cloudstream.desktop.core.ExtensionRepository
import app.cloudstream.desktop.core.InstalledArchive
import app.cloudstream.desktop.core.Library
import app.cloudstream.desktop.core.PlaybackSession
import app.cloudstream.desktop.core.Player
import app.cloudstream.desktop.core.SessionEpisode
import app.cloudstream.desktop.core.RepositoryPlugin
import app.cloudstream.desktop.core.RepositorySet
import app.cloudstream.desktop.core.Settings
import app.cloudstream.desktop.core.UpdateCheck
import app.cloudstream.desktop.core.WatchRecord
import app.cloudstream.desktop.core.watchLabel
import app.cloudstream.desktop.core.legacy.CatalogSection
import app.cloudstream.desktop.core.legacy.LegacyCatalog
import app.cloudstream.desktop.core.legacy.ResultItem
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private enum class Screen(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Outlined.Home),
    Search("Search", Icons.Outlined.Search),
    Library("Library", Icons.Outlined.VideoLibrary),
    Downloads("Downloads", Icons.Outlined.Download),
    Settings("Settings", Icons.Outlined.Settings),
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

    var extensions by remember { mutableStateOf(ExtensionLoadResult()) }
    /** Every archive on disk, including disabled ones; drives the Extensions screen list. */
    var installedExtensions by remember { mutableStateOf<List<InstalledArchive>>(emptyList()) }
    var screen by remember { mutableStateOf(Screen.Home) }
    var settingsPage by remember { mutableStateOf(SettingsPage.Root) }
    var darkTheme by remember { mutableStateOf(settings.appTheme != "light") }
    // Upstream opens on the provider that was set as the homepage.
    var sourceIndex by remember { mutableStateOf(settings.homepageProviderIndex.coerceAtLeast(0)) }
    var items by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    // A provider's named home rows, when it has them — drawn as the original's poster rails.
    var sections by remember { mutableStateOf<List<CatalogSection>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var accent by remember { mutableStateOf(accentColor(settings.accentColor)) }
    var failure by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Loading…") }
    // Upstream surfaces these messages as toasts; a snackbar keeps the window free of a
    // permanent status line.
    val snackbar = remember { SnackbarHostState() }

    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searchGroups by remember { mutableStateOf<List<Pair<String, List<CatalogItem>>>>(emptyList()) }

    var repos by remember { mutableStateOf<RepositorySet?>(null) }
    var repoLoading by remember { mutableStateOf(false) }
    var repoError by remember { mutableStateOf<String?>(null) }
    var repoQuery by remember { mutableStateOf("") }
    var installing by remember { mutableStateOf<String?>(null) }
    var fetchedFrom by remember { mutableStateOf<List<String>?>(null) }

    var activeItem by remember { mutableStateOf<CatalogItem?>(null) }
    var resultItem by remember { mutableStateOf<ResultItem?>(null) }
    var loadingResult by remember { mutableStateOf(false) }
    var resultError by remember { mutableStateOf<String?>(null) }
    var streams by remember { mutableStateOf<List<StreamLink>>(emptyList()) }
    var resolving by remember { mutableStateOf(false) }
    var showResultScreen by remember { mutableStateOf(false) }
    /** Episode `data` to preselect when the detail screen opens (Continue watching). */
    var resumeEpisode by remember { mutableStateOf<String?>(null) }
    /** Non-null while the in-app player is open. */
    var playerSession by remember { mutableStateOf<PlaybackSession?>(null) }

    var updateBanner by remember { mutableStateOf<UpdateCheck?>(null) }

    // One source per provider an installed extension registered, exactly like the original's
    // provider list; the app ships no sources of its own.
    val allSources = remember(extensions) {
        extensions.loaded.map {
            SourceEntry(
                name = it.manifest.name ?: it.catalog.name,
                description = it.catalog.description,
                origin = it.file.fileName.toString(),
                catalog = it.catalog,
            )
        }
    }
    val sources = remember(allSources, settings.providerLanguages) {
        if (settings.providerLanguages.isEmpty()) allSources
        else allSources.filter { source ->
            source.catalog.language?.lowercase() in settings.providerLanguages
        }
    }
    val current = sources.getOrNull(sourceIndex.coerceIn(0, sources.lastIndex.coerceAtLeast(0)))

    // Bookmarks and watch history; every Library change bumps the version and re-reads them.
    val libraryVersion by Library.changes.collectAsState()
    val continueWatching = remember(libraryVersion) { Library.history() }

    fun visibleItems(list: List<CatalogItem>): List<CatalogItem> {
        val media = settings.preferredMedia.toSet()
        return list.filter { item ->
            (media.isEmpty() || item.type.name in media || item.type == ItemType.OTHER) &&
                (settings.nsfwEnabled || item.type != ItemType.NSFW)
        }
    }

    /** Reloads providers and the on-disk list together; every install path ends here. */
    suspend fun reloadExtensionsFromDisk(): ExtensionLoadResult {
        val loaded = withContext(Dispatchers.IO) { ExtensionHost.loadAll() }
        extensions = loaded
        installedExtensions = withContext(Dispatchers.IO) { ExtensionHost.installed() }
        return loaded
    }

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
            reloadExtensionsFromDisk()
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
            reloadExtensionsFromDisk()
            sourceIndex = 0
            status = "${extensions.loaded.size} extension(s) loaded"
        }
    }

    /** Removes an extension and its providers, the original's delete action. */
    fun uninstallExtension(file: Path) {
        scope.launch {
            status = "Removing ${file.fileName}…"
            val result = withContext(Dispatchers.IO) { ExtensionHost.uninstall(file) }
            reloadExtensionsFromDisk()
            sourceIndex = 0
            status = result.fold(
                onSuccess = { "Removed ${file.fileName}" },
                onFailure = { "Could not remove ${file.fileName}: ${it.message}" },
            )
        }
    }

    /** Enable/disable without deleting, like the original's per-extension switch. */
    fun setExtensionEnabled(file: Path, enabled: Boolean) {
        scope.launch {
            withContext(Dispatchers.IO) { ExtensionHost.setEnabled(file, enabled) }
            reloadExtensionsFromDisk()
            sourceIndex = 0
            status = if (enabled) "Enabled ${file.fileName}" else "Disabled ${file.fileName}"
        }
    }

    /** Installs an extension from a .cs3/.jar url, the way the app handles a plugin link. */
    fun installExtensionFromUrl(url: String) {
        scope.launch {
            installing = url
            status = "Downloading ${url.substringAfterLast('/')}…"
            val result = withContext(Dispatchers.IO) { ExtensionHost.installFromUrl(url) }
            reloadExtensionsFromDisk()
            sourceIndex = 0
            installing = null
            status = result.fold(
                onSuccess = { "Installed ${it.manifest.name ?: it.file.fileName}" },
                onFailure = { "Install failed: ${it.message}" },
            )
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

    /** Shared install path for manual installs, updates and the auto-update options. */
    suspend fun installPlugin(plugin: RepositoryPlugin): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val file = ExtensionRepository.install(plugin, ExtensionHost.ensureExtensionsDir())
            // Same rule as a manual install: a copy is only kept if it actually loads.
            ExtensionHost.setEnabled(file, true)
            val load = ExtensionHost.loadAll()
            load.loaded.firstOrNull { it.file == file }
                ?: run {
                    runCatching { Files.deleteIfExists(file) }
                    val reason = load.errors
                        .firstOrNull { it.startsWith(file.fileName.toString()) }
                        ?.substringAfter(": ")
                        ?: "does not contain a loadable extension"
                    error(reason)
                }
            // A repository can mark a provider Down; upstream installs it disabled until it
            // comes back, so it stays on disk without joining the provider list.
            if (plugin.status == 0) {
                ExtensionHost.setEnabled(file, false)
                ExtensionHost.loadAll()
            }
        }
        "${plugin.name} v${plugin.version}"
    }

    fun installFromRepository(plugin: RepositoryPlugin) {
        scope.launch {
            installing = plugin.internalName
            status = "Downloading ${plugin.name} v${plugin.version}…"
            val result = installPlugin(plugin)
            reloadExtensionsFromDisk()
            sourceIndex = 0
            installing = null
            status = result.fold(
                onSuccess = { "Installed $it" },
                onFailure = { "Install failed: ${it.message}" },
            )
        }
    }

    /**
     * Updates and Backup → Update Plugins: update everything the repositories offer newer
     * versions of, and when *Automatically download plugins* is on, install what is missing.
     */
    fun updateInstalledPlugins() {
        scope.launch {
            status = "Checking extension updates…"
            val set = runCatching {
                withContext(Dispatchers.IO) { ExtensionRepository.fetchAll(settings.repositoryUrls) }
            }.getOrElse {
                status = "Update check failed: ${it.message}"
                return@launch
            }
            repos = set
            fetchedFrom = settings.repositoryUrls

            var changed = 0
            val plugins = set.repositories.flatMap { it.plugins }
            for (plugin in plugins) {
                val installed = extensions.installedVersion(plugin)
                if (installed != null && plugin.version > installed && installing == null) {
                    installing = plugin.internalName
                    if (installPlugin(plugin).isSuccess) changed++
                    installing = null
                    reloadExtensionsFromDisk()
                }
            }
            if (settings.autoDownloadPlugins) {
                for (plugin in plugins) {
                    if (extensions.installedVersion(plugin) == null) {
                        installing = plugin.internalName
                        if (installPlugin(plugin).isSuccess) changed++
                        installing = null
                        reloadExtensionsFromDisk()
                    }
                }
            }
            sourceIndex = 0
            status = if (changed > 0) {
                "$changed extension(s) updated or installed"
            } else {
                "All extensions are up to date"
            }
        }
    }

    fun loadBrowse() {
        val target = current ?: return
        scope.launch {
            loading = true
            failure = null
            items = emptyList()
            sections = emptyList()

            // Providers expose named home rows; upstream draws each one as a horizontal rail.
            val loaded = runCatching {
                withContext(Dispatchers.IO) {
                    (target.catalog as? LegacyCatalog)?.browseSections()
                }
            }.getOrElse {
                failure = it.message ?: "Could not load ${target.name}"
                status = "Failed to load ${target.name}"
                loading = false
                return@launch
            }

            if (loaded != null) {
                val rows = loaded
                    .map { row -> CatalogSection(row.name, visibleItems(row.items)) }
                    .filter { it.items.isNotEmpty() }
                items = rows.flatMap { it.items }
                // Named rows become rails; a provider with one unnamed row keeps the poster grid.
                sections = rows.takeIf { rows.isNotEmpty() && rows.all { it.name.isNotBlank() } }.orEmpty()
                status = "${items.size} items from ${target.name}"
            } else {
                runCatching { withContext(Dispatchers.IO) { target.catalog.browse() } }
                    .onSuccess {
                        items = visibleItems(it)
                        status = "${items.size} items from ${target.name}"
                    }
                    .onFailure {
                        failure = it.message ?: "Could not load ${target.name}"
                        status = "Failed to load ${target.name}"
                    }
            }
            loading = false
        }
    }

    fun runSearch() {
        if (query.isBlank()) return
        Library.addSearch(query)
        val targets = if (settings.advancedSearch) sources else listOfNotNull(current)
        scope.launch {
            searching = true
            failure = null
            items = emptyList()
            sections = emptyList()
            searchGroups = emptyList()

            if (settings.advancedSearch && targets.size > 1) {
                // Advanced Search: results separated by provider, like the original.
                val results = targets.map { target ->
                    async {
                        target.name to runCatching {
                            withContext(Dispatchers.IO) { target.catalog.search(query) }
                        }.getOrElse { emptyList() }
                    }
                }.awaitAll()
                searchGroups = results.map { (name, list) -> name to visibleItems(list) }
                status = "${searchGroups.sumOf { it.second.size }} result(s) for \"$query\""
            } else {
                val target = targets.firstOrNull()
                if (target == null) {
                    searching = false
                    return@launch
                }
                runCatching { withContext(Dispatchers.IO) { target.catalog.search(query) } }
                    .onSuccess {
                        items = visibleItems(it)
                        status = "${items.size} result(s) for \"$query\""
                    }
                    .onFailure {
                        failure = it.message ?: "Search failed"
                        status = "Search failed"
                    }
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

    /**
     * Opens the result/detail screen for an item when the current source is an upstream
     * extension (LegacyCatalog). Falls back to the simple stream dialog for plain catalogs.
     */
    fun openItem(item: CatalogItem, resumeEpisodeData: String? = null) {
        resumeEpisode = resumeEpisodeData
        val target = current ?: return
        val legacy = target.catalog as? LegacyCatalog
        if (legacy != null) {
            showResultScreen = true
            resultItem = null
            loadingResult = true
            resultError = null
            scope.launch {
                val loaded = withContext(Dispatchers.IO) {
                    runCatching { legacy.loadResult(item.url) }.getOrNull()
                }
                loadingResult = false
                if (loaded != null) {
                    resultItem = loaded
                } else {
                    resultError = "Could not load details for ${item.title}"
                    status = resultError ?: "Could not load details"
                    showResultScreen = false
                }
            }
        } else {
            // Native catalog: reuse the existing simple stream dialog.
            openStreams(item)
        }
    }

    /** Continue watching: reopen the item on the episode the history entry points at. */
    fun resumeItem(record: WatchRecord) {
        openItem(
            CatalogItem(
                title = record.title,
                url = record.itemUrl,
                subtitle = record.episodeLabel.takeIf { it.isNotBlank() },
                posterUrl = record.posterUrl,
                type = ItemType.entries.firstOrNull { it.name == record.type } ?: ItemType.OTHER,
            ),
            record.episodeData,
        )
    }

    /** In-app playback when the embedded engine is available, otherwise the external player. */
    fun startPlayback(session: PlaybackSession?, stream: StreamLink) {
        if (session != null && settings.inAppPlayer && VideoEngine.available) {
            playerSession = session
            status = "Playing ${session.title} in the app"
        } else {
            status = Player.open(stream, settings.playerCommand)
        }
    }

    LaunchedEffect(status) { snackbar.showSnackbar(status) }

    LaunchedEffect(Unit) {
        reloadExtensionsFromDisk()
        status = "${extensions.loaded.size} extension(s) loaded"
        refreshRepository()

        // Updates and Backup → startup behaviour.
        if (settings.autoPluginUpdates || settings.autoDownloadPlugins) updateInstalledPlugins()
        if (settings.showAppUpdates) {
            updateBanner = runCatching { AppUpdates.check(settings.installPrerelease) }
                .getOrNull()?.takeIf { it.newer }
            if (updateBanner != null) status = "Update ${updateBanner!!.version} available"
        }
    }

    LaunchedEffect(sourceIndex, extensions) {
        if (screen == Screen.Home) loadBrowse()
    }

    // Re-fetch when the configured repositories changed, e.g. after adding one.
    LaunchedEffect(screen, settingsPage) {
        if (screen == Screen.Settings && settingsPage == SettingsPage.Extensions &&
            fetchedFrom != settings.repositoryUrls
        ) {
            refreshRepository()
        }
    }

    CloudStreamTheme(dark = darkTheme, accent = accent) {
        // The detail screen takes over the whole window, like the original's result page.
        Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // Upstream's rail: icon-only items on primaryGrayBackground, accent when selected.
            NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                Spacer(Modifier.height(16.dp))
                Screen.entries.forEach { entry ->
                    NavigationRailItem(
                        selected = screen == entry,
                        onClick = {
                            screen = entry
                            if (entry == Screen.Home) loadBrowse()
                        },
                        icon = {
                            Icon(
                                entry.icon,
                                contentDescription = entry.label,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            unselectedIconColor = MaterialTheme.colorScheme.onBackground,
                        ),
                    )
                }
            }

            Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant))

            Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp)) {
                when (screen) {
                    Screen.Home -> {
                        updateBanner?.let { info ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable {
                                        runCatching { Desktop.getDesktop().browse(URI(info.url)) }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    "Update ${info.version} available — click to open the download page",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                        }

                        HomeScreen(
                            sources = sources,
                            currentSourceIndex = sourceIndex,
                            onSelectSource = { index ->
                                sourceIndex = index
                                loadBrowse()
                            },
                            onSelectHomepage = { index ->
                                settings.homepageProviderIndex = index
                                sourceIndex = index
                                loadBrowse() },
                            items = items,
                            sections = sections,
                            continueWatching = continueWatching,
                            onResume = ::resumeItem,
                            loading = loading,
                            failure = failure,
                            onRetry = ::loadBrowse,
                            onOpen = { openItem(it) },
                            onAddExtension = {
                                screen = Screen.Settings
                                settingsPage = SettingsPage.Extensions
                            },
                            posterSize = settings.posterSize,
                            titleUnder = settings.posterTitleUnder,
                        )
                    }

                    Screen.Search -> SearchScreen(
                        query = query,
                        onQueryChange = { query = it },
                        onSubmit = ::runSearch,
                        searching = searching,
                        items = items,
                        searchGroups = searchGroups,
                        advanced = settings.advancedSearch,
                        failure = failure,
                        searches = Library.searches(),
                        onRemoveSearch = { Library.removeSearch(it) },
                        onClearSearches = { Library.clearSearches() },
                        preferredMedia = settings.preferredMedia,
                        onToggleMedia = { media ->
                            val selected = settings.preferredMedia
                            settings.preferredMedia = when {
                                selected.isEmpty() -> MediaTypeChips.map { it.first } - media
                                media in selected -> selected - media
                                else -> selected + media
                            }
                            if (query.isNotBlank()) runSearch()
                        },
                        onOpen = { openItem(it) },
                        posterSize = settings.posterSize,
                        titleUnder = settings.posterTitleUnder,
                    )

                    Screen.Library -> LibraryScreen(
                        settings = settings,
                        onOpen = { openItem(it) },
                        onResume = ::resumeItem,
                        onStatus = { status = it },
                    )

                    Screen.Downloads -> DownloadsScreen(
                        settings = settings,
                        onStatus = { status = it },
                        onPlayFile = { title, location ->
                            val link = StreamLink(title, location)
                            startPlayback(
                                PlaybackSession(
                                    itemUrl = location,
                                    title = title,
                                    apiName = "Download",
                                    episodes = listOf(
                                        SessionEpisode(
                                            label = title,
                                            data = location,
                                            season = null,
                                            episode = null,
                                            streams = listOf(link),
                                        ),
                                    ),
                                    directDataUrl = location,
                                ),
                                link,
                            )
                        },
                    )

                    Screen.Settings -> when (settingsPage) {
                        SettingsPage.Root -> SettingsRootScreen(onOpen = { settingsPage = it })

                        SettingsPage.General -> GeneralSettings(
                            settings = settings,
                            onBack = { settingsPage = SettingsPage.Root },
                            onStatus = { status = it },
                        )

                        SettingsPage.Player -> PlayerSettings(
                            settings = settings,
                            onBack = { settingsPage = SettingsPage.Root },
                        )

                        SettingsPage.Providers -> ProvidersSettings(
                            settings = settings,
                            languages = allSources.mapNotNull { it.catalog.language?.lowercase() }.distinct(),
                            testTargets = allSources.map { it.catalog },
                            onBack = { settingsPage = SettingsPage.Root },
                        )

                        SettingsPage.Layout -> LayoutSettings(
                            settings = settings,
                            dark = darkTheme,
                            onThemeChange = { darkTheme = it },
                            onBack = { settingsPage = SettingsPage.Root },
                        )

                        SettingsPage.Ui -> UiSettings(
                            settings = settings,
                            dark = darkTheme,
                            onThemeChange = { darkTheme = it },
                            onAccentChange = { hex ->
                                settings.accentColor = hex
                                accent = accentColor(hex)
                            },
                            onBack = { settingsPage = SettingsPage.Root },
                            onStatus = { status = it },
                        )

                        SettingsPage.UpdatesBackup -> UpdatesBackupSettings(
                            settings = settings,
                            onBack = { settingsPage = SettingsPage.Root },
                            onStatus = { status = it },
                            onRefreshExtensions = ::reloadExtensions,
                            onUpdatePlugins = ::updateInstalledPlugins,
                        )

                        SettingsPage.Extensions -> ExtensionsScreen(
                            settings = settings,
                            extensions = extensions,
                            installed = installedExtensions,
                            repos = repos,
                            repoLoading = repoLoading,
                            repoError = repoError,
                            repoQuery = repoQuery,
                            onQueryChange = { repoQuery = it },
                            installing = installing,
                            onReload = ::reloadExtensions,
                            onInstallFile = ::installExtension,
                            onInstallUrl = ::installExtensionFromUrl,
                            onRefreshRepository = ::refreshRepository,
                            onUpdateAll = ::updateInstalledPlugins,
                            onInstallRepository = ::installFromRepository,
                            onUninstall = ::uninstallExtension,
                            onToggleEnabled = ::setExtensionEnabled,
                            onStatus = { status = it },
                        )

                        SettingsPage.Accounts -> AccountsSettings(
                            settings = settings,
                            onBack = { settingsPage = SettingsPage.Root },
                            onStatus = { status = it },
                        )
                    }
                }

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
                        LazyColumn(Modifier.height(320.dp)) {
                            items(streams) { stream ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = {
                                            status = Player.open(stream, settings.playerCommand)
                                            activeItem = null
                                        },
                                    ) {
                                        Text(stream.title, modifier = Modifier.padding(end = 8.dp))
                                    }
                                    TextButton(
                                        onClick = {
                                            DownloadManager.add(
                                                settings = settings,
                                                title = "${item.title} · ${stream.title}",
                                                url = stream.url,
                                                headers = stream.headers,
                                            )
                                            status = "Queued ${item.title}"
                                            activeItem = null
                                            screen = Screen.Downloads
                                        },
                                    ) { Text("Download") }
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

        if (showResultScreen && resultItem != null && !loadingResult) {
            ResultScreen(
                item = resultItem!!,
                catalog = current?.catalog ?: return@CloudStreamTheme,
                posterSize = settings.posterSize,
                titleUnder = settings.posterTitleUnder,
                settings = settings,
                resumeEpisodeData = resumeEpisode,
                onDismiss = { showResultScreen = false },
                onOpenRecommendation = { related -> openItem(related) },
                onSearchTag = { tag ->
                    query = tag
                    showResultScreen = false
                    screen = Screen.Search
                    runSearch()
                },
                onStatus = { status = it },
                onPlay = { stream, session -> startPlayback(session, stream) },
                onDownload = { stream ->
                    DownloadManager.add(
                        settings = settings,
                        title = "${resultItem!!.title} · ${stream.title}",
                        url = stream.url,
                        headers = stream.headers,
                        subtitles = stream.subtitles,
                    )
                    status = "Queued ${resultItem!!.title}"
                    showResultScreen = false
                    screen = Screen.Downloads
                },
            )
        }

        // The player covers the whole window, like the original's player activity.
        playerSession?.let { session ->
            PlayerScreen(
                session = session,
                settings = settings,
                onClose = { playerSession = null },
                onStatus = { status = it },
            )
        }

        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
        }
    }
}

@Composable
private fun HomeScreen(
    sources: List<SourceEntry>,
    currentSourceIndex: Int,
    onSelectSource: (Int) -> Unit,
    onSelectHomepage: (Int) -> Unit,
    items: List<CatalogItem>,
    sections: List<CatalogSection>,
    continueWatching: List<WatchRecord>,
    onResume: (WatchRecord) -> Unit,
    loading: Boolean,
    failure: String?,
    onRetry: () -> Unit,
    onOpen: (CatalogItem) -> Unit,
    onAddExtension: () -> Unit,
    posterSize: Int,
    titleUnder: Boolean,
) {
    // A fresh install has no providers: the original ships no built-in source either, so the
    // home screen points at Extensions instead of drawing an empty grid.
    if (sources.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "No providers installed",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "CloudStream has no built-in sources. Add an extension — the same .cs3\n" +
                        "archives the Android app uses — to start browsing.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = onAddExtension) { Text("Open Extensions") }
            }
        }
        return
    }

    var showSources by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
    // The reload button at the top right, where `home_preview_reload_provider` sits.
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onRetry) {
            Icon(
                Icons.Outlined.Refresh,
                contentDescription = "Reload provider",
                modifier = Modifier.size(20.dp),
            )
        }
    }
    Spacer(Modifier.height(6.dp))

    // Upstream's Continue watching rail, built from the local watch history.
    if (continueWatching.isNotEmpty()) {
        RailHeader("Continue watching")
        Spacer(Modifier.height(6.dp))
        PosterRailRow(
            items = continueWatching.map { record ->
                CatalogItem(
                    title = record.title,
                    url = record.itemUrl,
                    subtitle = watchLabel(record).takeIf { it.isNotBlank() },
                    posterUrl = record.posterUrl,
                    type = ItemType.entries.firstOrNull { it.name == record.type } ?: ItemType.OTHER,
                )
            },
            titleUnder = false,
            onOpen = { selected ->
                continueWatching.firstOrNull { it.itemUrl == selected.url }?.let(onResume)
                    ?: onOpen(selected)
            },
        )
        Spacer(Modifier.height(12.dp))
    }

    when {
        loading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text("Loading content…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        failure != null -> Column {
            Text(failure, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(10.dp))
            Button(onClick = onRetry) { Text("Retry") }
        }

        sections.isNotEmpty() -> PosterRails(
            sections = sections,
            titleUnder = titleUnder,
            onOpen = onOpen,
        )

        else -> PosterGrid(
            items = items,
            posterSize = posterSize,
            titleUnder = titleUnder,
            onOpen = onOpen,
        )
    }
    }

    // Upstream's `home_api_fab`: the Home source button that switches which provider the
    // home screen shows, floating bottom-end exactly as the Android app draws it.
    Box(Modifier.align(Alignment.BottomEnd).padding(18.dp)) {
        ExtendedFloatingActionButton(
            onClick = { showSources = true },
            icon = { Icon(Icons.Outlined.FilterList, contentDescription = null) },
            text = { Text("Home source") },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
        )
        DropdownMenu(
            expanded = showSources,
            onDismissRequest = { showSources = false },
        ) {
            sources.forEachIndexed { index, source ->
                DropdownMenuItem(
                    text = { Text(source.name, fontSize = 13.sp) },
                    leadingIcon = {
                        if (index == currentSourceIndex) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                    onClick = {
                        showSources = false
                        onSelectSource(index)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Use as homepage", fontSize = 13.sp) },
                onClick = {
                    showSources = false
                    onSelectHomepage(currentSourceIndex)
                },
            )
        }
    }
    }
}



@Composable
private fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    searching: Boolean,
    items: List<CatalogItem>,
    searchGroups: List<Pair<String, List<CatalogItem>>>,
    advanced: Boolean,
    failure: String?,
    searches: List<String>,
    onRemoveSearch: (String) -> Unit,
    onClearSearches: () -> Unit,
    preferredMedia: List<String>,
    onToggleMedia: (String) -> Unit,
    onOpen: (CatalogItem) -> Unit,
    posterSize: Int,
    titleUnder: Boolean,
) {
    Text("Search", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
    Spacer(Modifier.height(14.dp))

    // Upstream's search bar: a rounded field with a leading icon; Enter runs the search.
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        modifier = Modifier.width(520.dp),
    )

    Spacer(Modifier.height(10.dp))

    // Upstream's media-type chips also sit on the search screen.
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MediaTypeChips.forEach { (media, label) ->
            FilterChip(
                selected = preferredMedia.isEmpty() || media in preferredMedia,
                onClick = { onToggleMedia(media) },
                label = { Text(label, fontSize = 11.sp) },
            )
        }
    }

    // Upstream shows the recent searches while the field is empty.
    if (query.isBlank() && searches.isNotEmpty() && items.isEmpty() && searchGroups.isEmpty() && !searching) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Recent searches",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClearSearches) { Text("Clear history", fontSize = 12.sp) }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            searches.forEach { entry ->
                FilterChip(
                    selected = false,
                    onClick = {
                        onQueryChange(entry)
                        onSubmit()
                    },
                    label = { Text(entry, fontSize = 12.sp) },
                    trailingIcon = {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove $entry",
                            modifier = Modifier.size(16.dp).clickable { onRemoveSearch(entry) },
                        )
                    },
                )
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    when {
        searching -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text("Searching…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        failure != null -> Text(failure, color = MaterialTheme.colorScheme.error)

        advanced && searchGroups.isNotEmpty() -> GroupedResults(
            groups = searchGroups,
            posterSize = posterSize,
            titleUnder = titleUnder,
            onOpen = onOpen,
        )

        items.isEmpty() -> Text("No results yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        else -> PosterGrid(
            items = items,
            posterSize = posterSize,
            titleUnder = titleUnder,
            onOpen = onOpen,
        )
    }
}





/** Matches a repository entry to its installed extension, whatever the local file is called. */
internal fun ExtensionLoadResult.installedVersion(plugin: RepositoryPlugin): Int? {
    val match = loaded.firstOrNull { extension ->
        extension.file.fileName.toString().substringBeforeLast('.') == plugin.internalName ||
            extension.manifest.name == plugin.internalName ||
            extension.manifest.name == plugin.name
    } ?: return null
    // A version we do not know counts as older than anything the repository offers.
    return match.manifest.version ?: 0
}
