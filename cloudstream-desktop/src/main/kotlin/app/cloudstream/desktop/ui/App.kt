package app.cloudstream.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudstream.desktop.api.Catalog
import app.cloudstream.desktop.api.CatalogItem
import app.cloudstream.desktop.api.Episode
import app.cloudstream.desktop.api.EpisodeGroup
import app.cloudstream.desktop.api.HomeSection
import app.cloudstream.desktop.api.ItemType
import app.cloudstream.desktop.api.StreamLink
import app.cloudstream.desktop.core.AppUpdates
import app.cloudstream.desktop.core.AppVersion
import app.cloudstream.desktop.core.DownloadManager
import app.cloudstream.desktop.core.ExtensionHost
import app.cloudstream.desktop.core.ExtensionLoadResult
import app.cloudstream.desktop.core.ExtensionRepository
import app.cloudstream.desktop.core.IptvSource
import app.cloudstream.desktop.core.Player
import app.cloudstream.desktop.core.RepositoryPlugin
import app.cloudstream.desktop.core.RepositorySet
import app.cloudstream.desktop.core.Settings
import app.cloudstream.desktop.core.UpdateCheck
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
import java.nio.file.Paths
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private enum class Screen(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Outlined.Home),
    Search("Search", Icons.Outlined.Search),
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
    val builtin = remember { IptvSource() }

    var extensions by remember { mutableStateOf(ExtensionLoadResult()) }
    var screen by remember { mutableStateOf(Screen.Home) }
    var settingsPage by remember { mutableStateOf(SettingsPage.Root) }
    var darkTheme by remember { mutableStateOf(settings.appTheme != "light") }
    var sourceIndex by remember { mutableStateOf(0) }
    var items by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var homeSections by remember { mutableStateOf<List<HomeSection>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Loading…") }

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
    var activeEpisode by remember { mutableStateOf<Episode?>(null) }
    var streams by remember { mutableStateOf<List<StreamLink>>(emptyList()) }
    var selectedSource by remember { mutableStateOf(0) }
    var resolving by remember { mutableStateOf(false) }

    // The original's result screen: open for a series, closed with the back arrow.
    var detailItem by remember { mutableStateOf<CatalogItem?>(null) }
    var detailGroups by remember { mutableStateOf<List<EpisodeGroup>>(emptyList()) }
    var detailGroup by remember { mutableStateOf(0) }

    var updateBanner by remember { mutableStateOf<UpdateCheck?>(null) }

    // Filtered by the Providers settings: extension languages, preferred media, NSFW.
    val allSources = remember(extensions) {
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
    val sources = remember(allSources, settings.providerLanguages) {
        if (settings.providerLanguages.isEmpty()) allSources
        else allSources.filter { source ->
            source.origin == "built-in" ||
                source.catalog.language?.lowercase() in settings.providerLanguages
        }
    }
    val current = sources.getOrNull(sourceIndex.coerceIn(0, sources.lastIndex.coerceAtLeast(0)))

    fun visibleItems(list: List<CatalogItem>): List<CatalogItem> {
        val media = settings.preferredMedia.toSet()
        return list.filter { item ->
            (media.isEmpty() || item.type.name in media || item.type == ItemType.OTHER) &&
                (settings.nsfwEnabled || item.type != ItemType.NSFW)
        }
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

    /** Shared install path for manual installs, updates and the auto-update options. */
    suspend fun installPlugin(plugin: RepositoryPlugin): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val file = ExtensionRepository.install(plugin, ExtensionHost.ensureExtensionsDir())
            // Same rule as a manual install: a copy is only kept if it actually loads.
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
        }
        "${plugin.name} v${plugin.version}"
    }

    fun installFromRepository(plugin: RepositoryPlugin) {
        scope.launch {
            installing = plugin.internalName
            status = "Downloading ${plugin.name} v${plugin.version}…"
            val result = installPlugin(plugin)
            extensions = withContext(Dispatchers.IO) { ExtensionHost.loadAll() }
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
                    extensions = withContext(Dispatchers.IO) { ExtensionHost.loadAll() }
                }
            }
            if (settings.autoDownloadPlugins) {
                for (plugin in plugins) {
                    if (extensions.installedVersion(plugin) == null) {
                        installing = plugin.internalName
                        if (installPlugin(plugin).isSuccess) changed++
                        installing = null
                        extensions = withContext(Dispatchers.IO) { ExtensionHost.loadAll() }
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
            homeSections = emptyList()
            runCatching { withContext(Dispatchers.IO) { target.catalog.browseSections() } }
                .onSuccess { sections ->
                    homeSections = sections.map { section ->
                        HomeSection(section.name, visibleItems(section.items))
                    }
                    items = homeSections.flatMap { it.items }
                    status = "${items.size} items from ${target.name}"
                }
                .onFailure {
                    failure = it.message ?: "Could not load ${target.name}"
                    status = "Failed to load ${target.name}"
                }
            loading = false
        }
    }

    fun runSearch() {
        if (query.isBlank()) return
        val targets = if (settings.advancedSearch) sources else listOfNotNull(current)
        scope.launch {
            searching = true
            failure = null
            items = emptyList()
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

    fun openStreams(item: CatalogItem, episode: Episode?) {
        val target = current ?: return
        scope.launch {
            resolving = true
            activeItem = item
            activeEpisode = episode
            selectedSource = 0
            streams = emptyList()
            runCatching {
                withContext(Dispatchers.IO) {
                    if (episode == null) target.catalog.streams(item)
                    else target.catalog.streams(item, episode)
                }
            }
                .onSuccess {
                    streams = it
                    if (it.isEmpty()) status = "No streams returned for ${item.title}"
                }
                .onFailure {
                    val message = it.message ?: "Could not resolve streams"
                    failure = message
                    // The detail screen shows no error banner, so the status line carries it.
                    status = message
                    activeItem = null
                }
            resolving = false
        }
    }

    /**
     * Tapping a poster does what the original does: a series opens its result screen with the
     * season picker and the episode list, anything else goes straight to picking a source.
     */
    fun openItem(item: CatalogItem) {
        val target = current ?: return
        scope.launch {
            val groups = runCatching {
                withContext(Dispatchers.IO) { target.catalog.episodeGroups(item) }
            }.getOrElse { emptyList() }
            if (groups.isEmpty()) {
                openStreams(item, null)
            } else {
                detailItem = item
                detailGroups = groups
                detailGroup = 0
                status = "${groups.sumOf { it.episodes.size }} episode(s) for ${item.title}"
            }
        }
    }

    /**
     * The episode row's download button, which upstream's PieFetchButton turns into a queued
     * download: resolve the episode's links and queue the first one the source returns.
     */
    fun downloadEpisode(item: CatalogItem, episode: Episode) {
        val target = current ?: return
        scope.launch {
            val links = runCatching {
                withContext(Dispatchers.IO) { target.catalog.streams(item, episode) }
            }.getOrElse {
                status = "Could not resolve ${episode.label}: ${it.message}"
                return@launch
            }
            val link = links.firstOrNull()
            if (link == null) {
                status = "No streams returned for ${episode.label}"
                return@launch
            }
            val title = "${item.title} · ${episode.label}"
            DownloadManager.add(settings = settings, title = title, url = link.url, headers = link.headers)
            status = "Queued $title"
        }
    }

    LaunchedEffect(Unit) {
        extensions = withContext(Dispatchers.IO) { ExtensionHost.loadAll() }
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

    CloudStreamTheme(dark = darkTheme) {
        Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "CS",
                    color = MaterialTheme.colorScheme.primary,
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
                            androidx.compose.material3.Icon(
                                entry.icon,
                                contentDescription = entry.label,
                                modifier = Modifier.height(20.dp),
                            )
                        },
                        label = { Text(entry.label, fontSize = 11.sp) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = MaterialTheme.colorScheme.onBackground,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }

            Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant))

            Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp)) {
                val detail = detailItem
                if (detail != null) {
                    DetailScreen(
                        modifier = Modifier.weight(1f),
                        item = detail,
                        groups = detailGroups,
                        groupIndex = detailGroup,
                        onGroupIndex = { detailGroup = it },
                        onBack = { detailItem = null },
                        onSearch = {
                            detailItem = null
                            screen = Screen.Search
                        },
                        onPlay = { episode -> openStreams(detail, episode) },
                        onDownload = { episode -> downloadEpisode(detail, episode) },
                    )
                } else when (screen) {
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
                            sourceIndex = sourceIndex.coerceIn(0, sources.lastIndex.coerceAtLeast(0)),
                            onSelectSource = {
                                sourceIndex = it
                                loadBrowse()
                            },
                            sections = homeSections,
                            loading = loading,
                            failure = failure,
                            onRetry = ::loadBrowse,
                            onOpen = ::openItem,
                            posterSize = settings.posterSize,
                            titleUnder = settings.posterTitleUnder,
                            randomButton = settings.randomButton,
                            showClock = settings.showClock,
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
                        onOpen = ::openItem,
                        posterSize = settings.posterSize,
                        titleUnder = settings.posterTitleUnder,
                        nsfwEnabled = settings.nsfwEnabled,
                    )

                    Screen.Downloads -> DownloadsScreen(
                        settings = settings,
                        onStatus = { status = it },
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
                            repos = repos,
                            repoLoading = repoLoading,
                            repoError = repoError,
                            repoQuery = repoQuery,
                            onQueryChange = { repoQuery = it },
                            installing = installing,
                            onReload = ::reloadExtensions,
                            onInstallFile = ::installExtension,
                            onRefreshRepository = ::refreshRepository,
                            onInstallRepository = ::installFromRepository,
                            onStatus = { status = it },
                        )

                        SettingsPage.Accounts -> AccountsSettings(
                            settings = settings,
                            onBack = { settingsPage = SettingsPage.Root },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }

        val item = activeItem
        if (item != null) {
            AlertDialog(
                onDismissRequest = { activeItem = null },
                title = {
                    Text(
                        activeEpisode?.let { "${item.title} · ${it.label}" } ?: item.title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                text = {
                    Column(Modifier.fillMaxWidth()) {
                        if (resolving) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    Modifier.height(18.dp).width(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(12.dp))
                                Text("Resolving streams…")
                            }
                        } else if (streams.isEmpty()) {
                            Text(
                                "No streams returned for ${item.title}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            // player_select_source_and_subs.xml: the bold "Sources" header
                            // (pick_source) with its settings button above the choice list.
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    "Sources",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = {
                                        activeItem = null
                                        detailItem = null
                                        screen = Screen.Settings
                                        settingsPage = SettingsPage.Player
                                    },
                                ) {
                                    Icon(Icons.Outlined.Settings, contentDescription = "Source settings")
                                }
                            }

                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(8.dp),
                                    )
                                    .background(MaterialTheme.colorScheme.surface),
                            ) {
                                streams.forEachIndexed { index, stream ->
                                    val selected = index == selectedSource
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedSource = index }
                                            .background(
                                                if (selected) MaterialTheme.colorScheme.surfaceVariant
                                                else Color.Transparent,
                                            )
                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                    ) {
                                        RadioButton(
                                            selected = selected,
                                            onClick = { selectedSource = index },
                                        )
                                        Text(
                                            stream.title,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f).padding(end = 4.dp),
                                        )
                                        IconButton(
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
                                        ) {
                                            Icon(Icons.Outlined.Download, contentDescription = "Download")
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                // apply_btt then cancel_btt, the order the original's button row uses.
                confirmButton = {
                    Row {
                        Button(
                            enabled = !resolving && streams.isNotEmpty(),
                            onClick = {
                                streams.getOrNull(selectedSource)?.let {
                                    status = Player.open(it, settings.playerCommand)
                                }
                                activeItem = null
                            },
                        ) { Text("Apply") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { activeItem = null }) { Text("Cancel") }
                    }
                },
            )
        }
    }
}

@Composable
private fun HomeScreen(
    sources: List<SourceEntry>,
    sourceIndex: Int,
    onSelectSource: (Int) -> Unit,
    sections: List<HomeSection>,
    loading: Boolean,
    failure: String?,
    onRetry: () -> Unit,
    onOpen: (CatalogItem) -> Unit,
    posterSize: Int,
    titleUnder: Boolean,
    randomButton: Boolean,
    showClock: Boolean,
) {
    // Upstream's home has no title; the top row is the source picker (the original's
    // "Change API" FAB) and, when enabled, the real-time clock (tv_layout_clock_key).
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
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
        if (showClock) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = remember { java.time.LocalTime.now().let { t ->
                    String.format("%02d:%02d", t.hour, t.minute)
                } },
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }

    Spacer(Modifier.height(6.dp))

    val description = sources.getOrNull(sourceIndex)?.description.orEmpty()
    if (description.isNotEmpty()) {
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
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
            Button(onClick = onRetry) { Text("Reload") }
        }

        else -> {
            // home_master_recycler: one homepage_parent row per section, header + horizontal list
            LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                if (randomButton && sections.any { it.items.isNotEmpty() }) {
                    item(key = "random") {
                        // home_random ExtendedFloatingActionButton, shown when random_button_key is on
                        val pool = sections.flatMap { it.items }
                        Button(onClick = { pool.randomOrNull()?.let(onOpen) }) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Random")
                        }
                    }
                }
                sections.filter { it.items.isNotEmpty() }.forEach { section ->
                    item(key = "header:${section.name}") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                section.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    item(key = "row:${section.name}") {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            section.items.forEach { item ->
                                PosterCard(
                                    item = item,
                                    posterSize = posterSize,
                                    titleUnder = titleUnder,
                                    onOpen = { onOpen(item) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Upstream's `secondsToReadable`: "1h 20m", the runtime line on a large episode card. */
private fun secondsToReadable(seconds: Int): String {
    var rest = seconds.toLong()
    val days = TimeUnit.SECONDS.toDays(rest)
    rest -= TimeUnit.DAYS.toSeconds(days)
    val hours = TimeUnit.SECONDS.toHours(rest)
    rest -= TimeUnit.HOURS.toSeconds(hours)
    val minutes = TimeUnit.SECONDS.toMinutes(rest)
    return buildString {
        if (days != 0L) append(days).append("d ")
        if (hours != 0L) append(hours).append("h ")
        append(minutes).append("m")
    }
}

/** The original's small "Filler" button (SmallWhiteButton + R.string.filler). */
@Composable
private fun FillerChip() {
    Box(
        Modifier
            .background(
                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text("Filler", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}

/**
 * The original's result screen (fragment_result.xml): the back bar, the poster + title + meta
 * header, the season picker and the episode list. Every string is the upstream one.
 */
@Composable
private fun DetailScreen(
    modifier: Modifier = Modifier,
    item: CatalogItem,
    groups: List<EpisodeGroup>,
    groupIndex: Int,
    onGroupIndex: (Int) -> Unit,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onPlay: (Episode) -> Unit,
    onDownload: (Episode) -> Unit,
) {
    val group = groups.getOrNull(groupIndex) ?: groups.firstOrNull()
    var seasonMenu by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        // result_back on the left, result_openinbrower + result_search on the right.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { runCatching { Desktop.getDesktop().browse(URI(item.url)) } }) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = "Open in browser")
            }
            IconButton(onClick = onSearch) {
                Icon(Icons.Outlined.Search, contentDescription = "Search")
            }
        }

        Spacer(Modifier.height(4.dp))

        // result_poster, result_title and the result_meta_type / result_meta_site line.
        Row(verticalAlignment = Alignment.Top) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(125.dp)
                    .height(200.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val kind = item.type.name.lowercase().replaceFirstChar { it.uppercase() }
                    Text(kind, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    if (!item.subtitle.isNullOrBlank()) {
                        Text(
                            "  ·  ${item.subtitle}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // result_season_button + result_episodes_text, side by side as in the FlowLayout.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (groups.size > 1) {
                Box {
                    OutlinedButton(onClick = { seasonMenu = true }) {
                        Text(group?.label ?: "Season 1")
                        Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = seasonMenu, onDismissRequest = { seasonMenu = false }) {
                        groups.forEachIndexed { index, entry ->
                            DropdownMenuItem(
                                text = { Text(entry.label) },
                                onClick = {
                                    seasonMenu = false
                                    onGroupIndex(index)
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
            }
            Text(
                "${group?.episodes?.size ?: 0} Episodes",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 17.sp,
            )
        }

        Spacer(Modifier.height(10.dp))

        val episodes = group?.episodes.orEmpty()
        if (episodes.isEmpty()) {
            Text("No Episodes found", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(episodes) { episode ->
                    EpisodeRow(
                        episode = episode,
                        onPlay = { onPlay(episode) },
                        onDownload = { onDownload(episode) },
                    )
                }
            }
        }
    }
}

/**
 * One episode row. Episodes with a still or a synopsis get the original's large card
 * (result_episode_large.xml); the rest get its 50dp compact row (result_episode.xml) — the
 * same split `EpisodeAdapter` makes.
 */
@Composable
private fun EpisodeRow(
    episode: Episode,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val large = !episode.posterUrl.isNullOrBlank() || !episode.overview.isNullOrBlank()

    if (!large) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, outline, RoundedCornerShape(8.dp))
                .clickable { onPlay() }
                .padding(horizontal = 8.dp),
        ) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            if (episode.isFiller) {
                Spacer(Modifier.width(8.dp))
                FillerChip()
            }
            Text(
                episode.label,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
            IconButton(onClick = onDownload) {
                Icon(Icons.Outlined.Download, contentDescription = "Download", tint = muted)
            }
        }
        return
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, outline, RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onPlay() }
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(126.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.background),
            ) {
                episode.posterUrl?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Icon(
                    Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(30.dp),
                )
            }
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (episode.isFiller) {
                        FillerChip()
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        episode.label,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val rating = episode.rating
                val runTime = episode.runTime
                if (rating != null || runTime != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (rating != null) {
                            Text("Rated: ${"%.1f".format(rating)}", color = muted, fontSize = 12.sp)
                        }
                        if (runTime != null) {
                            if (rating != null) Spacer(Modifier.width(10.dp))
                            Text(secondsToReadable(runTime * 60), color = muted, fontSize = 12.sp)
                        }
                    }
                }
                episode.airDate?.let { airDate ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        remember(airDate) {
                            SimpleDateFormat.getDateInstance(DateFormat.LONG, Locale.getDefault())
                                .format(Date(airDate))
                        },
                        color = muted,
                        fontSize = 12.sp,
                    )
                }
            }
            IconButton(onClick = onDownload) {
                Icon(Icons.Outlined.Download, contentDescription = "Download", tint = muted)
            }
        }

        episode.overview?.takeIf { it.isNotBlank() }?.let { overview ->
            Spacer(Modifier.height(6.dp))
            Text(
                overview,
                color = muted,
                fontSize = 13.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The upstream media-type chip row (tvtypes_chips.xml), in its order. */
private val searchTypeChips = listOf(
    "Movies" to ItemType.MOVIE,
    "TV Series" to ItemType.SERIES,
    "Anime" to ItemType.ANIME,
    "Asian Drama" to ItemType.OTHER,
    "Cartoons" to ItemType.OTHER,
    "Documentaries" to ItemType.OTHER,
    "Livestreams" to ItemType.LIVE,
    "Torrent" to ItemType.OTHER,
    "NSFW" to ItemType.NSFW,
    "Others" to ItemType.OTHER,
)

/**
 * fragment_search.xml on the desktop: the rounded search bar (search icon, clear button,
 * filter button) with the media-type chips row underneath, results in the poster grid.
 */
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
    onOpen: (CatalogItem) -> Unit,
    posterSize: Int,
    titleUnder: Boolean,
    nsfwEnabled: Boolean,
) {
    var selectedTypes by remember { mutableStateOf<Set<String>>(emptySet()) }

    // The bar: search field with a leading search icon, a clear button and the filter icon.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
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
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onBackground,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        "Search…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                    )
                }
                inner()
            },
        )
        if (searching) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }

    Spacer(Modifier.height(10.dp))

    // tvtypes_chips_scroll — the same chip labels, NSFW only when the setting allows it.
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        searchTypeChips
            .filter { (label, _) -> label != "NSFW" || nsfwEnabled }
            .forEach { (label, _) ->
                FilterChip(
                    selected = label in selectedTypes,
                    onClick = {
                        selectedTypes =
                            if (label in selectedTypes) selectedTypes - label else selectedTypes + label
                    },
                    label = { Text(label, fontSize = 12.sp) },
                )
            }
    }

    Spacer(Modifier.height(12.dp))

    fun matchesChips(item: CatalogItem): Boolean {
        if (selectedTypes.isEmpty()) return true
        val labels = searchTypeChips.filter { it.second == item.type }.map { it.first }
        return labels.any { it in selectedTypes }
    }

    when {
        searching -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text("Searching…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        failure != null -> Text(failure, color = MaterialTheme.colorScheme.error)

        advanced && searchGroups.isNotEmpty() -> LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            searchGroups.forEach { (sourceName, group) ->
                if (group.isNotEmpty()) {
                    item(key = "header:$sourceName") {
                        Text(
                            sourceName,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                        )
                    }
                    item(key = "grid:$sourceName") {
                        ItemList(
                            items = group.filter(::matchesChips),
                            posterSize = posterSize,
                            titleUnder = titleUnder,
                            onOpen = onOpen,
                        )
                    }
                }
            }
        }

        items.isEmpty() -> Text("No results yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        else -> ItemList(
            items = items.filter(::matchesChips),
            posterSize = posterSize,
            titleUnder = titleUnder,
            onOpen = onOpen,
        )
    }
}

/**
 * One poster cell, sized like upstream's home_result_grid.xml (125dp × 200dp artwork,
 * bold title under the poster when *Poster title location* is on, otherwise overlaid).
 */
@Composable
private fun RowScope.PosterCard(
    item: CatalogItem,
    posterSize: Int,
    titleUnder: Boolean,
    onOpen: () -> Unit,
) {
    val width = (125 - posterSize * 5).coerceAtLeast(60).dp
    Column(Modifier.width(width).clickable { onOpen() }) {
        Box(
            Modifier.fillMaxWidth()
                .aspectRatio(0.625f)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            if (item.posterUrl != null) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (!titleUnder) {
                Text(
                    item.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(6.dp),
                )
            }
        }
        if (titleUnder) {
            Spacer(Modifier.height(4.dp))
            Text(
                item.title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
            )
        }
    }
}

/**
 * The original app's poster grid: artwork in fixed cells with the title under the poster (or
 * overlaid when *Poster title location* is off); *Poster size* controls how many fit a row.
 */
@Composable
private fun ItemList(
    items: List<CatalogItem>,
    posterSize: Int,
    titleUnder: Boolean,
    onOpen: (CatalogItem) -> Unit,
) {
    val columns = (8 - posterSize / 2).coerceIn(3, 8)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(items, key = { it.url + it.title }) { item ->
            Column(Modifier.clickable { onOpen(item) }) {
                Box(
                    Modifier.fillMaxWidth()
                        .aspectRatio(0.625f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    if (item.posterUrl != null) {
                        AsyncImage(
                            model = item.posterUrl,
                            contentDescription = item.title,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (!titleUnder) {
                        Text(
                            item.title,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(6.dp),
                        )
                    }
                }
                if (titleUnder) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.title,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                    )
                    val subtitle = item.subtitle
                    if (!subtitle.isNullOrBlank()) {
                        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
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
