package app.cloudstream.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Colors lifted from the upstream palette (shared/src/commonMain/.../theme/Palette.kt)
// so the desktop build matches the Android app's dark theme.
val CsPrimary = Color(0xFF3D50FA)
val CsBackground = Color(0xFF111111)
val CsSurface = Color(0xFF1C1C20)
val CsSurfaceVariant = Color(0xFF2B2C30)
val CsOnBackground = Color(0xFFE9EAEE)
val CsSecondaryText = Color(0xFF9BA0A4)
val CsAccent = Color(0xFFF53B66)

private val CloudStreamDark = darkColorScheme(
    primary = CsPrimary,
    onPrimary = Color.White,
    secondary = CsAccent,
    onSecondary = Color.White,
    background = CsBackground,
    onBackground = CsOnBackground,
    surface = CsSurface,
    onSurface = CsOnBackground,
    surfaceVariant = CsSurfaceVariant,
    onSurfaceVariant = CsSecondaryText,
    error = CsAccent,
)

@Composable
fun CloudStreamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CloudStreamDark, content = content)
}
