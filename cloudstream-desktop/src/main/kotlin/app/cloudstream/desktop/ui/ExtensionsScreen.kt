package app.cloudstream.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import app.cloudstream.desktop.core.RepositoryPlugin
import app.cloudstream.desktop.core.RepositorySet
import app.cloudstream.desktop.core.Settings
import java.awt.Desktop

/**
 * Settings → Extensions. The repositories list mirrors the original app: one row per
 * repository with its name and url, a delete button, and an "Add repository" button.
 */
@Composable
fun ExtensionsScreen(
    settings: Settings,
    extensions: ExtensionLoadResult,
    repos: RepositorySet?,
    repoLoading: Boolean,
    repoError: String?,
    repoQuery: String,
    onQueryChange: (String) -> Unit,
    installing: String?,
    onReload: () -> Unit,
    onInstallFile: () -> Unit,
    onRefreshRepository: () -> Unit,
    onInstallRepository: (RepositoryPlugin) -> Unit,
    onStatus: (String) -> Unit,
) {
    var showAddRepository by remember { mutableStateOf(false) }

    Column {
        Text("Extensions", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(
            "The CloudStream extension repositories: the same .cs3 archives the Android app " +
                "uses, verified against the repository's published hashes and translated to run " +
                "on the JVM. Desktop .jar extensions implementing Catalog work too.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(14.dp))

        // ---------------------------------------------------------------- repositories
        SectionLabel("Repositories")
        settings.repositoryUrls.forEach { url ->
            val state = repos?.repositories?.firstOrNull { it.url == url }
            val failure = repos?.failures?.firstOrNull { it.first == url }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        state?.name ?: url,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                    Text(
                        failure?.let { "${url} — ${it.second}" } ?: url,
                        color = if (failure != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
                IconButton(
                    onClick = {
                        settings.repositoryUrls = settings.repositoryUrls - url
                        onStatus("Repository removed")
                        onRefreshRepository()
                    },
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Remove repository",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Button(onClick = { showAddRepository = true }) { Text("+ Add repository") }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            Button(onClick = onInstallFile) { Text("Install file…") }
            Button(onClick = onReload) { Text("Reload") }
            Button(
                onClick = {
                    runCatching { Desktop.getDesktop().open(ExtensionHost.ensureExtensionsDir().toFile()) }
                },
            ) { Text("Open extensions folder") }
        }

        // ---------------------------------------------------------------- available
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Available extensions", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    repos == null -> "CloudStream repository"
                    else -> "${repos.repositories.size} repositories · ${repos.pluginCount} extensions"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
            Spacer(Modifier.width(10.dp))
            if (repoLoading) {
                CircularProgressIndicator(Modifier.width(14.dp).height(14.dp), strokeWidth = 2.dp)
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
            repoError != null -> Text(repoError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            repos == null && repoLoading -> Text("Loading repositories…", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("No extension matches \"$repoQuery\".", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(
                        Modifier.heightIn(max = 320.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                    ) {
                        filtered.forEach { state ->
                            item(key = "repo:${state.name}") {
                                Text(
                                    state.name,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        // ---------------------------------------------------------------- installed
        Spacer(Modifier.height(18.dp))
        Text("Installed", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))

        if (extensions.loaded.isEmpty()) {
            Text("No extensions installed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                items(extensions.loaded) { extension ->
                    Box(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Column {
                            Text(
                                extension.manifest.name ?: extension.catalog.name,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                buildString {
                                    append("v${extension.manifest.version ?: 0}")
                                    append(" · ")
                                    append(extension.file.fileName)
                                    if (extension.legacy) append(" · CloudStream")
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }

        if (extensions.errors.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("Failed to load:", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
            extensions.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
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
}

@Composable
internal fun RepositoryRow(
    plugin: RepositoryPlugin,
    repoName: String?,
    installedVersion: Int?,
    installing: Boolean,
    onInstall: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(plugin.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            val subtitle = buildString {
                append("v${plugin.version}")
                plugin.description?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                append(" · ").append(plugin.statusLabel)
                repoName?.let { append(" · ").append(it) }
            }
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Spacer(Modifier.width(10.dp))
        when {
            installing -> CircularProgressIndicator(Modifier.width(16.dp).height(16.dp), strokeWidth = 2.dp)
            installedVersion != null && installedVersion >= plugin.version -> Text(
                "Installed",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )

            installedVersion != null -> Button(onClick = onInstall) { Text("Update") }
            else -> Button(onClick = onInstall) { Text("Install") }
        }
    }
}
