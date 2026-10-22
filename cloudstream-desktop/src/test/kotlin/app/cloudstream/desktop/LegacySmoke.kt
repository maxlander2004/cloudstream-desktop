package app.cloudstream.desktop

import app.cloudstream.desktop.core.ExtensionHost
import app.cloudstream.desktop.core.ExtensionRepository
import com.lagradost.cloudstream3.APIHolder
import app.cloudstream.desktop.core.legacy.LegacyCatalog
import app.cloudstream.desktop.core.legacy.LegacyExtensions
import kotlinx.coroutines.runBlocking
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Loads real, published CloudStream extensions and drives them like the UI does.
 *
 * Run with `./gradlew verifyLegacy`. With no arguments it downloads the five providers from
 * the official CloudStream repository (`recloudstream/extensions`); paths or URLs passed as
 * arguments are used instead.
 */
fun main(args: Array<String>) = runBlocking {
    val targets = if (args.isNotEmpty()) args.toList() else OFFICIAL

    var failures = 0
    for (target in targets) {
        println("=".repeat(78))
        try {
            val file = resolve(target)
            println("file        : ${file.fileName} (${Files.size(file)} bytes)")

            val load = LegacyExtensions.load(file)
            println("plugin      : ${load.manifest.name} v${load.manifest.version} (${load.manifest.pluginClassName})")
            println("providers   : ${load.providers.size}")

            for (provider in load.providers) {
                val catalog = LegacyCatalog(provider, load.manifest.name ?: file.fileName.toString())
                println("  - ${catalog.name}  [${catalog.description}]")

                val browse = runCatching { catalog.browse() }
                println("    browse    : " + browse.fold(
                    { "${it.size} items" + it.take(3).joinToString("") { item -> "\n      · ${item.title}" } },
                    { "FAILED — ${it.message}" },
                ))
                if (browse.isFailure) failures++

                val search = runCatching { catalog.search("test") }
                println("    search    : " + search.fold(
                    { "${it.size} results" },
                    { "FAILED — ${it.message}" },
                ))
                if (search.isFailure) failures++

                val first = browse.getOrNull()?.firstOrNull()
                if (first != null) {
                    val streams = runCatching { catalog.streams(first) }
                    println("    streams   : " + streams.fold(
                        { "${it.size} links for \"${first.title}\"" + it.take(3).joinToString("") { link ->
                            "\n      · ${link.title} -> ${link.url.take(90)}"
                        } },
                        { "FAILED — ${it.message}" },
                    ))
                    // An empty list is a source-side miss, not a host failure.
                    if (streams.isFailure) failures++
                }
            }
        } catch (t: Throwable) {
            failures++
            println("FAILED: ${t::class.simpleName}: ${t.message}")
            t.cause?.let { println("  caused by: ${it::class.simpleName}: ${it.message}") }
        }
    }

    println("=".repeat(78))
    failures += extensionHostCycle(targets)

    println("=".repeat(78))
    failures += repositoryCycle()

    println("=".repeat(78))
    failures += megarepoCycle()

    println("=".repeat(78))
    if (failures > 0) {
        println("$failures failure(s) across ${targets.size} extensions")
        kotlin.system.exitProcess(1)
    }
    println("All ${targets.size} extensions loaded and ran")
}

/**
 * Drives the same path the Extensions screen uses: install into the extensions folder, then
 * load everything twice to prove a reload replaces the registry instead of growing it.
 */
private fun extensionHostCycle(targets: List<String>): Int {
    val home = Files.createTempDirectory("cs-desktop-home")
    val previous = System.getProperty("user.home")
    System.setProperty("user.home", home.toString())

    return try {
        for (target in targets) {
            val file = Paths.get("build", "real-extensions", target.substringAfterLast('/'))
            ExtensionHost.install(file).onFailure {
                println("install ${file.fileName} FAILED — ${it.message}")
                return 1
            }
        }

        val first = ExtensionHost.loadAll()
        val firstRegistry = APIHolder.allProviders.size
        val second = ExtensionHost.loadAll()
        val secondRegistry = APIHolder.allProviders.size

        println("ExtensionHost : ${first.loaded.size} catalogs installed, " +
            "${first.loaded.count { it.legacy }} from CloudStream archives")
        println("reload        : ${second.loaded.size} catalogs, registry " +
            "$firstRegistry -> $secondRegistry providers")
        first.errors.forEach { println("  error: $it") }
        second.errors.forEach { println("  error: $it") }

        when {
            first.loaded.size != targets.size -> 1
            second.loaded.size != first.loaded.size -> 1
            firstRegistry != secondRegistry -> 1
            else -> 0
        }
    } finally {
        System.setProperty("user.home", previous)
    }
}

/**
 * Fetches the official repository index exactly like the Extensions screen, installs every
 * advertised extension through the same download path (size and sha256 verified) and loads
 * them from the extensions folder.
 */
private suspend fun repositoryCycle(): Int {
    val home = Files.createTempDirectory("cs-desktop-repo-home")
    val previous = System.getProperty("user.home")
    System.setProperty("user.home", home.toString())

    return try {
        val state = ExtensionRepository
            .fetchAll(listOf(ExtensionRepository.OFFICIAL_REPOSITORY))
            .repositories
            .single()
        println("Repository   : ${state.name} — ${state.plugins.size} extension(s)")

        var failures = 0
        for (plugin in state.plugins) {
            if (plugin.status == 0) {
                println("  - ${plugin.name} skipped, repository says Down")
                continue
            }

            val result = runCatching {
                val file = ExtensionRepository.install(plugin, ExtensionHost.ensureExtensionsDir())
                val hash = ExtensionRepository.sha256(file)
                val verified = plugin.fileHash?.let { hash.equals(it, ignoreCase = true) } ?: true
                println("  - ${plugin.name} v${plugin.version} [${plugin.statusLabel}] -> " +
                    "${file.fileName} (${Files.size(file)} bytes, " +
                    if (verified) "hash ok)" else "HASH MISMATCH: $hash)")
                if (!verified) error("hash mismatch for ${plugin.name}")
            }
            if (result.isFailure) {
                failures++
                println("    FAILED — ${result.exceptionOrNull()?.message}")
            }
        }

        val load = ExtensionHost.loadAll()
        println("Installed    : ${load.loaded.size} catalog(s) loaded from the repository")
        load.errors.forEach { println("  error: $it") }
        if (load.loaded.isEmpty() || load.errors.isNotEmpty()) failures++
        failures
    } catch (t: Throwable) {
        println("FAILED: ${t::class.simpleName}: ${t.message}")
        1
    } finally {
        System.setProperty("user.home", previous)
    }
}

/**
 * Proves the `megarepo` shortcode keeps working like on Android: it expands to every known
 * community repository, each fetched and merged into one listing.
 */
private suspend fun megarepoCycle(): Int {
    val set = ExtensionRepository.fetchAll(listOf("megarepo"))
    val official = set.repositories.firstOrNull {
        it.plugins.any { plugin -> plugin.internalName == "InternetArchiveProvider" }
    }

    println("Megarepo     : ${set.repositories.size} repositories, ${set.pluginCount} extensions " +
        "(${set.failures.size} unreachable)")
    set.repositories.take(6).forEach { state ->
        println("  - ${state.name}: ${state.plugins.size} extension(s)")
    }
    if (set.repositories.size > 6) {
        println("  … and ${set.repositories.size - 6} more")
    }

    var failures = 0
    if (set.repositories.size < 5 || set.pluginCount < 20) {
        println("  FAILED — expected the whole community catalog, got this little")
        failures++
    }

    // Install an extension that does NOT come from the official repository, through the same
    // path the Extensions screen uses. Some community extensions target the old Android-only
    // plugin API and cannot load here (the current Android app rejects them too), so skip past
    // those the way a user would.
    val candidates = set.repositories
        .filter { it.name != official?.name }
        .mapNotNull { state ->
            state.plugins.firstOrNull { it.status != 0 && it.internalName != ExtensionRepository.MEGA_PLUGIN }
        }

    val home = Files.createTempDirectory("cs-desktop-mega-home")
    val previous = System.getProperty("user.home")
    System.setProperty("user.home", home.toString())
    try {
        for (candidate in candidates) {
            try {
                val file = ExtensionRepository.install(candidate, ExtensionHost.ensureExtensionsDir())
                val load = ExtensionHost.loadAll()
                val loaded = load.loaded.firstOrNull { it.file == file }
                if (loaded == null) {
                    Files.deleteIfExists(file)
                    val reason = load.errors
                        .firstOrNull { it.startsWith(file.fileName.toString()) }
                        ?.substringAfter(": ")
                        ?: "not loadable"
                    println("  ~ ${candidate.name}: $reason — trying the next one")
                    continue
                }
                println("Installed    : ${candidate.name} v${candidate.version} -> ${file.fileName}, " +
                    "loaded as \"${loaded.catalog.name}\"")
                return failures
            } catch (t: Throwable) {
                println("  ~ ${candidate.name}: ${t::class.simpleName}: ${t.message} — trying the next one")
            }
        }
        println("  FAILED — no community extension could be installed")
        failures++
    } finally {
        System.setProperty("user.home", previous)
    }
    return failures
}

private suspend fun resolve(target: String): Path {
    if (!target.startsWith("http")) return Paths.get(target)

    val cache = Paths.get("build", "real-extensions").toAbsolutePath()
    Files.createDirectories(cache)
    val name = target.substringAfterLast('/')
    val target_path = cache.resolve(name)
    if (Files.isRegularFile(target_path) && Files.size(target_path) > 0) return target_path

    val response = HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI.create(target)).build(),
        HttpResponse.BodyHandlers.ofByteArray(),
    )
    check(response.statusCode() == 200) { "download failed with HTTP ${response.statusCode()}" }
    Files.write(target_path, response.body())
    return target_path
}

private const val BASE = "https://raw.githubusercontent.com/recloudstream/extensions/builds"

/** The official repository only ships providers for legal sources. */
private val OFFICIAL = listOf(
    "$BASE/InternetArchiveProvider.cs3",
    "$BASE/DailymotionProvider.cs3",
    "$BASE/YoutubeProvider.cs3",
    "$BASE/InvidiousProvider.cs3",
    "$BASE/TwitchProvider.cs3",
)
