package app.cloudstream.desktop

import app.cloudstream.desktop.api.Catalog
import app.cloudstream.desktop.api.CatalogItem
import app.cloudstream.desktop.api.StreamLink
import app.cloudstream.desktop.core.AppUpdates
import app.cloudstream.desktop.core.Backup
import app.cloudstream.desktop.core.DownloadManager
import app.cloudstream.desktop.core.ExtensionHost
import app.cloudstream.desktop.core.Settings
import app.cloudstream.desktop.core.bestDownloadLink
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stand-in extension used by [ExtensionHostTest]; packaged into a jar at test time. */
class FakeCatalog : Catalog {
    override val name: String = "Fake"

    override suspend fun browse(): List<CatalogItem> = listOf(CatalogItem("Hello", "https://example.com/a"))

    override suspend fun streams(item: CatalogItem): List<StreamLink> =
        listOf(StreamLink("direct", item.url))
}

class ExtensionHostTest {
    @Test
    fun `loads a jar extension from its manifest`() {
        withTempHome {
            val dir = ExtensionHost.ensureExtensionsDir()
            writeJar(
                dir.resolve("fake.jar"),
                manifest = """{"name":"Fake Source","version":7,"pluginClassName":"app.cloudstream.desktop.FakeCatalog"}""",
            )

            val result = ExtensionHost.loadAll()

            assertTrue(result.errors.isEmpty(), "unexpected errors: ${result.errors}")
            assertEquals(1, result.loaded.size)
            val extension = result.loaded.single()
            assertEquals("Fake Source", extension.manifest.name)
            assertEquals(7, extension.manifest.version)
            assertEquals("Fake", extension.catalog.name)
        }
    }

    @Test
    fun `reports an archive that has no manifest`() {
        withTempHome {
            val dir = ExtensionHost.ensureExtensionsDir()
            writeJar(dir.resolve("broken.jar"), manifest = null)

            val result = ExtensionHost.loadAll()

            assertTrue(result.loaded.isEmpty())
            assertEquals(1, result.errors.size)
            assertTrue(result.errors.single().startsWith("broken.jar"))
        }
    }

    @Test
    fun `installs a jar from outside the extensions folder`() {
        withTempHome {
            val download = Files.createTempDirectory("cs-download")
            val source = download.resolve("downloaded.jar")
            writeJar(
                source,
                manifest = """{"name":"Fake Source","version":2,"pluginClassName":"app.cloudstream.desktop.FakeCatalog"}""",
            )

            val installed = ExtensionHost.install(source).getOrThrow()

            assertEquals("Fake Source", installed.manifest.name)
            assertEquals(
                ExtensionHost.extensionsDir().resolve("downloaded.jar"),
                installed.file,
            )
            assertEquals(1, ExtensionHost.loadAll().loaded.size)
        }
    }

    @Test
    fun `rejects an archive without a manifest and removes the copy`() {
        withTempHome {
            val dir = ExtensionHost.ensureExtensionsDir()
            val source = Files.createTempDirectory("cs-download").resolve("broken.jar")
            writeJar(source, manifest = null)

            val result = ExtensionHost.install(source)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()!!.message!!.contains("manifest.json"))
            assertTrue(Files.notExists(dir.resolve("broken.jar")))
        }
    }

    @Test
    fun `accepts a cloudstream archive`() {
        withTempHome {
            val source = Files.createTempDirectory("cs-download").resolve("legacy.cs3")
            java.util.zip.ZipOutputStream(Files.newOutputStream(source)).use { out ->
                out.putNextEntry(JarEntry("manifest.json"))
                out.write("""{"name":"Legacy","version":1,"pluginClassName":"x.Legacy"}""".toByteArray())
                out.closeEntry()
            }

            val result = ExtensionHost.install(source)

            // It is accepted as an extension, so it gets as far as being loaded and fails on
            // the missing code rather than on its file type.
            assertTrue(result.isFailure)
            assertTrue(
                result.exceptionOrNull()!!.message!!.contains("classes.dex"),
                result.exceptionOrNull()!!.message,
            )
        }
    }

    @Test
    fun `describes an installed archive without loading it`() {
        withTempHome {
            val dir = ExtensionHost.ensureExtensionsDir()
            val jar = dir.resolve("fake.jar")
            writeJar(
                jar,
                manifest = """{"name":"Fake Source","version":4,"pluginClassName":"app.cloudstream.desktop.FakeCatalog"}""",
            )

            val archive = ExtensionHost.installed().single()

            assertEquals("Fake Source", archive.name)
            assertEquals(4, archive.version)
            assertTrue(archive.enabled)
            assertEquals(jar, archive.file)
        }
    }

    @Test
    fun `disables and re-enables an extension`() {
        withTempHome {
            val dir = ExtensionHost.ensureExtensionsDir()
            val jar = dir.resolve("fake.jar")
            writeJar(
                jar,
                manifest = """{"name":"Fake Source","version":1,"pluginClassName":"app.cloudstream.desktop.FakeCatalog"}""",
            )
            assertEquals(1, ExtensionHost.loadAll().loaded.size)

            ExtensionHost.setEnabled(jar, false)

            assertTrue(ExtensionHost.loadAll().loaded.isEmpty())
            assertFalse(ExtensionHost.installed().single().enabled)

            ExtensionHost.setEnabled(jar, true)

            assertEquals(1, ExtensionHost.loadAll().loaded.size)
            assertTrue(ExtensionHost.installed().single().enabled)
        }
    }

    @Test
    fun `uninstalls an extension and drops its providers`() {
        withTempHome {
            val dir = ExtensionHost.ensureExtensionsDir()
            val jar = dir.resolve("fake.jar")
            writeJar(
                jar,
                manifest = """{"name":"Fake Source","version":1,"pluginClassName":"app.cloudstream.desktop.FakeCatalog"}""",
            )
            assertEquals(1, ExtensionHost.loadAll().loaded.size)

            ExtensionHost.uninstall(jar).getOrThrow()

            assertTrue(Files.notExists(jar))
            assertTrue(ExtensionHost.installed().isEmpty())
            assertTrue(ExtensionHost.loadAll().loaded.isEmpty())
        }
    }

    private fun withTempHome(block: () -> Unit) {
        val previous = System.getProperty("user.home")
        System.setProperty("user.home", Files.createTempDirectory("cs-desktop-home").toString())
        try {
            block()
        } finally {
            System.setProperty("user.home", previous)
        }
    }

    private fun writeJar(target: java.nio.file.Path, manifest: String?) {
        val classBytes = FakeCatalog::class.java.classLoader
            .getResourceAsStream("app/cloudstream/desktop/FakeCatalog.class")
            ?.readBytes()
            ?: error("FakeCatalog.class not found on the test classpath")

        JarOutputStream(Files.newOutputStream(target)).use { out ->
            if (manifest != null) {
                out.putNextEntry(JarEntry("manifest.json"))
                out.write(manifest.toByteArray())
                out.closeEntry()
            }
            out.putNextEntry(JarEntry("app/cloudstream/desktop/FakeCatalog.class"))
            out.write(classBytes)
            out.closeEntry()
        }
    }
}

class AppUpdateTest {
    @Test
    fun `compares versions segment by segment`() {
        assertTrue(AppUpdates.isNewer("1.2.0", "1.1.0"))
        assertTrue(AppUpdates.isNewer("1.1.1", "1.1.0"))
        assertTrue(AppUpdates.isNewer("2.0.0", "1.9.9"))
        assertFalse(AppUpdates.isNewer("1.1.0", "1.1.0"))
        assertFalse(AppUpdates.isNewer("1.0.9", "1.1.0"))
    }
}

class DownloadNamingTest {
    @Test
    fun `uses the file name from the url when it has an extension`() {
        assertEquals(
            "movie.mp4",
            DownloadManager.guessFileName("Some Title", "https://cdn.example/path/movie.mp4?token=1"),
        )
    }

    @Test
    fun `falls back to a cleaned title when the url has no usable name`() {
        assertEquals(
            "Some_ Title.mp4",
            DownloadManager.guessFileName("Some: Title?", "https://cdn.example/stream"),
        )
    }

    @Test
    fun `defaults to mp4 when the url carries no extension`() {
        assertEquals(
            "Nice_ Title.mp4",
            DownloadManager.guessFileName("Nice: Title", "https://cdn.example/v/playlist?id=9"),
        )
    }
}

/** The Updates and Backup settings' new keys and the backup round trip. */
class SettingsBackupTest {
    private fun withTempHome(block: (java.nio.file.Path) -> Unit) {
        val previous = System.getProperty("user.home")
        System.setProperty("user.home", Files.createTempDirectory("cs-settings-home").toString())
        try {
            block(java.nio.file.Paths.get(System.getProperty("user.home")))
        } finally {
            System.setProperty("user.home", previous)
        }
    }

    @Test
    fun `round-trips the parity settings through the properties file`() = withTempHome { _ ->
        val settings = Settings()
        settings.parallelDownloads = 7
        settings.concurrentConnections = 5
        settings.preferredMedia = listOf("movie", "anime")
        settings.providerLanguages = listOf("en", "de")
        settings.posterSize = 4
        settings.posterTitleUnder = false
        settings.advancedSearch = false
        settings.appTheme = "light"
        settings.nsfwEnabled = true
        settings.autoPluginUpdates = false
        settings.autoDownloadPlugins = true
        settings.githubProxy = true
        settings.cloneSite = "https://a.example=https://b.example"
        settings.doh = "cloudflare"

        val reloaded = Settings()
        assertEquals(7, reloaded.parallelDownloads)
        assertEquals(5, reloaded.concurrentConnections)
        assertEquals(listOf("MOVIE", "ANIME"), reloaded.preferredMedia)
        assertEquals(listOf("en", "de"), reloaded.providerLanguages)
        assertEquals(4, reloaded.posterSize)
        assertFalse(reloaded.posterTitleUnder)
        assertFalse(reloaded.advancedSearch)
        assertEquals("light", reloaded.appTheme)
        assertTrue(reloaded.nsfwEnabled)
        assertFalse(reloaded.autoPluginUpdates)
        assertTrue(reloaded.autoDownloadPlugins)
        assertTrue(reloaded.githubProxy)
        assertEquals("https://a.example=https://b.example", reloaded.cloneSite)
        assertEquals("cloudflare", reloaded.doh)
    }

    @Test
    fun `backup restores settings and extensions into a fresh home`() {
        withTempHome { _ ->
            val settings = Settings()
            settings.playerCommand = "mpv --fs"
            settings.parallelDownloads = 9
            val extensionsDir = ExtensionHost.ensureExtensionsDir()
            Files.writeString(extensionsDir.resolve("demo.cs3"), "not a real archive")

            val zip = Backup.create(
                Files.createTempDirectory("cs-backup").toString(),
                extensionsDir,
            )
            assertTrue(Files.isRegularFile(zip))

            // A different machine: empty home, then restore.
            val other = Files.createTempDirectory("cs-restore-home")
            val previous = System.getProperty("user.home")
            System.setProperty("user.home", other.toString())
            try {
                val summary = Backup.restore(
                    zip,
                    other.resolve(".cloudstream-desktop/extensions"),
                )
                assertTrue(summary.contains("Restored"), summary)
                assertEquals("mpv --fs", Settings().playerCommand)
                assertEquals(9, Settings().parallelDownloads)
                assertTrue(Files.isRegularFile(other.resolve(".cloudstream-desktop/extensions/demo.cs3")))
            } finally {
                System.setProperty("user.home", previous)
            }
        }
    }

    @Test
    fun `restore rejects a file that carries no backup data`() {
        val zip = Files.createTempFile("not-a-backup", ".zip")
        java.util.zip.ZipOutputStream(Files.newOutputStream(zip)).use { out ->
            out.putNextEntry(java.util.zip.ZipEntry("readme.txt"))
            out.write("nothing here".toByteArray())
            out.closeEntry()
        }
        val extensions = Files.createTempDirectory("cs-no-ext")

        val failure = kotlin.test.assertFailsWith<IllegalStateException> {
            Backup.restore(zip, extensions)
        }
        assertTrue(failure.message!!.contains("no CloudStream backup data"), failure.message)
    }
}

/** Which link a season's batch download picks for one episode. */
class DownloadSelectionTest {
    @Test
    fun `prefers a direct file over an hls playlist`() {
        val links = listOf(
            StreamLink("hls", "https://cdn.example/ep1/master.m3u8"),
            StreamLink(
                "direct",
                "https://cdn.example/ep1.mp4",
                headers = mapOf("Referer" to "https://example.com"),
            ),
        )

        val best = links.bestDownloadLink()

        assertEquals("https://cdn.example/ep1.mp4", best?.url)
        assertEquals(mapOf("Referer" to "https://example.com"), best?.headers)
    }

    @Test
    fun `falls back to the playlist when nothing else is offered`() {
        val links = listOf(StreamLink("hls", "https://cdn.example/live/index.m3u8?token=1"))

        assertEquals("https://cdn.example/live/index.m3u8?token=1", links.bestDownloadLink()?.url)
    }

    @Test
    fun `an episode without links has nothing to download`() {
        assertNull(emptyList<StreamLink>().bestDownloadLink())
    }
}
