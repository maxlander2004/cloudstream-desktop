package app.cloudstream.desktop

import app.cloudstream.desktop.core.ExtensionRepository
import app.cloudstream.desktop.core.legacy.LegacyExtensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Collections

/**
 * Throwaway diagnostic: downloads every extension the community catalog advertises and loads
 * each one through the real loader, then groups the failures by their deepest cause.
 */
fun main() = runBlocking {
    val out = Paths.get("build", "extension-survey")
    Files.createDirectories(out)
    val jars = Paths.get("build", "survey-jars")
    Files.createDirectories(jars)

    val set = ExtensionRepository.fetchAll(listOf("megarepo"))
    println("repositories : ${set.repositories.size} (${set.failures.size} unreachable)")
    set.failures.take(5).forEach { (url, why) -> println("  unreachable: $url — $why") }
    val plugins = set.repositories.flatMap { it.plugins }
    println("extensions   : ${plugins.size}")
    System.out.flush()

    val installed = Collections.synchronizedList(mutableListOf<Pair<String, Path>>())
    val downloadFailures = Collections.synchronizedList(mutableListOf<Pair<String, String>>())
    val gate = Semaphore(8)
    var finished = 0
    coroutineScope {
        plugins.map { plugin ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    runCatching { ExtensionRepository.install(plugin, jars) }
                        .onSuccess { installed += plugin.internalName to it }
                        .onFailure { downloadFailures += plugin.internalName to (it.message ?: "download failed") }
                    synchronized(this) {
                        finished++
                        if (finished % 50 == 0) println("downloaded   : $finished/${plugins.size}")
                    }
                }
            }
        }.awaitAll()
    }
    println("downloaded   : ${installed.size} ok, ${downloadFailures.size} failed")
    downloadFailures.take(10).forEach { (name, why) -> println("  $name — $why") }
    System.out.flush()

    // Publish the registry an install-from-repository would publish, so plugins that look
    // themselves up (by install url) behave the way they do in the app.
    com.lagradost.cloudstream3.plugins.PluginManager.pluginsOnline = plugins.map { plugin ->
        com.lagradost.cloudstream3.plugins.PluginData(
            internalName = plugin.internalName,
            url = plugin.url,
            isOnline = true,
            filePath = jars.resolve("${plugin.internalName}.cs3").toString(),
            version = plugin.version,
        )
    }.toTypedArray()

    val rows = mutableListOf<String>()
    val groups = mutableMapOf<String, MutableList<String>>()
    var ok = 0
    var index = 0
    for ((name, file) in installed.sortedBy { it.first.lowercase() }) {
        index++
        // One archive at a time is a batch of one: fire the batch event the host fires, so
        // plugins that register from `afterPluginsLoadedEvent` are measured as they run.
        val result = runCatching {
            LegacyExtensions.load(file)
            LegacyExtensions.notifyPluginsLoaded()
            LegacyExtensions.providersOf(file)
        }
        LegacyExtensions.unloadAll()
        val line = if (result.isSuccess) {
            ok++
            "OK   $name (${result.getOrThrow().size})"
        } else {
            val root = deepest(result.exceptionOrNull()!!)
            val signature = "${root::class.simpleName}: ${root.message}"
                .replace(Regex("\\s+"), " ")
                .take(220)
            groups.getOrPut(signature) { mutableListOf() } += name
            "FAIL $name — $signature"
        }
        rows += line
        if (index % 25 == 0 || index == installed.size) {
            println("loaded       : $index/${installed.size} (ok $ok)")
            System.out.flush()
        }
    }

    val summary = buildString {
        appendLine("extensions   : ${plugins.size}")
        appendLine("loaded ok    : $ok/${installed.size}")
        appendLine("failed       : ${installed.size - ok}/${installed.size}")
        appendLine()
        appendLine("failure groups:")
        groups.entries.sortedByDescending { it.value.size }.forEach { (signature, names) ->
            appendLine("  ${names.size}x $signature")
            appendLine("      ${names.take(8).joinToString(", ")}")
        }
        appendLine()
        rows.forEach { appendLine(it) }
    }
    Files.writeString(out.resolve("results.txt"), summary)
    println("=".repeat(78))
    println(summary.substringBefore("failure groups"))
    println("failure groups:")
    groups.entries.sortedByDescending { it.value.size }.forEach { (signature, names) ->
        println("  ${names.size}x $signature")
    }
    println("results: ${out.resolve("results.txt").toAbsolutePath()}")
}

private fun deepest(t: Throwable): Throwable {
    var current = t
    while (true) {
        val next = current.cause ?: break
        if (next === current) break
        current = next
    }
    return current
}
