package app.cloudstream.desktop.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudstream.desktop.api.StreamLink
import app.cloudstream.desktop.core.DownloadManager
import app.cloudstream.desktop.core.DownloadState
import app.cloudstream.desktop.core.DownloadTask
import app.cloudstream.desktop.core.Player
import app.cloudstream.desktop.core.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Paths

/** Human-readable byte count, e.g. "1.4 GB". */
internal fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "0 B"
    bytes >= 1L shl 30 -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1L shl 20 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
    bytes >= 1L shl 10 -> String.format("%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

/**
 * The original's Downloads screen: internal-storage bar at the top, the download queue below,
 * and a Network stream button that plays any URL directly.
 */
@Composable
fun DownloadsScreen(settings: Settings, onStatus: (String) -> Unit) {
    val tasks by DownloadManager.tasks.collectAsState()
    var usage by remember { mutableStateOf<DownloadManager.Usage?>(null) }
    var showNetworkStream by remember { mutableStateOf(false) }

    // Refresh the storage bar whenever the folder or the finished downloads change.
    LaunchedEffect(settings.downloadsDir, tasks.count { it.state == DownloadState.DONE }) {
        usage = withContext(Dispatchers.IO) { DownloadManager.usage(Paths.get(settings.downloadsDir)) }
    }

    Column(Modifier.fillMaxSize()) {
        Text("Downloads", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(12.dp))

        Text("Internal Storage", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        val current = usage
        val fraction = if (current != null && current.diskTotal > 0) {
            (current.diskUsed.toFloat() / current.diskTotal).coerceIn(0f, 1f)
        } else {
            0f
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.height(6.dp))
        Row {
            LegendDot(MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Used • ${formatBytes(current?.diskUsed ?: 0)}    " +
                    "App • ${formatBytes(current?.folderBytes ?: 0)}    " +
                    "Free • ${formatBytes(current?.diskFree ?: 0)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }

        Spacer(Modifier.height(14.dp))

        if (tasks.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("There are currently no downloads.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text("Download queue", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                items(tasks, key = { it.id }) { task ->
                    DownloadRow(
                        task = task,
                        onPlay = {
                            onStatus(Player.open(StreamLink(task.title, task.url), settings.playerCommand))
                        },
                        onPause = { DownloadManager.pause(task.id) },
                        onResume = { DownloadManager.resume(task.id) },
                        onCancel = { DownloadManager.cancel(task.id) },
                        onRetry = { DownloadManager.retry(task.id, settings) },
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (tasks.isNotEmpty()) {
                TextButton(onClick = { DownloadManager.clearFinished() }) { Text("Clear finished") }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = { showNetworkStream = true }) {
                Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.width(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Network stream")
            }
        }
    }

    if (showNetworkStream) {
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNetworkStream = false },
            title = { Text("Network stream") },
            text = {
                Column {
                    Text(
                        "Play a video URL directly in your configured player.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        placeholder = { Text("https://host/video.mp4") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = url.trim()
                        if (target.isNotBlank()) {
                            onStatus(Player.open(StreamLink("Network stream", target), settings.playerCommand))
                        }
                        showNetworkStream = false
                    },
                ) { Text("Play") }
            },
            dismissButton = { TextButton(onClick = { showNetworkStream = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier.padding(end = 6.dp).size(10.dp).clip(RoundedCornerShape(3.dp)).background(color),
    )
}

@Composable
private fun DownloadRow(
    task: DownloadTask,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val inFlight = task.state == DownloadState.QUEUED ||
        task.state == DownloadState.RUNNING ||
        task.state == DownloadState.PAUSED
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(task.title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                val detail = when (task.state) {
                    DownloadState.QUEUED -> task.fileName
                    DownloadState.RUNNING ->
                        "${task.fileName} · ${formatBytes(task.doneBytes)}" +
                            (if (task.totalBytes > 0) " / ${formatBytes(task.totalBytes)}" else "")
                    DownloadState.PAUSED ->
                        "${task.fileName} · Paused" +
                            if (task.totalBytes > 0) {
                                " · ${formatBytes(task.doneBytes)} / ${formatBytes(task.totalBytes)}"
                            } else {
                                " · ${formatBytes(task.doneBytes)}"
                            }
                    DownloadState.DONE -> "${task.fileName} · ${formatBytes(task.totalBytes)}"
                    DownloadState.FAILED -> task.error ?: "Download failed"
                }
                Text(
                    detail,
                    color = if (task.state == DownloadState.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 11.sp,
                )
            }
            if (task.state == DownloadState.DONE) {
                TextButton(onClick = onPlay) { Text("Play") }
            }
            // Pause/resume on the left of the stop button, like the original's queue row.
            if (inFlight) {
                IconButton(onClick = if (task.state == DownloadState.PAUSED) onResume else onPause) {
                    Icon(
                        if (task.state == DownloadState.PAUSED) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                        contentDescription = if (task.state == DownloadState.PAUSED) "Resume" else "Pause",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = if (inFlight) "Cancel download" else "Remove from queue",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Retry button for failed downloads.
            if (task.state == DownloadState.FAILED) {
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
        // A paused download keeps its bar, so the progress it will resume from stays visible.
        if (task.state == DownloadState.RUNNING || task.state == DownloadState.PAUSED) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { task.progress },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
            )
        }
    }
}
