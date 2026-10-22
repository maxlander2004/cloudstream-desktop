package app.cloudstream.desktop

import app.cloudstream.desktop.core.ExtensionHost
import app.cloudstream.desktop.core.ExtensionRepository
import app.cloudstream.desktop.core.RepositoryManifest
import app.cloudstream.desktop.core.RepositoryPlugin
import app.cloudstream.desktop.core.RepositoryState
import app.cloudstream.desktop.core.Settings
import app.cloudstream.desktop.core.Player
import app.cloudstream.desktop.core.legacy.DexTranslator
import app.cloudstream.desktop.core.legacy.LegacyExtensions
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DexTranslatorTest {
    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().also { buffer ->
            ZipOutputStream(buffer).use { out ->
                for ((name, bytes) in entries) {
                    out.putNextEntry(ZipEntry(name))
                    out.write(bytes)
                    out.closeEntry()
                }
            }
        }.toByteArray()

    @Test
    fun `collects multi-dex archives in index order`() {
        val archive = zip(
            "classes2.dex" to "second".toByteArray(),
            "classes.dex" to "first".toByteArray(),
            "manifest.json" to "{}".toByteArray(),
        )

        val found = DexTranslator.dexEntries(archive).map { it.decodeToString() }

        assertEquals(listOf("first", "second"), found)
    }

    @Test
    fun `ignores non-dex entries`() {
        val archive = zip(
            "manifest.json" to "{\"name\":\"x\"}".toByteArray(),
            "resources/icon.png" to byteArrayOf(1, 2, 3),
        )

        assertTrue(DexTranslator.dexEntries(archive).isEmpty())
    }

    @Test
    fun `accepts a bare dex file`() {
        val raw = byteArrayOf('d'.code.toByte(), 'e'.code.toByte(), 'x'.code.toByte(), '\n'.code.toByte()) +
            "035\u0000payload".toByteArray()

        assertEquals(listOf(raw), DexTranslator.dexEntries(raw))
    }
}

class LegacyManifestTest {
    private fun archive(vararg entries: Pair<String, String>): Path {
        val dir = Files.createTempDirectory("cs3")
        val file = dir.resolve("Test.cs3")
        ByteArrayOutputStream().also { buffer ->
            ZipOutputStream(buffer).use { out ->
                for ((name, text) in entries) {
                    out.putNextEntry(ZipEntry(name))
                    out.write(text.toByteArray())
                    out.closeEntry()
                }
            }
        }.toByteArray().let { Files.write(file, it) }
        return file
    }

    @Test
    fun `reads the plugin class name`() {
        val file = archive(
            "manifest.json" to """{"name":"Test","version":3,"pluginClassName":"x.TestPlugin"}""",
            "classes.dex" to "ignored",
        )

        val manifest = LegacyExtensions.manifestOf(file)

        assertEquals("Test", manifest.name)
        assertEquals(3, manifest.version)
        assertEquals("x.TestPlugin", manifest.pluginClassName)
    }

    @Test
    fun `reports an archive without a manifest`() {
        val file = archive("classes.dex" to "ignored")

        val failure = assertFailsWith<IllegalStateException> { LegacyExtensions.manifestOf(file) }

        assertTrue(failure.message!!.contains("no manifest.json"), failure.message)
    }
}

class SupportedExtensionTest {
    private fun named(name: String): Path = Files.createTempFile("ext", ".$name")

    @Test
    fun `accepts original and desktop extension formats`() {
        for (name in listOf("cs", "cs3", "apk", "jar")) {
            assertTrue(ExtensionHost.isSupported(named(name)), ".$name should be accepted")
        }
        assertFalse(ExtensionHost.isSupported(named("txt")))
        assertFalse(ExtensionHost.isSupported(named("zip")))
    }
}

class PlayerHeaderTest {
    @Test
    fun `forwards referer to mpv`() {
        val args = Player.headerArgs("mpv", mapOf("Referer" to "https://a.example", "User-Agent" to "UA"))

        assertEquals(listOf("--http-header-fields=Referer: https://a.example,User-Agent: UA"), args)
    }

    @Test
    fun `forwards referer to vlc`() {
        val args = Player.headerArgs("C:\\Program Files\\VideoLAN\\VLC\\vlc.exe", mapOf("Referer" to "https://a.example"))

        assertEquals(listOf("--http-referrer=https://a.example"), args)
    }

    @Test
    fun `adds nothing for unknown players or empty headers`() {
        assertTrue(Player.headerArgs("firefox", mapOf("Referer" to "https://a.example")).isEmpty())
        assertTrue(Player.headerArgs("mpv", emptyMap()).isEmpty())
    }
}

class RepositoryTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses a repository manifest`() {
        val manifest = json.decodeFromString<RepositoryManifest>(
            """{
                "name": "Cloudstream providers repository",
                "description": "Cloudstream extension Repository",
                "manifestVersion": 1,
                "pluginLists": ["https://raw.githubusercontent.com/recloudstream/extensions/builds/plugins.json"]
            }""",
        )

        assertEquals("Cloudstream providers repository", manifest.name)
        assertEquals(1, manifest.pluginLists.size)
    }

    @Test
    fun `parses plugin list entries and ignores newer fields`() {
        val plugin = json.decodeFromString<RepositoryPlugin>(
            """{"iconUrl":"https://www.google.com/s2/favicons?domain=dailymotion.com",
                "jarFileSize":49791,
                "fileHash":"sha256-9036525a64e8b3c8fe04f94e9fe89a744c13d69af684ed0d2ad2a7eb0f332cd9",
                "apiVersion":1,"repositoryUrl":"https://github.com/recloudstream/extensions",
                "fileSize":11472,"status":1,"authors":["Luna712"],"tvTypes":["Others"],
                "version":4,"internalName":"DailymotionProvider",
                "jarUrl":"https://raw.githubusercontent.com/recloudstream/extensions/builds/DailymotionProvider.jar",
                "description":"Watch content from Dailymotion",
                "url":"https://raw.githubusercontent.com/recloudstream/extensions/builds/DailymotionProvider.cs3",
                "name":"DailymotionProvider",
                "jarHash":"sha256-542c325f2e1e1f61db60952d161325b62ff58712b1b2020e6df50727fdd03bf2"}""",
        )

        assertEquals("DailymotionProvider", plugin.internalName)
        assertEquals(4, plugin.version)
        assertEquals("https://raw.githubusercontent.com/recloudstream/extensions/builds/DailymotionProvider.cs3", plugin.url)
        assertEquals("sha256-9036525a64e8b3c8fe04f94e9fe89a744c13d69af684ed0d2ad2a7eb0f332cd9", plugin.fileHash)
        assertEquals(11472L, plugin.fileSize)
        assertEquals("Ok", plugin.statusLabel)
    }

    @Test
    fun `maps repository status codes like upstream`() {
        fun statusOf(status: Int) =
            json.decodeFromString<RepositoryPlugin>(
                """{"url":"u","name":"n","internalName":"i","status":$status}""",
            ).statusLabel

        assertEquals("Down", statusOf(0))
        assertEquals("Ok", statusOf(1))
        assertEquals("Slow", statusOf(2))
        assertEquals("Beta", statusOf(3))
    }

    @Test
    fun `accepts the same repository url forms as upstream`() {
        assertEquals(
            ExtensionRepository.OFFICIAL_REPOSITORY,
            ExtensionRepository.normalizeUrl("  "),
        )
        assertEquals(
            "https://raw.githubusercontent.com/x/y/repo.json",
            ExtensionRepository.normalizeUrl("cloudstreamrepo://raw.githubusercontent.com/x/y/repo.json"),
        )
        assertEquals(
            "https://raw.githubusercontent.com/x/y/repo.json",
            ExtensionRepository.normalizeUrl("https://cs.repo/raw.githubusercontent.com/x/y/repo.json"),
        )
        assertEquals(
            "https://example.com/repo.json",
            ExtensionRepository.normalizeUrl("https://example.com/repo.json"),
        )
    }

    @Test
    fun `hashes files like the repository does`() {
        val file = Files.createTempFile("hash", ".cs3")
        Files.write(file, "abc".toByteArray())

        assertEquals(
            "sha256-ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ExtensionRepository.sha256(file),
        )
    }

    @Test
    fun `resolve passes urls through without a lookup`() = kotlinx.coroutines.runBlocking {
        assertEquals(
            "https://raw.githubusercontent.com/x/y/repo.json",
            ExtensionRepository.resolve("https://raw.githubusercontent.com/x/y/repo.json"),
        )
        assertEquals(ExtensionRepository.OFFICIAL_REPOSITORY, ExtensionRepository.resolve(""))
    }

    @Test
    fun `merges repositories keeping one entry per extension`() {
        val a = RepositoryPlugin(
            url = "https://a/One.cs3",
            name = "One",
            internalName = "OneProvider",
            version = 1,
        )
        val aDuplicate = a.copy(url = "https://b/One.cs3", version = 9)
        val b = RepositoryPlugin(
            url = "https://b/Two.cs3",
            name = "Two",
            internalName = "TwoProvider",
            version = 2,
        )

        val merged = ExtensionRepository.mergeRepositories(
            listOf(
                RepositoryState("Repo A", null, listOf(a, b)),
                RepositoryState("Repo B", null, listOf(aDuplicate)),
            ),
        )

        assertEquals(listOf("Repo A"), merged.map { it.name })
        assertEquals(listOf("OneProvider", "TwoProvider"), merged.single().plugins.map { it.internalName })
        assertEquals(1, merged.single().plugins.first().version)
    }

    @Test
    fun `reads a legacy single repository url from settings`() {
        val home = Files.createTempDirectory("cs-settings")
        val dir = Files.createDirectories(home.resolve(".cloudstream-desktop"))
        Files.writeString(dir.resolve("settings.properties"), "repository.url=https://old/repo.json\n")

        val previous = System.getProperty("user.home")
        System.setProperty("user.home", home.toString())
        try {
            assertEquals(listOf("https://old/repo.json"), Settings().repositoryUrls)
        } finally {
            System.setProperty("user.home", previous)
        }
    }
}
