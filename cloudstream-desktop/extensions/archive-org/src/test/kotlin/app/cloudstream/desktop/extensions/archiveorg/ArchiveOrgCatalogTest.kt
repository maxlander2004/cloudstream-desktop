package app.cloudstream.desktop.extensions.archiveorg

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArchiveOrgCatalogTest {
    private val catalog = ArchiveOrgCatalog()

    private val searchResponse = """
        {"response":{"numFound":2,"start":0,"docs":[
          {"identifier":"charlie_chaplin_film_fest","title":"Charlie Chaplin Festival","year":1938},
          {"identifier":"HisNewJobCharlesChaplin-1915","title":["Cukesim's New Job","Charles Chaplin"],"year":"1915"}
        ]}}
    """.trimIndent()

    @Test
    fun `maps search docs to items`() {
        val items = catalog.parseItems(searchResponse)

        assertEquals(2, items.size)
        assertEquals("Charlie Chaplin Festival", items[0].title)
        assertEquals("charlie_chaplin_film_fest", items[0].url)
        assertEquals("Public domain · 1938", items[0].subtitle)
        assertEquals("https://archive.org/services/img/charlie_chaplin_film_fest", items[0].posterUrl)

        // Solr returns `title` as an array when an item has several; take the first.
        assertEquals("Cukesim's New Job", items[1].title)
        assertEquals("Public domain · 1915", items[1].subtitle)
    }

    @Test
    fun `documents without an identifier are skipped and a response body of junk is empty`() {
        val items = catalog.parseItems("""{"response":{"docs":[{"title":"No id here"}]}}""")

        assertTrue(items.isEmpty())
        assertTrue(catalog.parseItems("<html>503</html>").isEmpty())
    }

    @Test
    fun `resolves mp4 files largest first and ignores other formats`() {
        val metadata = """
            {"is_dark":false,"files":[
              {"name":"fest.ogv","format":"Ogg Video","size":"329594705"},
              {"name":"fest.mp4","format":"h.264","size":"483532211"},
              {"name":"fest.mpeg","format":"MPEG2","size":"2814887940"},
              {"name":"low res copy.mp4","format":"MPEG4","size":"1048576"}
            ]}
        """.trimIndent()

        val streams = catalog.parseStreams(metadata, "charlie_chaplin_film_fest")

        assertEquals(2, streams.size)
        assertEquals("MP4 · h.264 · 461 MB", streams[0].title)
        assertEquals("https://archive.org/download/charlie_chaplin_film_fest/fest.mp4", streams[0].url)
        assertEquals("MP4 · 1 MB", streams[1].title)
        // File names with spaces must be encoded to stay a valid URL.
        assertEquals(
            "https://archive.org/download/charlie_chaplin_film_fest/low%20res%20copy.mp4",
            streams[1].url,
        )
    }

    @Test
    fun `a taken-down item resolves to no streams`() {
        val metadata = """{"is_dark":true,"files":[{"name":"gone.mp4","format":"h.264","size":"10"}]}"""

        assertTrue(catalog.parseStreams(metadata, "gone").isEmpty())
    }
}
