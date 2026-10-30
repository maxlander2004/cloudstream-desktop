package app.cloudstream.desktop

import app.cloudstream.desktop.core.SubtitleSearch
import app.cloudstream.desktop.core.Tracking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** OpenSubtitles search results and the Simkl id lookup, without any network. */
class SubtitleProviderTest {

    @Test
    fun `opensubtitles results carry the file id a download needs`() {
        val body = """
            {"total_count":2,"data":[
              {"id":"11","attributes":{"language":"en","release":"Show.S01E02.1080p.WEB",
               "files":[{"file_id":9001,"file_name":"Show.S01E02.srt"}],
               "feature_details":{"title":"Show","season_number":1,"episode_number":2}}},
              {"id":"12","attributes":{"language":"es","release":"Show.S01E02.HDTV",
               "files":[{"file_id":9002,"file_name":"Show.S01E02.es.srt"}],
               "feature_details":{"title":"Show"}}}
            ]}
        """.trimIndent()

        val hits = SubtitleSearch.parseOpenSubtitles(body)
        assertEquals(2, hits.size)
        assertEquals(9001, hits[0].fileId)
        assertEquals("en", hits[0].language)
        assertEquals("Show.S01E02.1080p.WEB", hits[0].release)
        assertTrue(hits[0].label.contains("Show.S01E02.1080p.WEB"))
        assertEquals(SubtitleSearch.PROVIDER_OPENSUBTITLES, hits[0].provider)
    }

    @Test
    fun `opensubtitles results without files are dropped`() {
        val body = """{"data":[{"attributes":{"language":"en","files":[]}}]}"""
        assertTrue(SubtitleSearch.parseOpenSubtitles(body).isEmpty())
        assertTrue(SubtitleSearch.parseOpenSubtitles("""{"message":"Unauthorized"}""").isEmpty())
    }

    @Test
    fun `opensubtitles download link is read`() {
        val body = """{"link":"https://dl.opensubtitles.com/download/abc.srt","file_name":"x.srt","remaining":99}"""
        assertEquals("https://dl.opensubtitles.com/download/abc.srt", SubtitleSearch.parseOpenSubtitlesDownload(body))
        assertNull(SubtitleSearch.parseOpenSubtitlesDownload("""{"message":"You must be logged in"}"""))
    }

    @Test
    fun `opensubtitles language is a lower case code`() {
        assertEquals("en", SubtitleSearch.openSubtitlesLanguage("en"))
        assertEquals("pt", SubtitleSearch.openSubtitlesLanguage("pt-BR"))
        assertEquals("en", SubtitleSearch.openSubtitlesLanguage(null))
    }

    @Test
    fun `simkl search returns the first simkl id`() {
        val body = """
            [{"title":"One Punch Man","ids":{"simkl":12345,"slug":"one-punch-man","imdb":"tt123"}},
             {"title":"One Punch Man 2","ids":{"simkl":67890}}]
        """.trimIndent()
        assertEquals(12345, Tracking.parseSimklId(body))
        assertNull(Tracking.parseSimklId("[]"))
        assertNull(Tracking.parseSimklId("""{"error":"bad client"}"""))
    }
}
