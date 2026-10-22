package app.cloudstream.desktop

import app.cloudstream.desktop.core.ExtensionHost
import kotlinx.coroutines.runBlocking

/**
 * Live check of the installed extension through the same loader the UI uses.
 * Run with `gradlew verifyExtension`; it needs network access.
 */
fun main() {
    val dir = ExtensionHost.extensionsDir()
    val result = ExtensionHost.loadAll()

    println("extensions dir : $dir")
    println("loaded         : ${result.loaded.size}")
    result.loaded.forEach { println("  - ${it.manifest.name} v${it.manifest.version} (${it.file.fileName})") }
    result.errors.forEach { println("  error: $it") }

    val extension = result.loaded.firstOrNull { it.catalog.name == "Public Domain Movies" }
        ?: error("Public Domain Movies extension is not installed in $dir")

    runBlocking {
        val browse = extension.catalog.browse()
        println("browse         : ${browse.size} items")
        browse.take(3).forEach { println("  · ${it.title} [${it.url}] ${it.subtitle}") }
        require(browse.isNotEmpty()) { "browse() returned nothing" }

        val search = extension.catalog.search("chaplin")
        println("search(chaplin): ${search.size} results")
        search.take(3).forEach { println("  · ${it.title}") }
        require(search.isNotEmpty()) { "search() returned nothing" }

        // Some catalogue entries legitimately have nothing playable, so walk a few the way
        // a user would until one resolves.
        var playable = 0
        for (item in browse.take(5)) {
            val streams = extension.catalog.streams(item)
            if (streams.isEmpty()) {
                println("streams        : none for ${item.title} (no playable file)")
                continue
            }
            playable++
            println("streams        : ${streams.size} for ${item.title}")
            streams.take(2).forEach { println("  · ${it.title} -> ${it.url}") }
            require(streams.all { it.url.startsWith("https://archive.org/download/") }) { "unexpected stream url" }
            if (playable == 2) break
        }
        require(playable > 0) { "no item in the first 5 resolved a stream" }
    }

    println("OK: extension installed and usable end to end")
}
