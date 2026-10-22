package app.cloudstream.desktop.core

import app.cloudstream.desktop.api.Catalog
import app.cloudstream.desktop.api.CatalogItem
import app.cloudstream.desktop.api.StreamLink

/**
 * Built-in source backed by the iptv-org public playlist, which lists freely available
 * channels and is the same kind of open source the upstream README advertises.
 *
 * It exists so a fresh install has something playable; everything else is expected to come
 * from extensions.
 */
class IptvSource : Catalog {
    override val name: String = "Public IPTV"
    override val description: String = "Freely available channels from the iptv-org project."

    private var cache: List<CatalogItem>? = null

    private suspend fun channels(): List<CatalogItem> {
        cache?.let { return it }
        val parsed = parse( Http.get(PLAYLIST_URL) )
        cache = parsed
        return parsed
    }

    override suspend fun browse(): List<CatalogItem> = channels().take(400)

    override suspend fun search(query: String): List<CatalogItem> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return channels().filter { it.title.lowercase().contains(needle) }.take(200)
    }

    override suspend fun streams(item: CatalogItem): List<StreamLink> =
        listOf(StreamLink(item.title, item.url))

    internal fun parse(playlist: String): List<CatalogItem> {
        val out = mutableListOf<CatalogItem>()
        var title: String? = null
        var logo: String? = null

        for (raw in playlist.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF") -> {
                    val comma = line.lastIndexOf(',')
                    title = if (comma >= 0) line.substring(comma + 1).trim() else ""
                    logo = LOGO.find(line)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
                }

                line.isEmpty() || line.startsWith("#") -> Unit

                else -> {
                    val channel = title
                    if (!channel.isNullOrBlank()) {
                        out += CatalogItem(
                            title = channel,
                            url = line,
                            subtitle = "Live TV",
                            posterUrl = logo,
                        )
                    }
                    title = null
                    logo = null
                }
            }
        }
        return out
    }

    private companion object {
        const val PLAYLIST_URL = "https://iptv-org.github.io/iptv/index.m3u"
        val LOGO = Regex("tvg-logo=\"([^\"]*)\"")
    }
}
