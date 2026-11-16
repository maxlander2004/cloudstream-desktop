package app.cloudstream.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.cloudstream.desktop.core.ExtensionLoadResult
import app.cloudstream.desktop.core.RepositoryPlugin
import app.cloudstream.desktop.core.RepositorySet
import app.cloudstream.desktop.core.RepositoryState
import app.cloudstream.desktop.core.Settings
import app.cloudstream.desktop.ui.CloudStreamTheme
import app.cloudstream.desktop.ui.ExtensionsScreen
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Settings → Extensions navigation, driven the way a user drives it: open a repository, go back
 * to the repository list, then go back out to the settings list.
 *
 * Compose renders these offscreen, so no window is needed — but Skiko still wants a native
 * library and a resolvable font. On a machine without either (a bare container), the tests are
 * skipped instead of failing, since that says nothing about this screen.
 */
class ExtensionsFlowTest {
    private val repository = RepositoryState(
        name = "Test Repo",
        description = "one extension",
        plugins = listOf(
            RepositoryPlugin(
                url = "https://example.invalid/Alpha.cs3",
                name = "Alpha Provider",
                internalName = "Alpha",
            ),
        ),
        url = "https://example.invalid/repo.json",
    )

    @BeforeEach
    fun requireRendering() {
        val usable = runCatching {
            // Loading a raster surface proves the Skiko native library is there; at least one
            // installed family proves Compose can resolve the fonts its text styles ask for.
            org.jetbrains.skia.Surface.makeRasterN32Premul(4, 4).close()
            org.jetbrains.skia.FontMgr.default.familiesCount > 0
        }.getOrDefault(false)
        assumeTrue(usable, "Compose cannot render here (no Skiko native library or no fonts)")
    }

    @OptIn(ExperimentalTestApi::class)
    private fun androidx.compose.ui.test.ComposeUiTest.openExtensions(onBack: () -> Unit) {
        setContent {
            CloudStreamTheme {
                ExtensionsScreen(
                    settings = Settings(),
                    extensions = ExtensionLoadResult(),
                    installed = emptyList(),
                    repos = RepositorySet(listOf(repository)),
                    repoLoading = false,
                    repoError = null,
                    repoQuery = "",
                    onQueryChange = {},
                    installing = null,
                    onReload = {},
                    onInstallFile = {},
                    onInstallUrl = {},
                    onRefreshRepository = {},
                    onUpdateAll = {},
                    onInstallRepository = {},
                    onUninstall = { _ -> },
                    onToggleEnabled = { _, _ -> },
                    onStatus = {},
                    onBack = onBack,
                )
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `back leaves a repository before leaving the extensions screen`() = runComposeUiTest {
        var leftExtensions = 0
        openExtensions { leftExtensions++ }

        // Root: the repository list, with the add button docked bottom-end like the original.
        onNodeWithText("Test Repo").assertExists()
        onNodeWithText("Add repository", useUnmergedTree = true).assertExists()
        assertEquals(0, leftExtensions)

        // Into the repository: its plugins replace the repository list.
        onNodeWithText("Test Repo").performClick()
        onNodeWithText("Alpha Provider").assertExists()
        assertEquals(
            0,
            onAllNodesWithText("Add repository", useUnmergedTree = true).fetchSemanticsNodes().size,
            "the add button belongs to the repository list only",
        )

        // First back returns to the repository list, not out of Extensions.
        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("Test Repo").assertExists()
        assertEquals(0, leftExtensions)

        // Second back leaves Extensions for the settings list.
        onNodeWithContentDescription("Back").performClick()
        assertEquals(1, leftExtensions)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `the same back arrow leaves the installed pane`() = runComposeUiTest {
        var leftExtensions = 0
        openExtensions { leftExtensions++ }

        onNodeWithContentDescription("More options").performClick()
        onNodeWithText("Installed extensions").performClick()
        onNodeWithText("Installed extensions").assertExists()

        onNodeWithContentDescription("Back").performClick()
        onNodeWithText("Test Repo").assertExists()
        assertEquals(0, leftExtensions)
    }
}
