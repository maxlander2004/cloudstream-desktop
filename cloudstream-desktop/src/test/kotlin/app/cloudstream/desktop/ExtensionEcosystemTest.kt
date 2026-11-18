package app.cloudstream.desktop

import app.cloudstream.desktop.core.ExtensionRepository
import app.cloudstream.desktop.core.legacy.LegacyExtensions
import com.lagradost.cloudstream3.MainAPI
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The extension ecosystem, checked on the schedule and never in a pull request.
 *
 * Every other test in this source set is offline by design — parsing, temp files, a jar built in
 * the test itself — so `test` stays hermetic. `TorrentSmokeTest` is tagged as well but stays
 * opt-in behind `TORRENT_SMOKE`, because it needs aria2 and a live magnet. That left
 * `networkTest`, and therefore the nightly's "Network-tagged tests" step, with nothing to run.
 * This file is what that step is for: the extension repository the Extensions screen installs
 * from, driven through the same code a fresh install uses.
 *
 * What it does not do is check somebody else's website. Browsing or searching a provider fails
 * when that provider's own upstream is down, which says nothing about this port — `verifyLegacy`
 * exists for that on demand. Here an archive has to be advertised, be served, match the hash the
 * repository publishes and load into this JVM; all of that is this project's promise.
 *
 * The sample is deliberately two archives rather than all of them: enough to prove the newest
 * advertised build still translates and links, small enough that the nightly stays quick.
 */
@Tag("network")
class ExtensionEcosystemTest {

    @Test
    fun `the official repository still advertises extensions an install can use`() = runBlocking {
        val set = ExtensionRepository.fetchAll(listOf(ExtensionRepository.OFFICIAL_REPOSITORY))

        assertTrue(
            set.failures.isEmpty(),
            "the official repository did not resolve: ${set.failures}",
        )
        assertTrue(
            set.pluginCount > 0,
            "the official repository parsed, but advertises no extensions: " +
                "a fresh install would show an empty Extensions screen",
        )

        val plugins = set.repositories.flatMap { it.plugins }
        for (plugin in plugins) {
            assertTrue(
                plugin.url.startsWith("https://"),
                "${plugin.internalName} is advertised over ${plugin.url}",
            )
            assertTrue(
                plugin.url.endsWith(".cs3"),
                "${plugin.internalName} is not a .cs3 archive: ${plugin.url}",
            )
            // install() verifies this hash before moving a download into place; without it a
            // truncated or replaced archive is installed silently.
            assertTrue(
                !plugin.fileHash.isNullOrBlank(),
                "${plugin.internalName} publishes no fileHash, so a tampered download would install",
            )
        }

        println("official repository: ${plugins.size} extension(s): ${plugins.joinToString { it.internalName }}")
    }

    @Test
    fun `a bounded sample of advertised extensions still installs and loads`() {
        val set = runBlocking { ExtensionRepository.fetchAll(listOf(ExtensionRepository.OFFICIAL_REPOSITORY)) }
        val plugins = set.repositories.flatMap { it.plugins }

        val sample = SAMPLED.map { name ->
            plugins.firstOrNull { it.internalName == name }
                ?: error("$name is no longer advertised by the official repository")
        }

        val dir = Files.createTempDirectory("cs-ecosystem")
        try {
            for (plugin in sample) {
                // Downloads the published archive and checks it against the published sha256.
                val archive = runBlocking { ExtensionRepository.install(plugin, dir) }
                val load = LegacyExtensions.load(archive)

                assertTrue(
                    load.providers.isNotEmpty(),
                    "${plugin.internalName} loaded but registered no providers",
                )
                for (provider in load.providers) {
                    assertTrue(
                        MainAPI::class.java.isAssignableFrom(provider.javaClass),
                        "${provider.name} is not a MainAPI: ${provider.javaClass.name}",
                    )
                    // The provider's class has to come from this archive's translated jar; a
                    // provider found on the application's own classpath would prove nothing.
                    assertTrue(
                        provider.javaClass.classLoader === load.classLoader,
                        "${provider.javaClass.name} was not defined by ${archive.fileName}",
                    )
                }
                println("${plugin.internalName} v${plugin.version}: ${load.providers.size} provider(s)")
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private companion object {
        /** Two of the archives the official repository publishes, kept small on purpose. */
        val SAMPLED = listOf("DailymotionProvider", "TwitchProvider")
    }
}
