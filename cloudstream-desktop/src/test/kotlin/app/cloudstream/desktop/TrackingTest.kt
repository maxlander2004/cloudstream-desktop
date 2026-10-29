package app.cloudstream.desktop

import app.cloudstream.desktop.core.Accounts
import app.cloudstream.desktop.core.SubtitleSearch
import app.cloudstream.desktop.core.Tracking
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The pure half of account linking and subtitle search, so no network is needed. */
class TrackingTest {

    @Test
    fun `anilist media search returns the first match`() {
        val body = """
            {"data":{"Page":{"media":[
              {"id":21,"title":{"romaji":"One Punch Man","english":"One-Punch Man"}},
              {"id":22,"title":{"romaji":"One Punch Man 2","english":"One-Punch Man 2"}}
            ]}}}
        """.trimIndent()
        assertEquals(21, Tracking.parseAniListMediaId(body))
    }

    @Test
    fun `anilist media search without results is null`() {
        assertEquals(null, Tracking.parseAniListMediaId("""{"data":{"Page":{"media":[]}}}"""))
        assertEquals(null, Tracking.parseAniListMediaId("""{"errors":[{"message":"Unauthorized"}]}"""))
    }

    @Test
    fun `anilist save response reports the stored progress`() {
        val body = """{"data":{"SaveMediaListEntry":{"id":12,"progress":7,"status":"CURRENT"}}}"""
        assertEquals(7, Tracking.parseAniListProgress(body))
    }

    @Test
    fun `mal search uses the node id`() {
        val body = """{"data":[{"node":{"id":30276,"title":"One Punch Man"}}],"paging":{}}"""
        assertEquals(30276, Tracking.parseMalAnimeId(body))
        assertNull(Tracking.parseMalAnimeId("""{"data":[]}"""))
    }

    @Test
    fun `oauth form values round trip`() {
        val encoded = Accounts.formEncode(mapOf("code" to "a b/c", "state" to "x=y"))
        assertEquals("code=a+b%2Fc&state=x%3Dy", encoded)
        val parsed = Accounts.parseForm(encoded)
        assertEquals("a b/c", parsed["code"])
        assertEquals("x=y", parsed["state"])
    }

    @Test
    fun `pkce challenge matches the reference vector`() {
        // RFC 7636, appendix B.
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            Accounts.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun `aniList token fragment is parsed`() {
        val form = Accounts.parseForm("access_token=abc.def&token_type=Bearer&expires_in=31536000")
        assertEquals("abc.def", form["access_token"])
        assertEquals("31536000", form["expires_in"])
    }

    @Test
    fun `subtitle language codes map to what subdl expects`() {
        assertEquals("EN", SubtitleSearch.subdlLanguage("en"))
        assertEquals("ES", SubtitleSearch.subdlLanguage("es-MX"))
        assertEquals("PT", SubtitleSearch.subdlLanguage("pt-BR"))
        assertEquals("EN", SubtitleSearch.subdlLanguage(""))
        assertEquals("EN", SubtitleSearch.subdlLanguage("klingon"))
    }

    @Test
    fun `subdl search response is parsed into hits`() {
        val body = """
            {"status":true,"subtitles":[
              {"name":"Show.S01E02.WEB-DL","language":"English","release":"Show.S01E02.1080p",
               "url":"/subtitle/12345.zip"},
              {"name":"Show.S01E02.HDTV","language":"Spanish","release":"",
               "url":"https://dl.subdl.com/subtitle/6789.zip"}
            ]}
        """.trimIndent()
        val hits = SubtitleSearch.parseSubdl(body)
        assertEquals(2, hits.size)
        assertEquals("https://dl.subdl.com/subtitle/12345.zip", hits[0].url)
        assertEquals("https://dl.subdl.com/subtitle/6789.zip", hits[1].url)
        assertEquals("English", hits[0].language)
        assertTrue(hits[0].label.contains("Show.S01E02.1080p"))
    }

    @Test
    fun `broken subdl response yields no hits`() {
        assertTrue(SubtitleSearch.parseSubdl("""{"status":false,"error":"invalid api key"}""").isEmpty())
        assertTrue(SubtitleSearch.parseSubdl("not json").isEmpty())
    }

    @Test
    fun `a zip archive is unwrapped to its subtitle`() {
        val srt = "1\n00:00:01,000 --> 00:00:02,000\nHello\n"
        val zip = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { stream ->
                stream.putNextEntry(ZipEntry("Show.S01E02.en.srt"))
                stream.write(srt.toByteArray())
                stream.closeEntry()
            }
        }.toByteArray()

        val extracted = SubtitleSearch.extractSubtitle(zip)
        assertNotNull(extracted)
        assertEquals("Show.S01E02.en.srt", extracted.first)
        assertTrue(String(extracted.second).contains("Hello"))
    }

    @Test
    fun `a bare subtitle file is accepted and other files are not`() {
        val srt = "1\n00:00:01,000 --> 00:00:02,000\nHello\n"
        val direct = SubtitleSearch.extractSubtitle(srt.toByteArray())
        assertNotNull(direct)
        assertNull(SubtitleSearch.extractSubtitle("<html>rate limited</html>".toByteArray()))
    }
}
