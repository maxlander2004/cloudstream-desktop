package app.cloudstream.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import app.cloudstream.desktop.core.Accounts
import app.cloudstream.desktop.core.AppUpdates
import app.cloudstream.desktop.core.Backup
import app.cloudstream.desktop.core.Doh
import app.cloudstream.desktop.core.ExtensionHost
import app.cloudstream.desktop.core.Settings
import app.cloudstream.desktop.core.SubtitleSearch
import app.cloudstream.desktop.core.TrackingProvider
import app.cloudstream.desktop.core.UpdateCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.Desktop

/** Which settings section is open, mirroring the original app's settings pages. */
enum class SettingsPage(val label: String, val icon: ImageVector, val summary: String) {
    Root("Settings", Icons.Outlined.Palette, ""),
    General("General", Icons.Outlined.Language, "Downloads, player command, network"),
    Player("Player", Icons.Outlined.PlayCircle, "Playback and subtitle options"),
    Providers("Providers", Icons.Outlined.Extension, "Languages, media types, NSFW"),
    Ui("UI", Icons.Outlined.Slideshow, "App theme, accent color, poster UI"),
    Layout("Layout", Icons.Outlined.Layers, "Poster size and title placement"),
    UpdatesBackup("Updates and Backup", Icons.Outlined.Backup, "App updates, plugins, backup"),
    Extensions("Extensions", Icons.Outlined.Extension, "Install and manage extensions"),
    Accounts("Accounts and Security", Icons.Outlined.Security, "Account and security settings"),
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
                        RadioButton(selected = value == selected, onClick = null)
                        Spacer(Modifier.width(10.dp))
                        Text(label, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

// ------------------------------------------------------------------ general

@Composable
fun GeneralSettings(
    settings: Settings,
    onBack: () -> Unit,
    onStatus: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var dohPicker by remember { mutableStateOf(false) }
    SectionHeader("General", onBack)

    SectionLabel("Downloads")
    SwitchRow(
        title = "Download subtitles",
        subtitle = "Save subtitle tracks next to the downloaded file",
        checked = settings.downloadSubtitles,
        onCheckedChange = {
            settings.downloadSubtitles = it
            onStatus(if (it) "Subtitles enabled" else "Subtitles disabled")
        },
    )
    SwitchRow(
        title = "Archive downloaded files",
        subtitle = "Store finished downloads in the archive folder",
        checked = settings.archiveDownloads,
        onCheckedChange = {
            settings.archiveDownloads = it
            onStatus("Archive setting updated")
        },
    )

    SectionLabel("Player")
    OutlinedTextField(
        value = settings.playerCommand,
        onValueChange = { settings.playerCommand = it },
        label = { Text("Player command") },
        placeholder = { Text("mpv --no-video %s") },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { }
            .padding(vertical = 6.dp, horizontal = 12.dp),
    )
    Spacer(Modifier.height(8.dp))

    SectionLabel("Network & Proxy")
    OutlinedTextField(
        value = settings.cloneSite,
        onValueChange = { settings.cloneSite = it },
        label = { Text("Clone site bypass") },
        placeholder = { Text("originalUrl=replacementUrl") },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { }
            .padding(vertical = 6.dp, horizontal = 12.dp),
    )
    Spacer(Modifier.height(8.dp))

    SettingsRow(
        title = "DNS-over-HTTPS",
        subtitle = "Name lookups go through the chosen resolver",
        onClick = { dohPicker = true },
        trailing = {
            Text(
                Settings.dohProviders[settings.doh] ?: "Off",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
            )
        },
    )
    if (dohPicker) {
        ChoiceDialog(
            title = "DNS-over-HTTPS",
            choices = Settings.dohProviders.map { (value, label) -> value to label },
            selected = settings.doh,
            onSelect = { value ->
                settings.doh = value
                onStatus(
                    if (Doh.endpoint(value) == null) {
                        "DNS-over-HTTPS off — using the system resolver"
                    } else {
                        "DNS-over-HTTPS: ${Settings.dohProviders[value]}"
                    },
                )
            },
            onDismiss = { dohPicker = false },
        )
    }

    SettingsRow(
        title = "Github proxy (jsDelivr)",
        subtitle = "Bypass raw.githubusercontent.com blocking",
        trailing = { Switch(checked = settings.githubProxy, onCheckedChange = { settings.githubProxy = it }) },
    )

    Spacer(Modifier.height(14.dp))
    Button(
        onClick = {
            scope.launch {
                onStatus("Saving…")
                withContext(Dispatchers.IO) { settings.save() }
                onStatus("Settings saved")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Save settings")
    }
}

// ------------------------------------------------------------------ player

@Composable
fun PlayerSettings(
    settings: Settings,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var osUser by remember { mutableStateOf(settings.openSubtitlesUser) }
    var osPassword by remember { mutableStateOf("") }
    var osBusy by remember { mutableStateOf(false) }
    var osMessage by remember { mutableStateOf<String?>(null) }

    SectionHeader("Player", onBack)

    SectionLabel("In-app playback")
    SettingsRow(
        title = "In-app player (VLC)",
        subtitle = VideoEngine.description(),
        trailing = {
            Switch(
                checked = settings.inAppPlayer && VideoEngine.available,
                onCheckedChange = { settings.inAppPlayer = it },
                enabled = VideoEngine.available,
            )
        },
    )
    SwitchRow(
        title = "Autoplay next episode",
        subtitle = "Start the next episode when one finishes",
        checked = settings.autoplayNext,
        onCheckedChange = { settings.autoplayNext = it },
    )
    SwitchRow(
        title = "Resume from last position",
        subtitle = "Continue where playback was left",
        checked = settings.resumePlayback,
        onCheckedChange = { settings.resumePlayback = it },
    )

    SectionLabel("Subtitles")
    SwitchRow(
        title = "Load subtitles automatically",
        subtitle = "Turn on a subtitle track when an episode starts",
        checked = settings.subtitleAutoLoad,
        onCheckedChange = { settings.subtitleAutoLoad = it },
    )
    OutlinedTextField(
        value = settings.subtitleLanguage,
        onValueChange = { settings.subtitleLanguage = it },
        label = { Text("Preferred subtitle language") },
        placeholder = { Text("en") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { }
            .padding(vertical = 6.dp, horizontal = 12.dp),
    )
    Text(
        "Subtitle files saved next to a download are picked up automatically.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 14.dp, top = 2.dp, bottom = 8.dp),
    )

    SectionLabel("Subtitle search")
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilterChip(
            selected = settings.subtitleProvider != SubtitleSearch.PROVIDER_SUBDL &&
                settings.subtitleProvider != SubtitleSearch.PROVIDER_OPENSUBTITLES,
            onClick = { settings.subtitleProvider = "off" },
            label = { Text("Off") },
        )
        Spacer(Modifier.width(8.dp))
        FilterChip(
            selected = settings.subtitleProvider == SubtitleSearch.PROVIDER_SUBDL,
            onClick = { settings.subtitleProvider = SubtitleSearch.PROVIDER_SUBDL },
            label = { Text("SubDL") },
        )
        Spacer(Modifier.width(8.dp))
        FilterChip(
            selected = settings.subtitleProvider == SubtitleSearch.PROVIDER_OPENSUBTITLES,
            onClick = { settings.subtitleProvider = SubtitleSearch.PROVIDER_OPENSUBTITLES },
            label = { Text("OpenSubtitles") },
        )
    }
    Spacer(Modifier.height(8.dp))

    when (settings.subtitleProvider) {
        SubtitleSearch.PROVIDER_SUBDL -> {
            OutlinedTextField(
                value = settings.subdlApiKey,
                onValueChange = { settings.subdlApiKey = it },
                label = { Text("SubDL API key") },
                placeholder = { Text("From subdl.com/panel/api") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { }
                    .padding(vertical = 6.dp, horizontal = 12.dp),
            )
            Text(
                "Create a free key on your SubDL account; it is stored with the rest of your " +
                    "settings.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 14.dp, top = 2.dp, bottom = 8.dp),
            )
        }

        SubtitleSearch.PROVIDER_OPENSUBTITLES -> {
            val signedIn = settings.openSubtitlesToken.isNotBlank() &&
                settings.openSubtitlesTokenExpires > System.currentTimeMillis()
            if (signedIn) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Signed in as ${settings.openSubtitlesUser.ifBlank { "OpenSubtitles" }}",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            settings.openSubtitlesToken = ""
                            settings.openSubtitlesTokenExpires = 0
                            osMessage = "Signed out"
                        },
                    ) { Text("Sign out") }
                }
            } else {
                OutlinedTextField(
                    value = osUser,
                    onValueChange = { osUser = it },
                    label = { Text("OpenSubtitles username") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { }
                        .padding(vertical = 6.dp, horizontal = 12.dp),
                )
                OutlinedTextField(
                    value = osPassword,
                    onValueChange = { osPassword = it },
                    label = { Text("OpenSubtitles password") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { }
                        .padding(vertical = 6.dp, horizontal = 12.dp),
                )
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = {
                        scope.launch {
                            osBusy = true
                            osMessage = null
                            val result = SubtitleSearch.openSubtitlesLogin(osUser, osPassword)
                            osBusy = false
                            result.onSuccess { session ->
                                settings.openSubtitlesToken = session.token
                                settings.openSubtitlesTokenExpires =
                                    System.currentTimeMillis() + 23 * 60 * 60 * 1000L
                                settings.openSubtitlesUser = session.username
                                osUser = session.username
                                osPassword = ""
                                osMessage = "Signed in as ${session.username}"
                            }.onFailure { osMessage = it.message ?: "Sign-in failed" }
                        }
                    },
                    enabled = !osBusy,
                ) { Text(if (osBusy) "Signing in…" else "Sign in to OpenSubtitles") }
                Text(
                    "Search works without an account; OpenSubtitles requires this free login " +
                        "before it hands out downloads. The password is not saved — only the " +
                        "24 hour token it returns.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 8.dp),
                )
            }
            osMessage?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 14.dp, bottom = 6.dp),
                )
            }
        }

        else -> Text(
            "Subtitle search is off. Subtitle files saved next to a download and the source's " +
                "own tracks still work.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 14.dp, top = 2.dp, bottom = 8.dp),
        )
    }

    OutlinedTextField(
        value = settings.subtitleSearchLanguage,
        onValueChange = { settings.subtitleSearchLanguage = it },
        label = { Text("Subtitle search language") },
        placeholder = { Text("en") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { }
            .padding(vertical = 6.dp, horizontal = 12.dp),
    )
    Text(
        "The player's subtitle menu then lists what the service has for the episode.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 14.dp, top = 2.dp, bottom = 8.dp),
    )

    OutlinedTextField(
        value = settings.vlcPath,
        onValueChange = { settings.vlcPath = it },
        label = { Text("libvlc folder (optional)") },
        placeholder = { Text("/usr/lib/vlc") },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { }
            .padding(vertical = 6.dp, horizontal = 12.dp),
    )
    Spacer(Modifier.height(8.dp))

    SectionLabel("Fallback player")
    OutlinedTextField(
        value = settings.playerCommand,
        onValueChange = { settings.playerCommand = it },
        label = { Text("External player command") },
        placeholder = { Text("mpv --no-video %s") },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { }
            .padding(vertical = 6.dp, horizontal = 12.dp),
    )
}

// ------------------------------------------------------------------ providers

@Composable
fun ProvidersSettings(
    settings: Settings,
    languages: List<String>,
    testTargets: List<Catalog>,
    onBack: () -> Unit,
) {
    SectionHeader("Providers", onBack)

    SectionLabel("Provider languages")
    if (languages.isEmpty()) {
        Text(
            "No provider languages configured.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            languages.forEach { lang ->
                FilterChip(
                    selected = lang in settings.providerLanguages,
                    onClick = {
                        settings.providerLanguages = if (lang in settings.providerLanguages) {
                            settings.providerLanguages - lang
                        } else {
                            settings.providerLanguages + lang
                        }
                    },
                    label = { Text(lang.uppercase()) },
                )
            }
        }
    }

    SectionLabel("Media types")
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
                label = { Text(label) },
            )
        }
    }

    SwitchRow(
        title = "Show dubbed / subbed",
        subtitle = "Show both dubbed and subbed versions",
        checked = settings.displayDubbedSubbed,
        onCheckedChange = { settings.displayDubbedSubbed = it },
    )

    SwitchRow(
        title = "NSFW providers",
        subtitle = "Include providers flagged as NSFW",
        checked = settings.nsfwEnabled,
        onCheckedChange = { settings.nsfwEnabled = it },
    )
}

// ------------------------------------------------------------------ layout

@Composable
fun LayoutSettings(
    settings: Settings,
    dark: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    SectionHeader("Layout", onBack)

    SwitchRow(
        title = "Dark theme",
        subtitle = "Use dark theme throughout the app",
        checked = dark,
        onCheckedChange = onThemeChange,
    )

    SliderRow(
        title = "Poster size",
        subtitle = "More columns = smaller posters",
        value = settings.posterSize,
        range = 0..6,
        onChange = { settings.posterSize = it },
    )

    SwitchRow(
        title = "Title under poster",
        subtitle = "Show the title below the artwork",
        checked = settings.posterTitleUnder,
        onCheckedChange = { settings.posterTitleUnder = it },
    )

    SwitchRow(
        title = "Advanced search",
        subtitle = "Search all providers at once",
        checked = settings.advancedSearch,
        onCheckedChange = { settings.advancedSearch = it },
    )
}

// ------------------------------------------------------------------ ui

@Composable
fun UiSettings(
    settings: Settings,
    dark: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onAccentChange: (String) -> Unit,
    onBack: () -> Unit,
    onStatus: (String) -> Unit,
) {
    var showTheme by remember { mutableStateOf(false) }
    var showPrimaryColor by remember { mutableStateOf(false) }
    var showPosterUi by remember { mutableStateOf(false) }

    SectionHeader("UI", onBack)

    SettingsRow(
        title = "App theme",
        subtitle = "Dark or light appearance",
        trailing = { TextButton(onClick = { showTheme = true }) { Text(if (dark) "Dark" else "Light") } },
    )

    SettingsRow(
        title = "Accent color",
        subtitle = "Primary brand color",
        trailing = { TextButton(onClick = { showPrimaryColor = true }) { Text(settings.accentColor) } },
    )

    SettingsRow(
        title = "Poster UI",
        subtitle = "Where the title is drawn on the poster",
        trailing = {
            TextButton(onClick = { showPosterUi = true }) {
                Text(if (settings.posterTitleUnder) "Below" else "Over")
            }
        },
    )

    if (showTheme) {
        ChoiceDialog(
            title = "App theme",
            choices = listOf("dark" to "Dark", "light" to "Light"),
            selected = if (dark) "dark" else "light",
            onSelect = {
                settings.appTheme = it
                onThemeChange(it == "dark")
                showTheme = false
            },
            onDismiss = { showTheme = false },
        )
    }

    if (showPrimaryColor) {
        ChoiceDialog(
            title = "Accent color",
            choices = accentColorOptions(),
            selected = settings.accentColor,
            onSelect = {
                settings.accentColor = it
                onAccentChange(it)
                onStatus("Accent color updated")
                showPrimaryColor = false
            },
            onDismiss = { showPrimaryColor = false },
        )
    }

    if (showPosterUi) {
        // Upstream's Poster UI choice: the title over the artwork or underneath it.
        val options = listOf(false to "Title over poster", true to "Title below poster")
        AlertDialog(
            onDismissRequest = { showPosterUi = false },
            title = { Text("Poster UI") },
            text = {
                Column {
                    options.forEach { (under, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    settings.posterTitleUnder = under
                                    showPosterUi = false
                                    onStatus(if (under) "Titles below posters" else "Titles over posters")
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                        ) {
                            RadioButton(selected = settings.posterTitleUnder == under, onClick = null)
                            Spacer(Modifier.width(10.dp))
                            Text(label, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPosterUi = false }) { Text("Close") } },
        )
    }
}

private fun accentColorOptions(): List<Pair<String, String>> = listOf(
    "#3D50FA" to "Blue (default)",
    "#F53B66" to "Red",
    "#6200EA" to "Purple",
    "#00BFA5" to "Green",
    "#FF1493" to "Pink",
    "#FF9800" to "Orange",
    "#4CAF50" to "Light green",
    "#2196F3" to "Light blue",
)

private fun posterUiOptions(): List<Pair<String, String>> = listOf(
    "under" to "Title below poster",
    "over" to "Title over poster",
)

// ------------------------------------------------------------------ root

@Composable
fun SettingsRootScreen(onOpen: (SettingsPage) -> Unit) {
    val sections: List<SettingsPage> = listOf(
        SettingsPage.General,
        SettingsPage.Player,
        SettingsPage.Providers,
        SettingsPage.Ui,
        SettingsPage.Layout,
        SettingsPage.UpdatesBackup,
        SettingsPage.Extensions,
        SettingsPage.Accounts,
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
            subtitle = page.summary,
            icon = page.icon,
            onClick = { onOpen(page) },
            trailing = { Icon(Icons.Outlined.KeyboardArrowRight, contentDescription = null) },
        )
    }
}

// ------------------------------------------------------------------ updates & backup


@Composable
fun UpdatesBackupSettings(
    settings: Settings,
    onBack: () -> Unit,
    onStatus: (String) -> Unit,
    onRefreshExtensions: () -> Unit,
    onUpdatePlugins: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    SectionHeader("Updates and Backup", onBack)

    SwitchRow(
        title = "Check for app updates on startup",
        subtitle = "Show a banner when a newer version is available",
        checked = settings.showAppUpdates,
        onCheckedChange = { settings.showAppUpdates = it },
    )

    SwitchRow(
        title = "Automatically update plugins",
        subtitle = "Update installed extensions from the repository",
        checked = settings.autoPluginUpdates,
        onCheckedChange = { settings.autoPluginUpdates = it },
    )

    SwitchRow(
        title = "Automatically download plugins",
        subtitle = "Install extensions that are missing locally",
        checked = settings.autoDownloadPlugins,
        onCheckedChange = { settings.autoDownloadPlugins = it },
    )

    SettingsRow(
        title = "Refresh extension repositories",
        subtitle = "Re-fetch the repository list from the configured URLs",
        onClick = {
            scope.launch {
                onStatus("Refreshing repositories…")
                withContext(Dispatchers.IO) { onRefreshExtensions() }
                onStatus("Repository refresh finished")
            }
        },
        trailing = { Icon(Icons.Outlined.KeyboardArrowRight, contentDescription = null) },
    )

    SettingsRow(
        title = "Update plugins now",
        subtitle = "Update everything the repositories offer newer versions of",
        onClick = {
            scope.launch {
                onStatus("Updating plugins…")
                withContext(Dispatchers.IO) { onUpdatePlugins() }
                onStatus("Plugin update finished")
            }
        },
        trailing = { Icon(Icons.Outlined.KeyboardArrowRight, contentDescription = null) },
    )

    SectionLabel("Backup")
    OutlinedTextField(
        value = settings.backupDir,
        onValueChange = { settings.backupDir = it },
        label = { Text("Backup directory") },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { }
            .padding(vertical = 6.dp, horizontal = 12.dp),
    )
    Spacer(Modifier.height(8.dp))

    Button(
        onClick = {
            scope.launch {
                onStatus("Creating backup…")
                val zip = withContext(Dispatchers.IO) {
                    Backup.create(
                        dir = settings.backupDir,
                        extensionsDir = ExtensionHost.ensureExtensionsDir(),
                    )
                }
                onStatus("Backup created: ${zip.fileName}")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Create backup now")
    }

    Spacer(Modifier.height(14.dp))
    Button(
        onClick = {
            scope.launch {
                onStatus("Checking for updates…")
                val check = withTimeoutOrNull(15_000) {
                    withContext(Dispatchers.IO) { AppUpdates.check(settings.installPrerelease) }
                }
                if (check?.newer == true) {
                    onStatus("Update ${check.version} available")
                    runCatching {
                        Desktop.getDesktop().browse(java.net.URI(check.url))
                    }
                } else {
                    onStatus(if (check?.newer == true) "Update available" else "No update available")
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Check for app updates")
    }
}

// ------------------------------------------------------------------ installed extensions list

@Composable
internal fun InstalledExtensionsList(
    extensions: app.cloudstream.desktop.core.ExtensionLoadResult,
    installing: String?,
) {
    if (extensions.loaded.isEmpty()) {
        Text(
            "No extensions loaded. Install one from file or refresh repositories.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
    } else {
        extensions.loaded.forEach { extension ->
            val manifest = extension.manifest
            SettingsRow(
                title = manifest.name ?: extension.file.fileName.toString(),
                subtitle = "v${manifest.version ?: 0} · loaded",
                icon = Icons.Outlined.Extension,
                trailing = {
                    if (installing == manifest.name) {
                        CircularProgressIndicator(Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
                    }
                },
            )
        }
    }
}

// ------------------------------------------------------------------ accounts

@Composable
fun AccountsSettings(
    settings: Settings,
    onBack: () -> Unit,
    onStatus: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val changes by Accounts.changes.collectAsState()
    var linking by remember { mutableStateOf<String?>(null) }
    var clientIds by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    SectionHeader("Accounts and Security", onBack)

    Text(
        "Link an AniList or MyAnimeList account to keep bookmarks and watch progress on your " +
            "list. Signing in opens your browser; the token is stored only on this computer.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
    )
    Spacer(Modifier.height(10.dp))

    SwitchRow(
        title = "Sync with linked accounts",
        subtitle = "Push watched episodes and bookmarks to your lists",
        checked = settings.trackSync,
        onCheckedChange = { settings.trackSync = it },
    )

    SectionLabel("Lists")

    TrackingProvider.entries.forEach { provider ->
        val account = remember(changes, provider) { Accounts.get(provider) }
        val busy = linking == provider.id

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Icon(
                Icons.Outlined.AccountCircle,
                contentDescription = null,
                tint = if (account != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    provider.label,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    account?.let { "Signed in as ${it.username.ifBlank { "account" }}" } ?: "Not linked",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
            if (busy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else if (account != null) {
                Switch(
                    checked = account.enabled,
                    onCheckedChange = { enabled ->
                        Accounts.setEnabled(provider, enabled)
                        onStatus(if (enabled) "${provider.label} syncing on" else "${provider.label} syncing off")
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary),
                )
                Spacer(Modifier.width(6.dp))
                TextButton(
                    onClick = {
                        Accounts.remove(provider)
                        onStatus("Signed out of ${provider.label}")
                    },
                ) { Text("Sign out") }
            }
        }

        if (account == null) {
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = clientIds[provider.id].orEmpty(),
                onValueChange = { value -> clientIds = clientIds + (provider.id to value) },
                label = { Text("${provider.label} client ID") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 4.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                provider.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        val clientId = clientIds[provider.id].orEmpty().trim()
                        if (clientId.isEmpty()) {
                            onStatus("Enter your ${provider.label} client ID first")
                            return@Button
                        }
                        scope.launch {
                            linking = provider.id
                            onStatus("Waiting for ${provider.label} sign-in…")
                            val result = Accounts.signIn(provider, clientId)
                            linking = null
                            onStatus(
                                result.fold(
                                    onSuccess = { "Signed in to ${provider.label} as ${it.username}" },
                                    onFailure = { "${provider.label} sign-in failed: ${it.message}" },
                                ),
                            )
                        }
                    },
                    enabled = linking == null,
                ) { Text("Sign in") }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        runCatching { Desktop.getDesktop().browse(java.net.URI(provider.registerUrl)) }
                        onStatus("Opened ${provider.label} app settings in your browser")
                    },
                ) { Text("Create an app") }
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    SectionLabel("Security")
    Text(
        "Access tokens are kept in accounts.json in your CloudStream folder and are sent only " +
            "to the list you signed in to. Signing out deletes the token from this computer.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
    )
}
