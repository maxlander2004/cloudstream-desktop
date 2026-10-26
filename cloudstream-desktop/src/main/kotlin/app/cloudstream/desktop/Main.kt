package app.cloudstream.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.cloudstream.desktop.core.DownloadManager
import app.cloudstream.desktop.core.DownloadState
import app.cloudstream.desktop.core.DownloadTask
import app.cloudstream.desktop.ui.App
import java.awt.Dimension
import androidx.compose.runtime.*

/** The window title mirrors the queue: what is downloading, or how much is held up. */
internal fun computeTitle(tasks: List<DownloadTask>): String {
    val running = tasks.count { it.state == DownloadState.RUNNING }
    val queued = tasks.count { it.state == DownloadState.QUEUED }
    val paused = tasks.count { it.state == DownloadState.PAUSED }
    if (running > 0) {
        val task = tasks.first { it.state == DownloadState.RUNNING }
        val percent = if (task.totalBytes > 0) {
            (task.doneBytes.toDouble() / task.totalBytes * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }
        return "CloudStream - downloading ${task.title} ($percent%)"
    }
    if (paused > 0) return "CloudStream - $paused paused"
    if (queued > 0) return "CloudStream - $queued queued"
    return "CloudStream"
}

fun main() = application {
    var title by remember { mutableStateOf("CloudStream") }
    LaunchedEffect(Unit) {
        DownloadManager.tasks.collect { tasks -> title = computeTitle(tasks) }
    }

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
