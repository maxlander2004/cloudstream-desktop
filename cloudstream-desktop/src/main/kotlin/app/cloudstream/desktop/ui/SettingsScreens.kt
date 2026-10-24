package app.cloudstream.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudstream.desktop.api.Catalog
import app.cloudstream.desktop.api.ItemType
import app.cloudstream.desktop.core.AppUpdates
import app.cloudstream.desktop.core.AppVersion
import app.cloudstream.desktop.core.Backup
import app.cloudstream.desktop.core.ExtensionHost
import app.cloudstream.desktop.core.Settings
import app.cloudstream.desktop.core.UpdateCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Paths
import javax.swing.JFileChooser

/** Which settings section is open, mirroring the original app's settings pages. */
enum class SettingsPage(val label: String, val icon: ImageVector) {
    Root("Settings", Icons.Outlined.Palette),
    General("General", Icons.Outlined.Language),
    Player("Player", Icons.Outlined.PlayCircle),
    Providers("Providers", Icons.Outlined.Extension),
    Layout("Layout", Icons.Outlined.Layers),
    UpdatesBackup("Updates and Backup", Icons.Outlined.Backup),
    Extensions("Extensions", Icons.Outlined.Extension),
    Accounts("Accounts and Security", Icons.Outlined.Security),
}

// ------------------------------------------------------------------ shared pieces

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
    )
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled && onClick != null) { onClick?.invoke() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.45f),
                fontSize = 15.sp,
            )
            if (subtitle != null) {
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

@Composable
private fun SliderRow(
    title: String,
    subtitle: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Text("$value", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}

@Composable
private fun SectionHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun ChoiceDialog(
    title: String,
    choices: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                choices.forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onSelect(value)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                    ) {
                        RadioButton(selected = value == selected)
                        Spacer(Modifier.width(10.dp))
                        Text(label, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun RadioButton(selected: Boolean) {
    Box(
        Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            ),
    )
}

// ------------------------------------------------------------------ root

@Composable
fun SettingsRootScreen(onOpen: (SettingsPage) -> Unit) {
    val sections = listOf(
        SettingsPage.General, SettingsPage.Player, SettingsPage.Providers, SettingsPage.Layout,
        SettingsPage.UpdatesBackup, SettingsPage.Extensions, SettingsPage.Accounts,
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.AccountCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text("Default", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
    }
    Spacer(Modifier.height(20.dp))

    sections.forEach { page ->
        SettingsRow(
            title = page.label,
            icon = page.icon,
            onClick = { onOpen(page) },
            trailing = {
                Icon(
                    Icons.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        Spacer(Modifier.height(6.dp))
    }

    Spacer(Modifier.height(18.dp))
    Text(
        "${AppVersion.VALUE} · desktop build",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        modifier = Modifier.fillMaxWidth(),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

// ------------------------------------------------------------------ General

@Composable
fun GeneralSettings(settings: Settings, onBack: () -> Unit, onStatus: (String) -> Unit) {
    var showDisclaimer by remember { mutableStateOf(false) }
    var showCloneDialog by remember { mutableStateOf(false) }
    var showDohDialog by remember { mutableStateOf(false) }

    Column {
        SectionHeader("General", onBack)

        SectionLabel("App")
        SettingsRow(title = "App Language", subtitle = "English", enabled = false)
        Spacer(Modifier.height(6.dp))
        SettingsRow(title = "Disclaimer", onClick = { showDisclaimer = true })
        Spacer(Modifier.height(6.dp))

        SectionLabel("Downloads")
        SettingsRow(
            title = "Download path",
            subtitle = settings.downloadsDir,
            onClick = {
                val chooser = JFileChooser(settings.downloadsDir).apply {
                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    dialogTitle = "Choose the download folder"
                }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    settings.downloadsDir = chooser.selectedFile.absolutePath
                    onStatus("Download folder set")
                }
            },
        )
        Spacer(Modifier.height(6.dp))
        SliderRow(
            title = "Parallel downloads",
            subtitle = "How many different items that can be downloaded in parallel",
            value = settings.parallelDownloads,
            range = 1..10,
            onChange = { settings.parallelDownloads = it },
        )
        Spacer(Modifier.height(6.dp))
        SliderRow(
            title = "Concurrent connections",
            subtitle = "How many concurrent connections each download can use while downloading",
            value = settings.concurrentConnections,
            range = 1..10,
            onChange = { settings.concurrentConnections = it },
        )
        Spacer(Modifier.height(6.dp))

        SectionLabel("ISP Bypasses")
        SettingsRow(
            title = "Clone site",
            subtitle = settings.cloneSite.ifBlank { "Add a clone of an existing site, with a different URL" },
            onClick = { showCloneDialog = true },
        )
        Spacer(Modifier.height(6.dp))
        SettingsRow(
            title = "DNS over HTTPS",
            subtitle = "Useful for bypassing ISP blocks",
            onClick = { showDohDialog = true },
        )
        Spacer(Modifier.height(6.dp))
        SwitchRow(
            title = "GitHub Proxy",
            subtitle = "Bypass blocking of raw github URLs using jsDelivr. May cause updates to be delayed by few days.",
            checked = settings.githubProxy,
            onCheckedChange = { settings.githubProxy = it },
        )
        Spacer(Modifier.height(6.dp))

        SectionLabel("Links")
        SettingsRow(
            title = "Github",
            subtitle = AppUpdates.REPOSITORY.removeSuffix("/cloudstream-desktop").removePrefix("maxlander2004/")
                .let { "https://github.com/maxlander2004/cloudstream-desktop" },
            onClick = {
                runCatching {
                    Desktop.getDesktop().browse(java.net.URI("https://github.com/maxlander2004/cloudstream-desktop"))
                }
            },
        )
    }

    if (showDisclaimer) {
        AlertDialog(
            onDismissRequest = { showDisclaimer = false },
            title = { Text("Disclaimer") },
            text = {
                Text(
                    "This application does not host or provide any media. It only loads " +
                        "extensions built by the community; what they list is their responsibility.",
                )
            },
            confirmButton = { TextButton(onClick = { showDisclaimer = false }) { Text("Close") } },
        )
    }

    if (showCloneDialog) {
        var text by remember { mutableStateOf(settings.cloneSite) }
        AlertDialog(
            onDismissRequest = { showCloneDialog = false },
            title = { Text("Clone site") },
            text = {
                Column {
                    Text(
                        "originalUrl=replacementUrl — providers whose main site is blocked are " +
                            "pointed at the clone, the same way the Android app does.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        placeholder = { Text("https://example.com=https://mirror.example") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        settings.cloneSite = text
                        showCloneDialog = false
                        onStatus("Clone site saved")
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showCloneDialog = false }) { Text("Cancel") } },
        )
    }

    if (showDohDialog) {
        ChoiceDialog(
            title = "DNS over HTTPS",
            choices = Settings.dohProviders.map { it.key to it.value },
            selected = settings.doh,
            onSelect = {
                settings.doh = it
                onStatus("DNS over HTTPS: ${Settings.dohProviders[it]}")
            },
            onDismiss = { showDohDialog = false },
        )
    }
}

// ------------------------------------------------------------------ Player

@Composable
fun PlayerSettings(settings: Settings, onBack: () -> Unit) {
    Column {
        SectionHeader("Player", onBack)

        SectionLabel("Playback")
        Text(
            "Streams are handed to an external player, e.g. mpv or the full path to vlc.exe. " +
                "Leave empty to open links in your default browser.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    }
}

// ------------------------------------------------------------------ Providers

@Composable
fun ProvidersSettings(
    settings: Settings,
    languages: List<String>,
    testTargets: List<Catalog>,
    onBack: () -> Unit,
) {
    var showLanguages by remember { mutableStateOf(false) }
    var showMedia by remember { mutableStateOf(false) }
    var testResults by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column {
        SectionHeader("Providers", onBack)

        SettingsRow(
            title = "Extension languages",
            subtitle = settings.providerLanguages.joinToString().ifBlank { "All languages" },
            onClick = { showLanguages = true },
        )
        Spacer(Modifier.height(6.dp))
        SettingsRow(
            title = "Preferred media",
            subtitle = settings.preferredMedia.joinToString().ifBlank { "All media" },
            onClick = { showMedia = true },
        )
        Spacer(Modifier.height(6.dp))
        SwitchRow(
            title = "Display Dubbed/Subbed Anime",
            subtitle = "Filters anime results by subtitles/dub where the provider reports it",
            checked = settings.displayDubbedSubbed,
            onCheckedChange = { settings.displayDubbedSubbed = it },
        )
        Spacer(Modifier.height(6.dp))
        SwitchRow(
            title = "Enable NSFW on supported Extensions",
            subtitle = "Shows adult results from extensions that provide them",
            checked = settings.nsfwEnabled,
            onCheckedChange = { settings.nsfwEnabled = it },
        )
        Spacer(Modifier.height(6.dp))
        SettingsRow(
            title = "Test all Extensions",
            subtitle = "Runs the home page request of every loaded extension. This test is meant " +
                "for developers and does not verify or deny the working of any extension.",
            enabled = !testing,
            onClick = {
                testing = true
                testResults = null
                scope.launch {
                    val results = testTargets.map { catalog ->
                        val outcome = withContext(Dispatchers.IO) {
                            withTimeoutOrNull(20_000) {
                                runCatching { catalog.browse() }.fold(
                                    onSuccess = { "ok · ${it.size} items" },
                                    onFailure = { "failed · ${it.message ?: "error"}" },
                                )
                            } ?: "timed out"
                        }
                        catalog.name to outcome
                    }
                    testResults = results
                    testing = false
                }
            },
        )

        if (testing) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Testing ${testTargets.size} extension(s)…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }

        testResults?.let { results ->
            Spacer(Modifier.height(12.dp))
            results.forEach { (name, outcome) ->
                Text(
                    "$name — $outcome",
                    color = if (outcome.startsWith("ok")) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                )
            }
        }
    }

    if (showLanguages) {
        val selected = remember { mutableStateOf(settings.providerLanguages.toSet()) }
        AlertDialog(
            onDismissRequest = {
                settings.providerLanguages = selected.value.toList()
                showLanguages = false
            },
            title = { Text("Extension languages") },
            text = {
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selected.value.isEmpty(),
                        onClick = { selected.value = emptySet() },
                        label = { Text("All") },
                    )
                    languages.sorted().forEach { language ->
                        FilterChip(
                            selected = language in selected.value,
                            onClick = {
                                selected.value =
                                    if (language in selected.value) selected.value - language
                                    else selected.value + language
                            },
                            label = { Text(language.uppercase()) },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = {
                settings.providerLanguages = selected.value.toList()
                showLanguages = false
            }) { Text("Done") } },
        )
    }

    if (showMedia) {
        val selected = remember { mutableStateOf(settings.preferredMedia.toSet()) }
        val options = listOf(
            ItemType.MOVIE.name to "Movies",
            ItemType.SERIES.name to "Series",
            ItemType.ANIME.name to "Anime",
        )
        AlertDialog(
            onDismissRequest = {
                settings.preferredMedia = selected.value.toList()
                showMedia = false
            },
            title = { Text("Preferred media") },
            text = {
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selected.value.isEmpty(),
                        onClick = { selected.value = emptySet() },
                        label = { Text("All") },
                    )
                    options.forEach { (value, label) ->
                        FilterChip(
                            selected = value in selected.value,
                            onClick = {
                                selected.value =
                                    if (value in selected.value) selected.value - value
                                    else selected.value + value
                            },
                            label = { Text(label) },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = {
                settings.preferredMedia = selected.value.toList()
                showMedia = false
            }) { Text("Done") } },
        )
    }
}

// ------------------------------------------------------------------ Layout

@Composable
fun LayoutSettings(
    settings: Settings,
    dark: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var showTheme by remember { mutableStateOf(false) }

    Column {
        SectionHeader("Layout", onBack)

        SectionLabel("Looks")
        SettingsRow(
            title = "App theme",
            subtitle = if (dark) "Dark" else "Light",
            onClick = { showTheme = true },
        )
        Spacer(Modifier.height(6.dp))
        SwitchRow(
            title = "Poster title location",
            subtitle = "Put the title under the poster",
            checked = settings.posterTitleUnder,
            onCheckedChange = { settings.posterTitleUnder = it },
        )
        Spacer(Modifier.height(6.dp))
        SliderRow(
            title = "Poster size",
            subtitle = "Changes size of posters",
            value = settings.posterSize,
            range = 0..6,
            onChange = { settings.posterSize = it },
        )

        SectionLabel("Features")
        SwitchRow(
            title = "Advanced Search",
            subtitle = "Gives you the search results separated by provider",
            checked = settings.advancedSearch,
            onCheckedChange = { settings.advancedSearch = it },
        )
    }

    if (showTheme) {
        ChoiceDialog(
            title = "App theme",
            choices = listOf("dark" to "Dark", "light" to "Light"),
            selected = if (dark) "dark" else "light",
            onSelect = {
                settings.appTheme = it
                onThemeChange(it == "dark")
            },
            onDismiss = { showTheme = false },
        )
    }
}

// ------------------------------------------------------------------ Updates and Backup

@Composable
fun UpdatesBackupSettings(
    settings: Settings,
    onBack: () -> Unit,
    onStatus: (String) -> Unit,
    onRefreshExtensions: () -> Unit,
    onUpdatePlugins: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<UpdateCheck?>(null) }
    var restoreResult by remember { mutableStateOf<String?>(null) }

    Column {
        SectionHeader("Updates and Backup", onBack)

        SectionLabel("App updates")
        SettingsRow(
            title = "Check for Update",
            subtitle = when {
                checking -> "Checking…"
                update?.newer == true -> "Version ${update!!.version} is available"
                update != null -> "You are on the latest version (${AppVersion.VALUE})"
                else -> AppVersion.VALUE
            },
            enabled = !checking,
            onClick = {
                checking = true
                scope.launch {
                    update = runCatching { AppUpdates.check(settings.installPrerelease) }.getOrNull()
                    checking = false
                    onStatus(
                        when {
                            update?.newer == true -> "Update ${update!!.version} available"
                            update != null -> "App is up to date"
                            else -> "Could not reach GitHub"
                        },
                    )
                }
            },
        )
        Spacer(Modifier.height(6.dp))
        SwitchRow(
            title = "Show app updates",
            subtitle = "Automatically search for new updates after starting the app.",
            checked = settings.showAppUpdates,
            onCheckedChange = { settings.showAppUpdates = it },
        )
        Spacer(Modifier.height(6.dp))
        SwitchRow(
            title = "Install pre-release version",
            checked = settings.installPrerelease,
            onCheckedChange = { settings.installPrerelease = it },
        )

        SectionLabel("Backup")
        SettingsRow(
            title = "Back up data",
            subtitle = "Settings and installed extensions into ${settings.backupDir}",
            onClick = {
                scope.launch {
                    val result = runCatching {
                        withContext(Dispatchers.IO) {
                            Backup.create(settings.backupDir, ExtensionHost.ensureExtensionsDir()).fileName.toString()
                        }
                    }
                    onStatus(result.fold({ "Backup written: $it" }, { "Backup failed: ${it.message}" }))
                }
            },
        )
        Spacer(Modifier.height(6.dp))
        SettingsRow(
            title = "Restore data from backup",
            subtitle = restoreResult,
            onClick = {
                val dialog = FileDialog(null as Frame?, "Select a CloudStream backup", FileDialog.LOAD)
                dialog.isVisible = true
                val file = dialog.file
                val directory = dialog.directory
                dialog.dispose()
                if (file == null || directory == null) return@SettingsRow
                scope.launch {
                    val result = runCatching {
                        withContext(Dispatchers.IO) {
                            Backup.restore(
                                Paths.get(directory, file),
                                ExtensionHost.ensureExtensionsDir(),
                            )
                        }
                    }
                    restoreResult = result.getOrNull()
                    onStatus(result.fold({ it }, { "Restore failed: ${it.message}" }))
                    onRefreshExtensions()
                }
            },
        )
        Spacer(Modifier.height(6.dp))
        SettingsRow(
            title = "Backup folder location",
            subtitle = settings.backupDir,
            onClick = {
                val chooser = JFileChooser(settings.backupDir).apply {
                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    dialogTitle = "Choose the backup folder"
                }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    settings.backupDir = chooser.selectedFile.absolutePath
                }
            },
        )

        SectionLabel("Extensions")
        SwitchRow(
            title = "Automatic plugin updates",
            subtitle = "Update installed extensions when a newer version is published",
            checked = settings.autoPluginUpdates,
            onCheckedChange = { settings.autoPluginUpdates = it },
        )
        Spacer(Modifier.height(6.dp))
        SwitchRow(
            title = "Automatically download plugins",
            subtitle = "Automatically install all not yet installed plugins from added repositories",
            checked = settings.autoDownloadPlugins,
            onCheckedChange = { settings.autoDownloadPlugins = it },
        )
        Spacer(Modifier.height(6.dp))
        SettingsRow(
            title = "Update Plugins",
            subtitle = "Update plugins manually",
            onClick = onUpdatePlugins,
        )
    }

    update?.takeIf { it.newer }?.let { info ->
        AlertDialog(
            onDismissRequest = { update = null },
            title = { Text("Update available") },
            text = { Text("Version ${info.version} is available. Open the release page to download it.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        runCatching { Desktop.getDesktop().browse(java.net.URI(info.url)) }
                        update = null
                    },
                ) { Text("Open download page") }
            },
            dismissButton = { TextButton(onClick = { update = null }) { Text("Later") } },
        )
    }
}

// ------------------------------------------------------------------ Accounts and Security

@Composable
fun AccountsSettings(onBack: () -> Unit) {
    Column {
        SectionHeader("Accounts and Security", onBack)

        SectionLabel("Accounts")
        listOf("MAL", "Kitsu", "AniList", "Simkl", "OpenSubtitles", "SubDL", "AnimeSkip").forEach { account ->
            SettingsRow(
                title = account,
                subtitle = "Account sync runs on upstream servers; not available on desktop yet",
                enabled = false,
            )
            Spacer(Modifier.height(6.dp))
        }

        SectionLabel("Security")
        SettingsRow(
            title = "Lock with Biometrics",
            subtitle = "Fingerprint, Face ID and PIN are Android features; not available on desktop",
            enabled = false,
        )
    }
}
