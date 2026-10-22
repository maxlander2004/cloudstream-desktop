package app.cloudstream.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.cloudstream.desktop.ui.App
import app.cloudstream.desktop.ui.CloudStreamTheme
import java.awt.Dimension

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "CloudStream",
        state = rememberWindowState(size = DpSize(1180.dp, 760.dp)),
    ) {
        window.minimumSize = Dimension(900, 600)
        CloudStreamTheme {
            App()
        }
    }
}
