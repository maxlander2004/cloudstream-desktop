package app.cloudstream.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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

// The original app also ships a light theme (Layout → App theme); these follow its palette.
private val CloudStreamLight = lightColorScheme(
    primary = CsPrimary,
    onPrimary = Color.White,
    secondary = CsAccent,
    onSecondary = Color.White,
    background = Color(0xFFF7F7F9),
    onBackground = Color(0xFF17181C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17181C),
    surfaceVariant = Color(0xFFE8E9EE),
    onSurfaceVariant = Color(0xFF5C626B),
    error = CsAccent,
)

/**
 * The media chips the original shows above the poster grid, in its order, as (the [ItemType]
 * name the filter uses, the label shown to the user). Upstream also has Asian Drama, Cartoons,
 * Documentaries, Torrent and Others; the desktop ItemType folds those into OTHER.
 */
val MediaTypeChips: List<Pair<String, String>> = listOf(
    "MOVIE" to "Movies",
    "SERIES" to "TV Series",
    "ANIME" to "Anime",
    "LIVE" to "Livestreams",
    "NSFW" to "NSFW",
)

/** Reads the "#RRGGBB" the UI settings store, falling back to the upstream brand blue. */
fun accentColor(value: String): Color =
    runCatching { Color(0xFF000000L or value.removePrefix("#").toLong(16)) }
        .getOrDefault(CsPrimary)

/**
 * Applies the stored accent color over the upstream dark/light palettes, the way the original's
 * UI → Primary color setting recolors the app.
 */
@Composable
fun CloudStreamTheme(
    dark: Boolean = true,
    accent: Color = CsPrimary,
    content: @Composable () -> Unit,
) {
    val base = if (dark) CloudStreamDark else CloudStreamLight
    MaterialTheme(
        colorScheme = base.copy(primary = accent),
        content = content,
    )
}
