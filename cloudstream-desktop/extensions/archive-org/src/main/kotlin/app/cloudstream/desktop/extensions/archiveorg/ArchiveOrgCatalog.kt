package app.cloudstream.desktop.extensions.archiveorg

import app.cloudstream.desktop.api.Catalog
import app.cloudstream.desktop.api.CatalogItem
import app.cloudstream.desktop.api.StreamLink
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Example desktop extension: public-domain feature films hosted by the Internet Archive.
 *
 * It is a plain `.jar` that implements [Catalog] — no Android, no Dalvik, no ExoPlayer. It
 * shows the three things an extension actually needs to do: list content, search it, and
 * resolve playable links. Playback itself is the host app's job.
 *
 * The Internet Archive only serves material it is legally allowed to distribute, and the
 * `feature_films` collection is public domain / openly licensed.
 */
class ArchiveOrgCatalog : Catalog {
    override val name: String = "Public Domain Movies"
    override val description: String = "Free-to-watch feature films hosted by the Internet Archive."

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    override suspend fun browse(): List<CatalogItem> = parseItems(get(searchUrl(BROWSE_QUERY)))

    override suspend fun search(query: String): List<CatalogItem> {
        // Solr treats punctuation as syntax, so a raw query such as "Dr. Strangelove" would
        // be a parse error rather than a search.
        val needle = query.filter { it.isLetterOrDigit() || it == ' ' }.trim().replace(WHITESPACE, " ")
        if (needle.isEmpty()) return emptyList()
        return parseItems(get(searchUrl("title:($needle) AND $FILTER")))
    }

    override suspend fun streams(item: CatalogItem): List<StreamLink> =
        parseStreams(get(METADATA_URL + item.url), item.url)

    private fun searchUrl(expression: String): String =
        "$SEARCH_URL?q=${encode(expression)}" +
            "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=year" +
            "&rows=$ROWS&page=1&output=json&sort%5B%5D=downloads+desc"

    private fun get(url: String): String {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(25))
            .header("User-Agent", "CloudStreamDesktop-ArchiveOrg/1.0")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) { "HTTP ${response.statusCode()} from archive.org" }
        return response.body()
    }

    internal fun parseItems(body: String): List<CatalogItem> {
        val docs = runCatching {
            Json.parseToJsonElement(body).jsonObject["response"]?.jsonObject?.get("docs") as? JsonArray
        }.getOrNull() ?: return emptyList()

        return docs.mapNotNull { element ->
            val doc = element as? JsonObject ?: return@mapNotNull null
            val identifier = doc["identifier"].text() ?: return@mapNotNull null
            CatalogItem(
                title = doc["title"].text()?.takeIf { it.isNotBlank() } ?: identifier,
                url = identifier,
                subtitle = doc["year"].text()?.let { "Public domain · $it" } ?: "Public domain",
                posterUrl = THUMB_URL + identifier,
            )
        }
    }

    internal fun parseStreams(body: String, identifier: String): List<StreamLink> {
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyList()
        // Items that were taken down keep a metadata record, but carry is_dark.
        if (root["is_dark"].text() == "true") return emptyList()

        val files = root["files"] as? JsonArray ?: return emptyList()
        return files.mapNotNull { element ->
            val file = element as? JsonObject ?: return@mapNotNull null
            val fileName = file["name"].text() ?: return@mapNotNull null
            if (!fileName.endsWith(".mp4", ignoreCase = true)) return@mapNotNull null

            val size = file["size"].text()?.toLongOrNull()
            val format = file["format"].text().orEmpty()
            val label = buildString {
                append(fileName.substringAfterLast('.').uppercase())
                if (format.isNotBlank() && !format.equals("MPEG4", ignoreCase = true)) append(" · $format")
                if (size != null) append(" · ${humanSize(size)}")
            }

            StreamLink(
                title = label,
                url = "$DOWNLOAD_URL$identifier/${encode(fileName)}",
            ) to (size ?: 0L)
        }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { it.url }
    }

    private fun humanSize(bytes: Long): String {
        val mb = bytes / 1024.0 / 1024.0
        return if (mb >= 1024) String.format("%.1f GB", mb / 1024) else String.format("%.0f MB", mb)
    }

    private fun JsonElement?.text(): String? = when (this) {
        is JsonPrimitive -> contentOrNull
        is JsonArray -> firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull }
        else -> null
    }

    /** Percent-encodes for a query value or a single path segment. */
    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private companion object {
        const val ROWS = 40
        // `format:(MPEG4)` keeps metadata-only uploads out of the results: without it the
        // most-downloaded hits are items that carry no video file at all.
        const val FILTER = "collection:(feature_films) AND mediatype:(movies) AND format:(MPEG4)"
        const val BROWSE_QUERY = "$FILTER AND -title:(trailer)"
        const val SEARCH_URL = "https://archive.org/advancedsearch.php"
        const val METADATA_URL = "https://archive.org/metadata/"
        const val DOWNLOAD_URL = "https://archive.org/download/"
        const val THUMB_URL = "https://archive.org/services/img/"
        val WHITESPACE = Regex("\\s+")
    }
}
