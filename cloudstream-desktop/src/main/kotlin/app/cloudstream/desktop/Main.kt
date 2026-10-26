package app.cloudstream.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.cloudstream.desktop.core.DownloadManager
import app.cloudstream.desktop.core.DownloadState
import app.cloudstream.desktop.ui.App
import java.awt.Dimension
import androidx.compose.runtime.*

fun main() = application {
    var title by remember { mutableStateOf("CloudStream") }
    val tasks by remember { mutableStateOf<List<DownloadManager.DownloadTask>>(emptyList()) }
    LaunchedEffect(Unit) {
        tasks = DownloadManager.tasks.value
        DownloadManager.tasks.collect { newTasks ->
            if (tasks != newTasks) {
                tasks = newTasks
                title = computeTitle(newTasks)
            }
        }
    }

    fun computeTitle(tasks: List<DownloadManager.DownloadTask>): String {
        val running = tasks.count { it.state == DownloadState.RUNNING }
        val queued = tasks.count { it.state == DownloadState.QUEUED }
        val paused = tasks.count { it.state == DownloadState.PAUSED }
        if (running > 0) {
            val task = tasks.first { it.state == DownloadState.RUNNING }
            val percent = if (task.totalBytes > 0) (task.doneBytes.toFloat() / task.totalBytes * 100).coerceIn(0f, 100f).toInt() else 0
            return "CloudStream - downloading ${task.title} ($percent%)"
        } else if (paused > 0) {
            return "CloudStream - ${paused} paused"
        } else if (queued > 0) {
            return "CloudStream - ${queued} queued"
        }
        return "CloudStream"
    }

    // Restore previously saved download queue on startup.
    DownloadManager.restoreOnStartup(Settings())

    Window(
        onCloseRequest = ::exitApplication,
        title = title,
        state = rememberWindowState(size = DpSize(1180.dp, 760.dp)),
    ) {
        window.minimumSize = Dimension(900, 600)
        // App() applies CloudStreamTheme itself so Layout → App theme can switch it live.
        App()
    }
}
