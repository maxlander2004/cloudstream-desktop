package app.cloudstream.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density

/**
 * Renders one frame of the Material3 chrome the app is built from, with no display attached.
 *
 * The Windows installers shipped twice with a launcher that died before the first window:
 * first `NoClassDefFoundError: java/net/http/HttpClient` (jlink module set) and then
 * `NoSuchMethodError: WindowInsets$_notMobileKt.getSystemBars` (material3 built against an
 * older foundation-layout than the one Gradle resolves). Both were linkage failures that only
 * appear once the classes actually run against the packaged classpath, so neither `compileKotlin`
 * nor the unit tests noticed. This scene exercises the same composition path — insets, Scaffold,
 * NavigationRail, TopAppBar — headlessly, so `verifyRuntime` can run it against the real
 * distributable and fail the build instead of the user's launch.
 */
fun main() {
    val scene = ImageComposeScene(
        width = 1280,
        height = 800,
        density = Density(1f),
    ) {
        MaterialTheme {
            Scaffold(
                topBar = { TopAppBar(title = { Text("CloudStream") }) },
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(selected = true, onClick = {}, icon = {}, label = { Text("Home") })
                    }
                },
            ) { padding ->
                Row(androidx.compose.ui.Modifier.padding(padding)) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                        NavigationRailItem(selected = true, onClick = {}, icon = {}, label = { Text("Home") })
                    }
                    Column {
                        Text("Rendered without a display")
                        Button(onClick = {}) { Text("Play") }
                    }
                }
            }
        }
    }

    val image = scene.render()
    println("headless-render ok: ${image.width}x${image.height}")
    scene.close()
}
