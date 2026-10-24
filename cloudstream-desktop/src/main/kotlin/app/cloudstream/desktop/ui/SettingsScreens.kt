package app.cloudstream.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Theaters
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
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

/**
 * Settings ported from the upstream Android app's real preference definitions:
 * `app/src/main/res/xml/settings_*.xml` define the rows, their order and grouping, and the
 * exact English strings from `values/strings.xml` are used verbatim. Desktop keeps every row
 * of the original; rows whose feature physically cannot exist on a PC (battery optimization,
 * Chromecast subtitles, Android TV seek amounts…) are shown dimmed with their original text,
 * and every preference that can be honored is stored under the original's semantics.
 */
enum class SettingsPage(
    val label: String,
    val icon: ImageVector,
    val subtitle: List<String>,
) {
    Root("Settings", Icons.Outlined.Build, emptyList()),
    General(
        "General", Icons.Outlined.Build,
        listOf("Language", "Downloads", "ISP Bypasses", "Links"),
    ),
    Player(
        "Player", Icons.Outlined.PlayArrow,
        listOf("Subtitles", "Player features", "Gestures", "Layout", "Cache"),
    ),
    Providers("Providers", Icons.Outlined.Language, emptyList()),
    Layout(
        "Layout", Icons.Outlined.GpsFixed,
        listOf("Looks", "Features", "Poster", "Toggle UI elements on poster"),
    ),
    UpdatesBackup(
        "Updates and Backup", Icons.Outlined.SystemUpdate,
        listOf("App updates", "Backup", "Extensions", "Actions"),
    ),
    Extensions("Extensions", Icons.Outlined.Extension, listOf("Add repository")),
    Accounts(
        "Accounts and Security", Icons.Outlined.Security,
        listOf("Accounts", "Security"),
    ),
}

// ------------------------------------------------------------------ shared widgets

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

/** One Preference row: title, optional summary, leading icon — exactly the original's shape. */
@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconEmoji: String? = null,
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
        when {
            iconEmoji != null -> {
                Text(iconEmoji, fontSize = 18.sp)
                Spacer(Modifier.width(14.dp))
            }

            icon != null -> {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                        .copy(alpha = if (enabled) 1f else 0.4f),
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(14.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.45f),
                fontSize = 15.sp,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                        .copy(alpha = if (enabled) 1f else 0.5f),
                    fontSize = 12.sp,
                )
            }
        }
        trailing?.invoke()
    }
}

/** SwitchPreferenceCompat row. */
@Composable
fun SwitchRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        enabled = enabled,
        trailing = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        },
    )
}

/** SeekBarPreference row, min..max with the current value shown, like upstream. */
@Composable
private fun SliderRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    value: Int,
    range: IntRange,
    enabled: Boolean = true,
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
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                        .copy(alpha = if (enabled) 1f else 0.4f),
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
            Text(
                "$value",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.45f),
                fontWeight = FontWeight.Medium,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { if (enabled) onChange(it.toInt().coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled,
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

/** ListPreference dialog: single choice with radio markers. */
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
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .border(
                                    2.dp,
                                    if (value == selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    },
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (value == selected) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(label, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** MultiSelectListPreference dialog: checkbox rows closed with Done, like upstream. */
@Composable
private fun MultiSelectDialog(
    title: String,
    choices: List<Pair<String, String>>,
    selected: Set<String>,
    onSelect: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.height(360.dp)) {
                choices.forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onSelect(
                                    if (value in selected) selected - value else selected + value,
                                )
                            }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                    ) {
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .border(
                                    1.5.dp,
                                    if (value in selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    },
                                    RoundedCornerShape(4.dp),
                                )
                                .background(
                                    if (value in selected) MaterialTheme.colorScheme.primary
                                    else Color.Transparent,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (value in selected) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

// ------------------------------------------------------------------ root

/**
 * The original settings landing page: profile row ("Default"), one entry per section with the
 * section's categories as its subtitle, and the click-to-copy build stamp at the bottom.
 */
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
            subtitle = page.subtitle.joinToString(),
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
    val stamp = "${AppVersion.VALUE} • desktop"
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                runCatching {
                    java.awt.Toolkit.getDefaultToolkit().systemClipboard
                        .setContents(java.awt.datatransfer.StringSelection(stamp), null)
                }
            }
            .padding(vertical = 8.dp),
    ) {
        Text(
            stamp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
    }
}

// ------------------------------------------------------------------ General
// app/src/main/res/xml/settings_general.xml — rows in source order, strings verbatim.

@Composable
fun GeneralSettings(settings: Settings, onBack: () -> Unit, onStatus: (String) -> Unit) {
    var showDisclaimer by remember { mutableStateOf(false) }
    var showCloneDialog by remember { mutableStateOf(false) }
    var showDnsDialog by remember { mutableStateOf(false) }

    Column {
        SectionHeader("General", onBack)

        // locale_key / app_language
        SettingsRow(title = "App Language", subtitle = "English", icon = Icons.Outlined.Language, enabled = false)
        Spacer(Modifier.height(6.dp))
        // legal_notice_key / legal_notice
        SettingsRow(title = "Disclaimer", icon = Icons.Outlined.Warning, onClick = { showDisclaimer = true })
        Spacer(Modifier.height(6.dp))
        // benene_count / benene + benene_des
        SettingsRow(
            title = "Give a benene to the devs",
            subtitle = "Given benene: ${settings.bananas}",
            iconEmoji = "🍌",
            onClick = {
                settings.bananas = settings.bananas + 1
                onStatus(if (settings.bananas == 1) "1 Benene given 🍌" else "${settings.bananas} Benenes given 🍌")
            },
        )

        // PreferenceCategory title_downloads
        SectionLabel("Downloads")
        // download_path_key / download_path_pref
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
        // download_parallel_key / parallel_downloads — SeekBar 1..10, default 3
        SliderRow(
            title = "Parallel downloads",
            subtitle = "How many different items that can be downloaded in parallel",
            value = settings.parallelDownloads,
            range = 1..10,
            onChange = { settings.parallelDownloads = it },
        )
        Spacer(Modifier.height(6.dp))
        // download_concurrent_key / concurrent_connections — SeekBar 1..10, default 3
        SliderRow(
            title = "Concurrent connections",
            subtitle = "How many concurrent connections each download can use while downloading",
            value = settings.concurrentConnections,
            range = 1..10,
            onChange = { settings.concurrentConnections = it },
        )
        Spacer(Modifier.height(6.dp))
        // battery_optimisation_key / battery_dialog_title — an Android power feature
        SettingsRow(
            title = "Disable Battery optimization",
            icon = Icons.Outlined.BatteryAlert,
            enabled = false,
        )

        // PreferenceCategory pref_category_bypass
        SectionLabel("ISP Bypasses")
        // override_site_key / add_site_pref + add_site_summary
        SettingsRow(
            title = "Clone site",
            subtitle = "Add a clone of an existing site, with a different URL",
            icon = Icons.Outlined.Add,
            onClick = { showCloneDialog = true },
        )
        Spacer(Modifier.height(6.dp))
        // dns_key / dns_pref + dns_pref_summary
        SettingsRow(
            title = "DNS over HTTPS",
            subtitle = "Useful for bypassing ISP blocks",
            icon = Icons.Outlined.Dns,
            onClick = { showDnsDialog = true },
        )
        Spacer(Modifier.height(6.dp))
        // jsdelivr_proxy_key / jsdelivr_proxy + jsdelivr_proxy_summary
        SwitchRow(
            title = "GitHub Proxy",
            subtitle = "Bypass blocking of raw github URLs using jsDelivr. May cause updates to be delayed by few days.",
            icon = Icons.Outlined.Code,
            checked = settings.githubProxy,
            onCheckedChange = { settings.githubProxy = it },
        )

        // PreferenceCategory pref_category_links
        SectionLabel("Links")
        val links = listOf(
            Triple("Github", "https://github.com/recloudstream/cloudstream", Icons.Outlined.Code),
            Triple(
                "Light novel app by the same devs",
                "https://github.com/LagradOst/QuickNovel",
                Icons.Outlined.Article,
            ),
            Triple("Join Discord", "https://discord.gg/5Hus6fM", Icons.Outlined.Info),
            Triple("CloudStream Wiki", "https://cloudstream.miraheze.org/", Icons.Outlined.Info),
        )
        links.forEachIndexed { index, (label, url, rowIcon) ->
            if (index > 0) Spacer(Modifier.height(6.dp))
            SettingsRow(
                title = label,
                subtitle = url,
                icon = rowIcon,
                onClick = { runCatching { Desktop.getDesktop().browse(java.net.URI(url)) } },
            )
        }
    }

    if (showDisclaimer) {
        AlertDialog(
            onDismissRequest = { showDisclaimer = false },
            title = { Text("Disclaimer") },
            text = { Text(Settings.LEGAL_NOTICE) },
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
                        "Add a clone of an existing site, with a different URL. " +
                            "Format: originalUrl=replacementUrl",
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

    if (showDnsDialog) {
        ChoiceDialog(
            title = "DNS over HTTPS",
            choices = Settings.dnsProviders.map { it.key to it.value },
            selected = settings.dns,
            onSelect = {
                settings.dns = it
                onStatus("DNS over HTTPS: ${Settings.dnsProviders[it]}")
            },
            onDismiss = { showDnsDialog = false },
        )
    }
}

// ------------------------------------------------------------------ Player
// app/src/main/res/xml/settings_player.xml — every row of the original kept.

@Composable
fun PlayerSettings(settings: Settings, onBack: () -> Unit) {
    var showPlayerDialog by remember { mutableStateOf(false) }

    Column {
        SectionHeader("Player", onBack)

        // episode_sync_enabled_key / episode_sync_settings + _des, default true
        SwitchRow(
            title = "Update watch progress",
            subtitle = "Automatically sync your current episode progress",
            icon = Icons.Outlined.Sync,
            checked = settings.episodeSync,
            onCheckedChange = { settings.episodeSync = it },
        )

        // PreferenceCategory pref_category_defaults
        SectionLabel("Defaults")
        // player_default_key / player_pref — the external player command on desktop
        SettingsRow(
            title = "Preferred video player",
            subtitle = settings.playerCommand.ifBlank { "System default (browser / mpv)" },
            icon = Icons.Outlined.PlayArrow,
            onClick = { showPlayerDialog = true },
        )

        // PreferenceCategory pref_category_player_layout
        SectionLabel("Layout")
        SettingsRow(
            title = "Video player title max chars",
            icon = Icons.Outlined.Title,
            enabled = false,
        )
        Spacer(Modifier.height(6.dp))
        SettingsRow(
            title = "Show player information",
            icon = Icons.Outlined.Title,
            enabled = false,
        )
        Spacer(Modifier.height(6.dp))
        // hide_player_control_names_key, default false
        SwitchRow(
            title = "Hide names of the player's controls",
            icon = Icons.Outlined.Title,
            checked = settings.flag("player.hideControlNames", false),
            onCheckedChange = { settings.flag("player.hideControlNames", false, it) },
        )

        // PreferenceCategory pref_category_subtitles
        SectionLabel("Subtitles")
        SettingsRow(
            title = "Subtitles",
            subtitle = "Player subtitles settings",
            icon = Icons.Outlined.Subtitles,
            enabled = false,
        )
        Spacer(Modifier.height(6.dp))
        SettingsRow(
            title = "Chromecast Subtitles",
            subtitle = "Chromecast subtitles settings",
            icon = Icons.Outlined.Subtitles,
            enabled = false,
        )

        // PreferenceCategory pref_category_player_features — switches store their flag with
        // the original's defaults; rows that are Android-player-only stay visible but dimmed.
        SectionLabel("Player features")
        // source_priority_key
        SettingsRow(
            title = "Source priority",
            subtitle = "Decide how video sources should be sorted in the player",
            icon = Icons.Outlined.People,
            enabled = false,
        )
        Spacer(Modifier.height(6.dp))
        // pip_enabled_key, default true
        SwitchRow(
            title = "Picture-in-picture",
            subtitle = "Continues playback in a miniature player on top of other apps",
            icon = Icons.Outlined.Theaters,
            checked = settings.flag("player.pip", true),
            onCheckedChange = { settings.flag("player.pip", true, it) },
        )
        Spacer(Modifier.height(6.dp))
        // player_resize_enabled_key, default true
        SwitchRow(
            title = "Player resize button",
            subtitle = "Remove the black borders",
            icon = Icons.Outlined.Theaters,
            checked = settings.flag("player.resize", true),
            onCheckedChange = { settings.flag("player.resize", true, it) },
        )
        Spacer(Modifier.height(6.dp))
        // playback_speed_enabled_key / eigengraumode_settings, default false
        SwitchRow(
            title = "Playback speed",
            subtitle = "Adds a speed option in the player",
            icon = Icons.Outlined.Speed,
            checked = settings.flag("player.speed", false),
            onCheckedChange = { settings.flag("player.speed", false, it) },
        )
        Spacer(Modifier.height(6.dp))
        // compressor_enabled_key, default false
        SwitchRow(
            title = "Dynamic Range Compressor",
            subtitle = "Show compressor controls in the player (helps balance loud/quiet audio)",
            icon = Icons.Outlined.Speed,
            checked = settings.flag("player.compressor", false),
            onCheckedChange = { settings.flag("player.compressor", false, it) },
        )
        Spacer(Modifier.height(6.dp))
        // speedup_key / speedup_title, default false
        SwitchRow(
            title = "LongPress Speed Toggle",
            subtitle = "Hold to get 2x speed",
            icon = Icons.Outlined.Speed,
            checked = settings.flag("player.speedup", false),
            onCheckedChange = { settings.flag("player.speedup", false, it) },
        )
        Spacer(Modifier.height(6.dp))
        // autoplay_next_key, default true
        SwitchRow(
            title = "Autoplay next episode",
            subtitle = "Start the next episode when the current one ends",
            icon = Icons.Outlined.PlayArrow,
            checked = settings.flag("player.autoplayNext", true),
            onCheckedChange = { settings.flag("player.autoplayNext", true, it) },
        )
        Spacer(Modifier.height(6.dp))
        // enable_skip_op_from_database / video_skip_op, default true
        SwitchRow(
            title = "Skip OP",
            subtitle = "Show skip popups for opening/ending",
            icon = Icons.Outlined.PlayArrow,
            checked = settings.flag("player.skipOp", true),
            onCheckedChange = { settings.flag("player.skipOp", true, it) },
        )
        Spacer(Modifier.height(6.dp))
        // rotate_video_key, default false
        SwitchRow(
            title = "Rotate",
            subtitle = "Display a toggle button for screen orientation",
            icon = Icons.Outlined.ScreenRotation,
            checked = settings.flag("player.rotate", false),
            onCheckedChange = { settings.flag("player.rotate", false, it) },
        )
        Spacer(Modifier.height(6.dp))
        // auto_rotate_video_key, default true
        SwitchRow(
            title = "Auto rotate",
            subtitle = "Enable automatic switching of screen orientation based on video orientation",
            icon = Icons.Outlined.ScreenRotation,
            checked = settings.flag("player.autoRotate", true),
            onCheckedChange = { settings.flag("player.autoRotate", true, it) },
        )
        Spacer(Modifier.height(6.dp))
        // preview_seekbar_key, default true
        SwitchRow(
            title = "Seekbar preview",
            subtitle = "Enable preview thumbnail on seekbar",
            icon = Icons.Outlined.Sync,
            checked = settings.flag("player.seekbarPreview", true),
            onCheckedChange = { settings.flag("player.seekbarPreview", true, it) },
        )
        Spacer(Modifier.height(6.dp))
        // software_decoding_key (a Preference upstream, with a boolean value)
        SettingsRow(
            title = "Software decoding",
            subtitle = "Software decoding enables the player to play video files not supported by " +
                "your device, but may cause laggy or unstable playback on high resolution.",
            icon = Icons.Outlined.Extension,
            enabled = false,
        )
        Spacer(Modifier.height(6.dp))
        // extra_brightness_key, default false
        SwitchRow(
            title = "Extra brightness",
            subtitle = "Enable brightness filter when 100% display brightness is exceeded",
            icon = Icons.Outlined.Speed,
            checked = settings.flag("player.extraBrightness", false),
            onCheckedChange = { settings.flag("player.extraBrightness", false, it) },
        )

        // PreferenceCategory pref_category_gestures
        SectionLabel("Gestures")
        // swipe_enabled_key, default true
        SwitchRow(
            title = "Swipe to seek",
            subtitle = "Swipe from side to side to control your position in a video",
            icon = Icons.Outlined.TouchApp,
            checked = settings.flag("player.swipeSeek", true),
            onCheckedChange = { settings.flag("player.swipeSeek", true, it) },
        )
        Spacer(Modifier.height(6.dp))
        // swipe_vertical_enabled_key, default true
        SwitchRow(
            title = "Swipe to change settings",
            subtitle = "Slide up or down on the left or right side to change brightness or volume",
            icon = Icons.Outlined.TouchApp,
            checked = settings.flag("player.swipeVertical", true),
            onCheckedChange = { settings.flag("player.swipeVertical", true, it) },
        )
        Spacer(Modifier.height(6.dp))
        // double_tap_enabled_key, default false
        SwitchRow(
            title = "Double tap to seek",
            subtitle = "Tap twice on the right or left side to seek forwards or backwards",
            icon = Icons.Outlined.TouchApp,
            checked = settings.flag("player.doubleTapSeek", false),
            onCheckedChange = { settings.flag("player.doubleTapSeek", false, it) },
        )
        Spacer(Modifier.height(6.dp))
        // double_tap_pause_enabled_key, default false
        SwitchRow(
            title = "Double tap to pause",
            subtitle = "Tap twice in the middle to pause",
            icon = Icons.Outlined.PlayArrow,
            checked = settings.flag("player.doubleTapPause", false),
            onCheckedChange = { settings.flag("player.doubleTapPause", false, it) },
        )
        Spacer(Modifier.height(6.dp))
        // double_tap_seek_time_key — SeekBar 5..60 step 5, default 10
        SliderRow(
            title = "Player seek amount (Seconds)",
            value = settings.seekAmount,
            range = 5..60,
            onChange = { settings.seekAmount = it },
        )

        // PreferenceCategory pref_category_cache — disk cache is handled by the external player
        SectionLabel("Cache")
        SettingsRow(
            title = "Video cache on disk",
            subtitle = "Causes problems if set too high on devices with low storage space, such as Android TV.",
            icon = Icons.Outlined.Storage,
            enabled = false,
        )
        Spacer(Modifier.height(6.dp))
        SettingsRow(
            title = "Video buffer size",
            subtitle = "Causes crashes if set too high on devices with low memory, such as Android TV.",
            icon = Icons.Outlined.Storage,
            enabled = false,
        )
        Spacer(Modifier.height(6.dp))
        SettingsRow(
            title = "Video buffer length",
            subtitle = "Causes crashes if set too high on devices with low memory, such as Android TV.",
            icon = Icons.Outlined.Storage,
            enabled = false,
        )
        Spacer(Modifier.height(6.dp))
        SettingsRow(title = "Clear video and image cache", icon = Icons.Outlined.Storage, enabled = false)

        // PreferenceCategory pref_category_android_tv
        SectionLabel("Android TV")
        SliderRow(
            title = "Player Shown - Seek Amount",
            subtitle = "The seek amount used when the player is visible",
            value = 30,
            range = 5..60,
            enabled = false,
            onChange = {},
        )
        Spacer(Modifier.height(6.dp))
        SliderRow(
            title = "Player Hidden - Seek Amount",
            subtitle = "The seek amount used when the player is hidden",
            value = 10,
            range = 5..60,
            enabled = false,
            onChange = {},
        )
    }

    if (showPlayerDialog) {
        var text by remember { mutableStateOf(settings.playerCommand) }
        AlertDialog(
            onDismissRequest = { showPlayerDialog = false },
            title = { Text("Preferred video player") },
            text = {
                Column {
                    Text(
                        "Command line used to play streams, e.g. mpv or the full path to vlc.exe. " +
                            "Leave empty to open links in the default browser.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        label = { Text("Command") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        settings.playerCommand = text
                        showPlayerDialog = false
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showPlayerDialog = false }) { Text("Cancel") } },
        )
    }
}

// ------------------------------------------------------------------ Providers
// app/src/main/res/xml/settings_providers.xml

@Composable
fun ProvidersSettings(
    settings: Settings,
    languages: List<String>,
    testTargets: List<Catalog>,
    onBack: () -> Unit,
) {
    var showLanguages by remember { mutableStateOf(false) }
    var showMedia by remember { mutableStateOf(false) }
    var showDubSub by remember { mutableStateOf(false) }
    var testResults by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column {
        SectionHeader("Providers", onBack)

        // provider_lang_key / provider_lang_settings
        SettingsRow(
            title = "Extension languages",
            subtitle = settings.providerLanguages.joinToString().ifBlank { "All languages" },
            icon = Icons.Outlined.Language,
            onClick = { showLanguages = true },
        )
        Spacer(Modifier.height(6.dp))
        // prefer_media_type_key / preferred_media_settings
        SettingsRow(
            title = "Preferred media",
            subtitle = settings.preferredMedia.joinToString().ifBlank { "All media" },
            icon = Icons.Outlined.PlayArrow,
            onClick = { showMedia = true },
        )
        Spacer(Modifier.height(6.dp))
        // display_sub_key / display_subbed_dubbed_settings — multiselect None/Dub/Sub
        SettingsRow(
            title = "Display Dubbed/Subbed Anime",
            subtitle = settings.displayDubSub.joinToString().ifBlank { "None" },
            onClick = { showDubSub = true },
        )
        Spacer(Modifier.height(6.dp))
        // enable_nsfw_on_providers_key + apply_on_restart, default false
        SwitchRow(
            title = "Enable NSFW on supported Extensions",
            subtitle = "Restart the app to see changes.",
            icon = Icons.Outlined.Extension,
            checked = settings.nsfwEnabled,
            onCheckedChange = { settings.nsfwEnabled = it },
        )
        Spacer(Modifier.height(6.dp))
        // test_providers_key / test_extensions + test_extensions_summary (upstream text)
        SettingsRow(
            title = "Test all Extensions",
            subtitle = "This Test is meant for developers only and does not verifies or denies " +
                "working of any extension.",
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
                Text(
                    "Testing ${testTargets.size} extension(s)…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }

        testResults?.let { results ->
            Spacer(Modifier.height(12.dp))
            results.forEach { (name, outcome) ->
                Text(
                    "$name — $outcome",
                    color = if (outcome.startsWith("ok")) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    fontSize = 12.sp,
                )
            }
        }
    }

    if (showLanguages) {
        var selected by remember { mutableStateOf(settings.providerLanguages.toSet()) }
        MultiSelectDialog(
            title = "Extension languages",
            choices = languages.sorted().map { it to it.replaceFirstChar { c -> c.uppercase() } },
            selected = selected,
            onSelect = { selected = it },
            onDismiss = {
                settings.providerLanguages = selected.toList()
                showLanguages = false
            },
        )
    }

    if (showMedia) {
        var selected by remember { mutableStateOf(settings.preferredMedia.toSet()) }
        MultiSelectDialog(
            title = "Preferred media",
            choices = listOf(
                ItemType.MOVIE.name to "Movies",
                ItemType.SERIES.name to "Series",
                ItemType.ANIME.name to "Anime",
            ),
            selected = selected,
            onSelect = { selected = it },
            onDismiss = {
                settings.preferredMedia = selected.toList()
                showMedia = false
            },
        )
    }

    if (showDubSub) {
        var selected by remember { mutableStateOf(settings.displayDubSub.toSet()) }
        MultiSelectDialog(
            title = "Display Dubbed/Subbed Anime",
            choices = listOf("None" to "None", "Dub" to "Dub", "Sub" to "Sub"),
            selected = selected,
            onSelect = { chosen ->
                // "None" clears the selection, matching the original dialog's first entry
                selected = if ("None" in chosen) emptySet() else chosen
            },
            onDismiss = {
                settings.displayDubSub = selected.toList()
                showDubSub = false
            },
        )
    }
}

// ------------------------------------------------------------------ Layout
// app/src/main/res/xml/settings_ui.xml

@Composable
fun LayoutSettings(
    settings: Settings,
    dark: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var showTheme by remember { mutableStateOf(false) }
    var showPrimaryColor by remember { mutableStateOf(false) }
    var showAppLayout by remember { mutableStateOf(false) }
    var showPosterUi by remember { mutableStateOf(false) }
    var showConfirmExit by remember { mutableStateOf(false) }
    var showQualityFilter by remember { mutableStateOf(false) }

    Column {
        SectionHeader("Layout", onBack)

        // PreferenceCategory pref_category_looks
        SectionLabel("Looks")
        // primary_color_key / primary_color_settings
        SettingsRow(
            title = "Primary color",
            subtitle = settings.primaryColor,
            onClick = { showPrimaryColor = true },
        )
        Spacer(Modifier.height(6.dp))
        // app_theme_key / app_theme_settings
        SettingsRow(
            title = "App theme",
            subtitle = settings.appTheme,
            onClick = { showTheme = true },
        )
        Spacer(Modifier.height(6.dp))
        // app_layout_key / app_layout
        SettingsRow(
            title = "App Layout",
            subtitle = settings.appLayout,
            icon = Icons.Outlined.Tv,
            onClick = { showAppLayout = true },
        )
        Spacer(Modifier.height(6.dp))
        // bottom_title_key, default true
        SwitchRow(
            title = "Poster title location",
            subtitle = "Put the title under the poster",
            icon = Icons.Outlined.Title,
            checked = settings.posterTitleUnder,
            onCheckedChange = { settings.posterTitleUnder = it },
        )
        Spacer(Modifier.height(6.dp))
        // overscan_key — SeekBar 0..100, TV overscan is not applicable on a monitor
        SliderRow(
            title = "Overscan",
            subtitle = "Changes the bounds of the screen",
            value = settings.overscan,
            range = 0..100,
            enabled = false,
            onChange = { settings.overscan = it },
        )
        Spacer(Modifier.height(6.dp))
        // poster_size_key — SeekBar 0..15, default 0
        SliderRow(
            title = "Poster size",
            subtitle = "Changes size of posters",
            icon = Icons.Outlined.GridOn,
            value = settings.posterSize,
            range = 0..15,
            onChange = { settings.posterSize = it },
        )

        // PreferenceCategory pref_category_ui_features
        SectionLabel("Features")
        // poster_ui_key / poster_ui_settings
        SettingsRow(
            title = "Toggle UI elements on poster",
            icon = Icons.Outlined.Tv,
            onClick = { showPosterUi = true },
        )
        Spacer(Modifier.height(6.dp))
        // advanced_search, default true
        SwitchRow(
            title = "Advanced Search",
            subtitle = "Gives you the search results separated by provider",
            checked = settings.advancedSearch,
            onCheckedChange = { settings.advancedSearch = it },
        )
        Spacer(Modifier.height(6.dp))
        // search_suggestions_enabled, default true
        SwitchRow(
            title = "Search Suggestions",
            subtitle = "Show search suggestions while typing",
            checked = settings.searchSuggestions,
            onCheckedChange = { settings.searchSuggestions = it },
        )
        Spacer(Modifier.height(6.dp))
        // show_trailers_key, default true
        SwitchRow(
            title = "Show trailers",
            icon = Icons.Outlined.Theaters,
            checked = settings.trailersEnabled,
            onCheckedChange = { settings.trailersEnabled = it },
        )
        Spacer(Modifier.height(6.dp))
        // show_kitsu_posters_key, default true
        SwitchRow(
            title = "Show posters from Kitsu",
            checked = settings.kitsuPosters,
            onCheckedChange = { settings.kitsuPosters = it },
        )
        Spacer(Modifier.height(6.dp))
        // show_cast_in_details_key, default true
        SwitchRow(
            title = "Show cast panel",
            icon = Icons.Outlined.People,
            checked = settings.showCast,
            onCheckedChange = { settings.showCast = it },
        )
        Spacer(Modifier.height(6.dp))
        // show_fillers_key, default false
        SwitchRow(
            title = "Show filler episode for anime",
            checked = settings.showFillers,
            onCheckedChange = { settings.showFillers = it },
        )
        Spacer(Modifier.height(6.dp))
        // show_player_metadata_key, default true
        SwitchRow(
            title = "Show Player Metadata Overlay",
            checked = settings.playerMetadataOverlay,
            onCheckedChange = { settings.playerMetadataOverlay = it },
        )
        Spacer(Modifier.height(6.dp))
        // tv_layout_clock_key, default false
        SwitchRow(
            title = "Show real time clock",
            subtitle = "Show a real time clock at the top of the screen. Applies to the homepage and player",
            icon = Icons.Outlined.Info,
            checked = settings.showClock,
            onCheckedChange = { settings.showClock = it },
        )
        Spacer(Modifier.height(6.dp))
        // random_button_key, default false
        SwitchRow(
            title = "Random Button",
            subtitle = "Show random button on Homepage and Library",
            icon = Icons.Outlined.PlayArrow,
            checked = settings.randomButton,
            onCheckedChange = { settings.randomButton = it },
        )
        Spacer(Modifier.height(6.dp))
        // confirm_exit_key / confirm_before_exiting_title + _desc
        SettingsRow(
            title = "Confirm before exiting",
            subtitle = "Show dialog before exiting the app — " +
                Settings.confirmExitOptions[settings.confirmExit],
            icon = Icons.Outlined.ExitToApp,
            onClick = { showConfirmExit = true },
        )
        Spacer(Modifier.height(6.dp))
        // pref_filter_search_quality_key / pref_filter_search_quality
        SettingsRow(
            title = "Hide selected video quality in search results",
            icon = Icons.Outlined.FilterList,
            onClick = { showQualityFilter = true },
        )
    }

    if (showPrimaryColor) {
        AlertDialog(
            onDismissRequest = { showPrimaryColor = false },
            title = { Text("Primary color") },
            text = {
                Column {
                    Settings.primaryColors.forEach { (name, argb) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    settings.primaryColor = name
                                    showPrimaryColor = false
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                        CircleShape,
                                    ),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(name, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPrimaryColor = false }) { Text("Close") } },
        )
    }

    if (showTheme) {
        ChoiceDialog(
            title = "App theme",
            choices = Settings.themeNames.map { it to it },
            selected = settings.appTheme,
            onSelect = {
                settings.appTheme = it
                // The desktop ships two schemes; every upstream theme maps onto one of them
                onThemeChange(it != "Flashbang")
            },
            onDismiss = { showTheme = false },
        )
    }

    if (showAppLayout) {
        ChoiceDialog(
            title = "App Layout",
            choices = Settings.appLayouts.map { it.key to it.value },
            selected = settings.appLayout,
            onSelect = { settings.appLayout = it },
            onDismiss = { showAppLayout = false },
        )
    }

    if (showPosterUi) {
        var selected by remember { mutableStateOf(settings.posterElements.toSet()) }
        MultiSelectDialog(
            title = "Toggle UI elements on poster",
            choices = listOf(
                "rating" to "Rating",
                "episode" to "Episode",
                "title" to "Title",
                "hd" to "HD",
                "dub" to "Dub",
                "sub" to "Sub",
            ),
            selected = selected,
            onSelect = { selected = it },
            onDismiss = {
                settings.posterElements = selected.toList()
                showPosterUi = false
            },
        )
    }

    if (showConfirmExit) {
        ChoiceDialog(
            title = "Confirm before exiting",
            choices = Settings.confirmExitOptions.map { it.key to it.value },
            selected = settings.confirmExit,
            onSelect = { settings.confirmExit = it },
            onDismiss = { showConfirmExit = false },
        )
    }

    if (showQualityFilter) {
        var selected by remember { mutableStateOf(settings.filterSearchQuality.toSet()) }
        MultiSelectDialog(
            title = "Hide selected video quality in search results",
            choices = Settings.searchQualities.map { it to it },
            selected = selected,
            onSelect = { selected = it },
            onDismiss = {
                settings.filterSearchQuality = selected.toList()
                showQualityFilter = false
            },
        )
    }
}

// ------------------------------------------------------------------ Updates and Backup
// app/src/main/res/xml/settings_updates.xml

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
    var showBackupFrequency by remember { mutableStateOf(false) }

    Column {
        SectionHeader("Updates and Backup", onBack)

        // PreferenceCategory pref_category_app_updates
        SectionLabel("App updates")
        // manual_update_plugins_key / check_for_update
        SettingsRow(
            title = "Check for Update",
            subtitle = when {
                checking -> "Checking…"
                update?.newer == true -> "Version ${update!!.version} is available"
                update != null -> "You are on the latest version (${AppVersion.VALUE})"
                else -> AppVersion.VALUE
            },
            icon = Icons.Outlined.SystemUpdate,
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
        // install_prerelease_key / install_prerelease
        SwitchRow(
            title = "Install pre-release version",
            icon = Icons.Outlined.DeveloperMode,
            checked = settings.installPrerelease,
            onCheckedChange = {
                settings.installPrerelease = it
                onStatus(if (it) "Pre-release updates enabled" else "Pre-release updates disabled")
            },
        )
        Spacer(Modifier.height(6.dp))
        // apk_installer_key / apk_installer_settings + _des — Android package installer
        SettingsRow(
            title = "APK Installer",
            subtitle = "Some devices do not support the new package installer. Try the legacy " +
                "option if updates do not install.",
            icon = Icons.Outlined.Build,
            enabled = false,
        )
        Spacer(Modifier.height(6.dp))
        // auto_update_key / updates_settings + updates_settings_des, default true
        SwitchRow(
            title = "Show app updates",
            subtitle = "Automatically search for new updates after starting the app.",
            icon = Icons.Outlined.Notifications,
            checked = settings.showAppUpdates,
            onCheckedChange = { settings.showAppUpdates = it },
        )

        // PreferenceCategory pref_category_backup
        SectionLabel("Backup")
        // backup_key / backup_settings
        SettingsRow(
            title = "Back up data",
            icon = Icons.Outlined.Save,
            onClick = {
                scope.launch {
                    val result = runCatching {
                        withContext(Dispatchers.IO) {
                            Backup.create(settings.backupDir, ExtensionHost.ensureExtensionsDir())
                                .fileName.toString()
                        }
                    }
                    onStatus(
                        result.fold({ "Backup written: $it" }, { "Backup failed: ${it.message}" }),
                    )
                }
            },
        )
        Spacer(Modifier.height(6.dp))
        // automatic_backup_key / backup_frequency — periodic_work_values
        SettingsRow(
            title = "Backup frequency",
            subtitle = Settings.backupFrequencies[settings.backupFrequency.toString()],
            icon = Icons.Outlined.Save,
            onClick = { showBackupFrequency = true },
        )
        Spacer(Modifier.height(6.dp))
        // restore_key / restore_settings
        SettingsRow(
            title = "Restore data from backup",
            subtitle = restoreResult,
            icon = Icons.Outlined.Restore,
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
        // backup_path_key / backup_path_title
        SettingsRow(
            title = "Backup folder location",
            subtitle = settings.backupDir,
            icon = Icons.Outlined.Folder,
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

        // PreferenceCategory pref_category_extensions
        SectionLabel("Extensions")
        // auto_update_plugins_key / automatic_plugin_updates, default true
        SwitchRow(
            title = "Automatic plugin updates",
            icon = Icons.Outlined.Extension,
            checked = settings.autoPluginUpdates,
            onCheckedChange = { settings.autoPluginUpdates = it },
        )
        Spacer(Modifier.height(6.dp))
        // auto_download_plugins_key / automatic_plugin_download + _summary
        SwitchRow(
            title = "Automatically download plugins",
            subtitle = "Automatically install all not yet installed plugins from added repositories.",
            icon = Icons.Outlined.Extension,
            checked = settings.autoDownloadPlugins,
            onCheckedChange = { settings.autoDownloadPlugins = it },
        )
        Spacer(Modifier.height(6.dp))
        // manual_update_plugins_key / update_plugins + update_plugins_manually
        SettingsRow(
            title = "Update Plugins",
            subtitle = "Update plugins manually",
            icon = Icons.Outlined.Extension,
            onClick = onUpdatePlugins,
        )

        // PreferenceCategory pref_category_actions
        SectionLabel("Actions")
        // show_logcat_key / show_log_cat — Android logcat
        SettingsRow(title = "Show Logcat 🐈", icon = Icons.Outlined.Article, enabled = false)
        Spacer(Modifier.height(6.dp))
        // redo_setup_key / redo_setup_process
        SettingsRow(title = "Redo setup process", icon = Icons.Outlined.Construction, enabled = false)
    }

    if (showBackupFrequency) {
        ChoiceDialog(
            title = "Backup frequency",
            choices = Settings.backupFrequencies.map { it.key to it.value },
            selected = settings.backupFrequency.toString(),
            onSelect = {
                settings.backupFrequency = it.toIntOrNull() ?: 0
            },
            onDismiss = { showBackupFrequency = false },
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
// app/src/main/res/xml/settings_account.xml

@Composable
fun AccountsSettings(settings: Settings, onBack: () -> Unit) {
    Column {
        SectionHeader("Accounts and Security", onBack)

        // PreferenceCategory pref_category_accounts — account services from the original,
        // their sign-in flows are upstream server features
        SectionLabel("Accounts")
        listOf(
            "MyAnimeList" to Icons.Outlined.Info,
            "Kitsu" to Icons.Outlined.Info,
            "AniList" to Icons.Outlined.Info,
            "Simkl" to Icons.Outlined.Info,
            "OpenSubtitles" to Icons.Outlined.Subtitles,
            "SubDL" to Icons.Outlined.Subtitles,
            "AnimeSkip" to Icons.Outlined.Info,
        ).forEach { (account, rowIcon) ->
            SettingsRow(
                title = account,
                icon = rowIcon,
                enabled = false,
            )
            Spacer(Modifier.height(6.dp))
        }
        // skip_startup_account_select_key / skip_startup_account_select_pref, default false
        SwitchRow(
            title = "Skip account selection at startup",
            icon = Icons.Outlined.AccountCircle,
            checked = settings.skipAccountSelection,
            onCheckedChange = { settings.skipAccountSelection = it },
        )

        // PreferenceCategory pref_category_security
        SectionLabel("Security")
        // biometric_key / biometric_setting + biometric_setting_summary — Android biometrics
        SettingsRow(
            title = "Lock with Biometrics",
            subtitle = "Unlock the app with Fingerprint, Face ID, PIN, Pattern and Password.",
            icon = Icons.Outlined.Fingerprint,
            enabled = false,
        )
    }
}
