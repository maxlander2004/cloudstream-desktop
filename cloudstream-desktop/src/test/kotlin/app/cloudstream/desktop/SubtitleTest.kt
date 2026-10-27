package app.cloudstream.desktop

import app.cloudstream.desktop.api.SubtitleTrack
import app.cloudstream.desktop.core.SubtitleOption
import app.cloudstream.desktop.core.Subtitles
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Subtitle discovery, encoding handling and the player's track selection. */
class SubtitleTest {
    private fun tempDir(): Path = Files.createTempDirectory("cs-subs")

    @Test
    fun `sidecars are matched by the video name and labelled by their language`() {
        val dir = tempDir()
        dir.resolve("Show S01E02.mp4").writeText("video")
        dir.resolve("Show S01E02.en.srt").writeText("1")
        dir.resolve("show s01e02.es.vtt").writeText("2")
        dir.resolve("Show S01E02.notes.txt").writeText("not a subtitle")
        dir.resolve("Other Show.en.srt").writeText("3")

        val found = Subtitles.sidecarsFor(dir.resolve("Show S01E02.mp4"))

        assertEquals(2, found.size)
        assertTrue(found.any { it.language == "en" && it.label == "en" })
        // The video's own extension in the file name is not part of the match.
        assertEquals(setOf("en", "es"), found.mapNotNull { it.language }.toSet())
        assertTrue(found.all { it.file != null })
    }

    @Test
    fun `an unrelated folder yields no subtitles`() {
        val dir = tempDir()
        dir.resolve("Movie.mkv").writeText("video")
        dir.resolve("Movie.srt").writeText("same name, no language suffix")

        assertEquals(listOf("Movie"), Subtitles.sidecarsFor(dir.resolve("Movie.mkv")).map { it.label })
        assertTrue(Subtitles.sidecarsFor(dir.resolve("Missing.mp4")).isEmpty())
        assertTrue(Subtitles.sidecarsFor(dir.resolve("Nothing.mp4")).isEmpty())
    }

    @Test
    fun `windows-1252 subtitles are decoded instead of appearing as mojibake`() {
        val latin1 = "1\n00:00:01,000 --> 00:00:02,000\ncafé déjà vu\n".toByteArray(Charsets.ISO_8859_1)

        val text = Subtitles.decodeSubtitle(latin1)

        assertTrue(text.contains("café déjà vu"), "expected decoded text, got: $text")
    }

    @Test
    fun `utf-8 is kept and a byte order mark is stripped`() {
        val plain = "café".toByteArray(Charsets.UTF_8)
        assertEquals("café", Subtitles.decodeSubtitle(plain))

        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + plain
        assertEquals("café", Subtitles.decodeSubtitle(bom))
        assertEquals("", Subtitles.decodeSubtitle(ByteArray(0)))
    }

    @Test
    fun `the preferred language wins and falls back to the first track`() {
        val options = listOf(
            SubtitleOption("English", "en", url = "https://example.com/en.srt"),
            SubtitleOption("Spanish", "es", url = "https://example.com/es.srt"),
            SubtitleOption("French", "fr", url = "https://example.com/fr.srt"),
        )

        assertEquals("es", Subtitles.preferred(options, "ES")?.language)
        assertEquals("en", Subtitles.preferred(options, "")?.language)
        assertEquals("en", Subtitles.preferred(options, "de")?.language)
        assertNull(Subtitles.preferred(emptyList(), "en"))
    }

    @Test
    fun `a language name in the label still matches the code`() {
        val named = listOf(
            SubtitleOption("English", null, url = "https://example.com/a.srt"),
            SubtitleOption("Japanese", null, url = "https://example.com/b.srt"),
        )

        assertEquals("Japanese", Subtitles.preferred(named, "ja")?.label)
        assertEquals("Japanese", Subtitles.preferred(named, "japanese")?.label)
        // "en" selects English by prefix, and does not match Japanese by accident.
        assertEquals("English", Subtitles.preferred(named, "en")?.label)
    }

    @Test
    fun `subtitle extensions come from the url and default to srt`() {
        assertEquals("vtt", Subtitles.extensionFor("https://example.com/a/captions.vtt?token=1"))
        assertEquals("ass", Subtitles.extensionFor("https://example.com/a/captions.ASS"))
        assertEquals("srt", Subtitles.extensionFor("https://example.com/captions"))
        assertEquals("srt", Subtitles.extensionFor("https://example.com/captions.html"))
    }

    @Test
    fun `only real local files count as local paths`() {
        val dir = tempDir()
        val file = dir.resolve("Episode.mp4")
        file.writeText("video")

        assertNotNull(Subtitles.localPathOf(file.toString()))
        assertNotNull(Subtitles.localPathOf(file.toUri().toString()))
        assertNull(Subtitles.localPathOf("https://example.com/Episode.mp4"))
        assertNull(Subtitles.localPathOf(dir.resolve("Nope.mp4").toString()))
    }

    @Test
    fun `saving beside a download writes a language-suffixed subtitle file`() {
        val dir = tempDir()
        val video = dir.resolve("Episode 1.mp4")
        video.writeText("video")
        val source = dir.resolve("incoming.srt")
        source.writeText("1\n00:00:01,000 --> 00:00:02,000\nhello\n")

        val saved = runBlocking {
            Subtitles.saveBeside(video, SubtitleOption(label = "English", language = "en", file = source))
        }

        assertNotNull(saved)
        assertEquals("Episode 1.en.srt", saved.fileName.toString())
        assertEquals("hello", Files.readAllLines(saved)[2])

        // …and the player finds it again for the video it belongs to.
        val discovered = Subtitles.sidecarsFor(video)
        assertEquals(listOf("en"), discovered.map { it.language })
    }

    @Test
    fun `a source track becomes a player option unchanged`() {
        val track = SubtitleTrack("English", "en", "https://example.com/en.srt", mapOf("Referer" to "https://site"))

        val option = Subtitles.optionOf(track)

        assertEquals("English", option.label)
        assertEquals("en", option.language)
        assertEquals("https://example.com/en.srt", option.url)
        assertEquals("https://site", option.headers["Referer"])
        assertNull(option.file)
        assertEquals("https://example.com/en.srt", option.key)
    }
}
