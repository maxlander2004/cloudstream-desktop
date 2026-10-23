package app.cloudstream.desktop

import app.cloudstream.desktop.api.Catalog
import app.cloudstream.desktop.api.CatalogItem
import app.cloudstream.desktop.api.StreamLink
import app.cloudstream.desktop.core.ExtensionHost
import app.cloudstream.desktop.core.IptvSource
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stand-in extension used by [ExtensionHostTest]; packaged into a jar at test time. */
class FakeCatalog : Catalog {
    override val name: String = "Fake"

    override suspend fun browse(): List<CatalogItem> = listOf(CatalogItem("Hello", "https://example.com/a"))

    override suspend fun streams(item: CatalogItem): List<StreamLink> =
        listOf(StreamLink("direct", item.url))
}

class IptvSourceTest {
    private val source = IptvSource()

    @Test
    fun `parses channels with logos and bare channels`() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="One.uk" tvg-logo="https://example.com/one.png" group-title="News",One News
            https://example.com/one.m3u8
            #EXTINF:-1,Two Channel
            https://example.com/two.m3u8
        """.trimIndent()

        val items = source.parse(playlist)

        assertEquals(2, items.size)
        assertEquals("One News", items[0].title)
        assertEquals("https://example.com/one.m3u8", items[0].url)
        assertEquals("https://example.com/one.png", items[0].posterUrl)
        assertEquals("Two Channel", items[1].title)
        assertNull(items[1].posterUrl)
    }

    @Test
    fun `skips metadata lines and entries without a stream url`() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1,Orphan Without Url
            #EXTINF:-1 tvg-logo="",Valid
            https://example.com/valid.m3u8
        """.trimIndent()

        val items = source.parse(playlist)

        assertEquals(1, items.size)
        assertEquals("Valid", items[0].title)
        assertNull(items[0].posterUrl)
    }
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
