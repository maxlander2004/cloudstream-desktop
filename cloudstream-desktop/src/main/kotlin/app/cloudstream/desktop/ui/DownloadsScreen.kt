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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Link
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cloudstream.desktop.api.StreamLink
import app.cloudstream.desktop.core.BatchProgress
import app.cloudstream.desktop.core.DownloadManager
import app.cloudstream.desktop.core.DownloadState
import app.cloudstream.desktop.core.DownloadTask
import app.cloudstream.desktop.core.Player
import app.cloudstream.desktop.core.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
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
fun DownloadsScreen(
    settings: Settings,
    onStatus: (String) -> Unit,
    onPlayFile: (String, String) -> Unit,
) {
    val tasks by DownloadManager.tasks.collectAsState()
    var usage by remember { mutableStateOf<DownloadManager.Usage?>(null) }
    var showNetworkStream by remember { mutableStateOf(false) }
    val rows = queueRows(tasks)
    // Batches start expanded; the header folds their episodes away for a one-job view.
    var collapsed by remember { mutableStateOf(emptySet<String>()) }

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
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is DownloadsRow.Single -> DownloadRow(
                            task = row.task,
                            onPlay = {
                                onPlayFile(
                                    row.task.title,
                                    Paths.get(settings.downloadsDir, row.task.fileName).toString(),
                                )
                            },
                            onDelete = {
                                DownloadManager.remove(row.task.id, Paths.get(settings.downloadsDir))
                                onStatus("Removed ${row.task.title}")
                            },
                            onStatus = onStatus,
                        )

                        is DownloadsRow.Batch -> BatchDownloadRow(
                            group = row.group,
                            tasks = row.tasks,
                            expanded = row.key !in collapsed,
                            onToggle = {
                                collapsed = if (row.key in collapsed) {
                                    collapsed - row.key
                                } else {
                                    collapsed + row.key
                                }
                            },
                            onRetryFailed = {
                                val retried = DownloadManager.retryFailed(settings, row.group)
                                onStatus("Retrying $retried episode(s) of ${row.group}")
                            },
                            downloadsDir = Paths.get(settings.downloadsDir),
                            onPlayFile = onPlayFile,
                            onStatus = onStatus,
                        )
                    }
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
                        if (target.isNotBlank()) onPlayFile("Network stream", target)
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
    onDelete: () -> Unit,
    onStatus: (String) -> Unit,
    background: Color = MaterialTheme.colorScheme.surface,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
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
                    DownloadState.DONE -> "${task.fileName} · ${formatBytes(task.totalBytes)}" +
                        when (task.subtitles.size) {
                            0 -> ""
                            1 -> " · 1 subtitle"
                            else -> " · ${task.subtitles.size} subtitles"
                        }
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
            if (task.state == DownloadState.RUNNING || task.state == DownloadState.QUEUED) {
                TextButton(
                    onClick = {
                        DownloadManager.cancel(task.id)
                        onStatus("Cancelled ${task.title}")
                    },
                ) { Text("Cancel") }
            }
            if (task.state == DownloadState.DONE) {
                TextButton(onClick = onPlay) { Text("Play") }
            }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
        if (task.state == DownloadState.RUNNING) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { task.progress },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
            )
        }
    }
}

/** One entry of the queue: a plain download, or a whole batch folded into a single job. */
internal sealed interface DownloadsRow {
    val key: String

    data class Single(val task: DownloadTask) : DownloadsRow {
        override val key: String get() = task.id
    }

    data class Batch(val group: String, val tasks: List<DownloadTask>) : DownloadsRow {
        override val key: String get() = "group:$group"
    }
}

/**
 * Tasks in queue order, with each batch collapsed into one row where its first episode landed,
 * so a 24-episode show is one job instead of 24 rows among the single downloads.
 */
internal fun queueRows(tasks: List<DownloadTask>): List<DownloadsRow> {
    val seen = mutableSetOf<String>()
    val rows = mutableListOf<DownloadsRow>()
    tasks.forEach { task ->
        val group = task.group
        when {
            group == null -> rows += DownloadsRow.Single(task)
            seen.add(group) -> rows += DownloadsRow.Batch(
                group = group,
                tasks = tasks.filter { it.group == group },
            )
        }
    }
    return rows
}

/** "7 of 24 done · 2 downloading · 1 failed": the batch header's one-line summary. */
internal fun batchDetail(progress: BatchProgress): String {
    val parts = mutableListOf("${progress.done + progress.failed} of ${progress.total} done")
    if (progress.running > 0) parts += "${progress.running} downloading"
    if (progress.queued > 0) parts += "${progress.queued} queued"
    if (progress.failed > 0) parts += "${progress.failed} failed"
    return parts.joinToString(" · ")
}

/**
 * A season or a whole show as one job: a single bar over every episode, one Cancel for the ones
 * still running, and the per-episode rows folded underneath.
 */
@Composable
private fun BatchDownloadRow(
    group: String,
    tasks: List<DownloadTask>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRetryFailed: () -> Unit,
    downloadsDir: Path,
    onPlayFile: (String, String) -> Unit,
    onStatus: (String) -> Unit,
) {
    val progress = BatchProgress.of(tasks)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggle, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Hide episodes" else "Show episodes",
                )
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    group,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                )
                Text(
                    batchDetail(progress),
                    color = if (progress.failed > 0 && progress.settled) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 11.sp,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
        )
        // Actions get their own row: a 24-episode batch can show all three at once.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            if (progress.failed > 0) {
                TextButton(onClick = onRetryFailed) {
                    Text("Retry failed (${progress.failed})")
                }
            }
            if (!progress.settled) {
                TextButton(
                    onClick = {
                        DownloadManager.cancelGroup(group)
                        onStatus("Cancelled $group")
                    },
                ) { Text("Cancel all") }
            }
            TextButton(
                onClick = {
                    DownloadManager.removeGroup(group, downloadsDir)
                    onStatus("Removed $group")
                },
            ) { Text("Delete all") }
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            tasks.forEach { task ->
                DownloadRow(
                    task = task,
                    onPlay = {
                        onPlayFile(task.title, downloadsDir.resolve(task.fileName).toString())
                    },
                    onDelete = {
                        DownloadManager.remove(task.id, downloadsDir)
                        onStatus("Removed ${task.title}")
                    },
                    onStatus = onStatus,
                    background = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
