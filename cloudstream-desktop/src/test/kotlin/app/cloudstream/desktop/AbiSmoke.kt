package app.cloudstream.desktop

import app.cloudstream.desktop.core.legacy.LegacyExtensions
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import kotlinx.coroutines.runBlocking
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest

/**
 * The ABI smoke test: one pinned, real CloudStream archive, loaded and nothing more.
 *
 * [AbiContractTest] proves the members exist by reflection, which is fast and offline but says
 * nothing about the path that actually matters — translating an archive's `classes.dex` to JVM
 * bytecode and letting a real provider's bytecode link against `:cs-compat`. This does exactly
 * that, against a specific provider from the official repository, and stops there: it never
 * browses, searches or resolves links, because a source's own upstream being down is not a
 * signal about this port. `verifyLegacy` is the one that drives providers for real.
 *
 * Run with `./gradlew abiSmoke`, and in the scheduled live-checks workflow. It is deliberately
 * *not* part of `test`: a pull request must never fail because raw.githubusercontent.com was
 * slow. The pinned archive is a rolling build, so its SHA-256 is printed rather than compared —
 * the repository's own signature is what the Extensions screen checks, and `verifyLegacy` covers
 * that path with a hash-verified install.
 */
fun main() = runBlocking {
    println("ABI smoke: loading a published extension to prove translated bytecode still links")

    val archive = try {
        download(PINNED_URL)
    } catch (t: Throwable) {
        println("FAILED: could not fetch $PINNED_URL — ${t::class.simpleName}: ${t.message}")
        kotlin.system.exitProcess(1)
    }

    println("archive    : ${archive.fileName} (${Files.size(archive)} bytes)")
    println("sha256     : ${sha256(archive)}")

    var failures = 0
    val load = try {
        LegacyExtensions.load(archive)
    } catch (t: Throwable) {
        println("FAILED: the archive did not load — ${t::class.simpleName}: ${t.message}")
        t.cause?.let { println("  caused by: ${it::class.simpleName}: ${it.message}") }
        kotlin.system.exitProcess(1)
    }

    val manifest = load.manifest
    println("plugin     : ${manifest.name} v${manifest.version} (${manifest.pluginClassName})")
    println("providers  : ${load.providers.size}")

    if (load.providers.isEmpty()) {
        println("FAILED: the plugin loaded but registered no providers")
        failures++
    }

    val before = APIHolder.allProviders.size
    for (provider in load.providers) {
        // A provider that linked is a MainAPI instance whose own class resolved; if the ABI had
        // moved, the class loader would have thrown before reaching here.
        val links = MainAPI::class.java.isAssignableFrom(provider.javaClass)
        println(
            "  - ${provider.name}  <${provider.mainUrl}>" +
                "  class=${provider.javaClass.name}" +
                "  super=${provider.javaClass.superclass?.name ?: "none"}" +
                if (links) "" else "  FAILED: not a MainAPI",
        )
        if (!links) failures++
        if (provider.mainUrl.isBlank()) {
            println("    FAILED: the provider lost its mainUrl")
            failures++
        }
    }

    // Provider registration is the observable outcome of the plugin's own load() running.
    if (APIHolder.allProviders.size != before + load.providers.size) {
        println(
            "FAILED: registry went $before -> ${APIHolder.allProviders.size} " +
                "for ${load.providers.size} providers",
        )
        failures++
    }

    println("=".repeat(78))
    if (failures > 0) {
        println("$failures failure(s): the extension ABI does not hold")
        kotlin.system.exitProcess(1)
    }
    println("ABI holds: ${load.providers.size} provider(s) loaded from a published archive")
}

/** Downloaded once per build directory, so a rerun does not depend on the same host twice. */
private fun download(url: String): Path {
    val cache = Paths.get("build", "abi-smoke").toAbsolutePath()
    Files.createDirectories(cache)
    val target = cache.resolve(url.substringAfterLast('/'))
    if (Files.isRegularFile(target) && Files.size(target) > 0) return target

    val response = HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI.create(url)).build(),
        HttpResponse.BodyHandlers.ofByteArray(),
    )
    check(response.statusCode() == 200) { "download failed with HTTP ${response.statusCode()}" }
    check(response.body().isNotEmpty()) { "download was empty" }
    Files.write(target, response.body())
    return target
}

private fun sha256(file: Path): String =
    MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(file))
        .joinToString("") { "%02x".format(it) }

/**
 * Pinned by name rather than by hash: the official repository's `builds` branch is rebuilt on
 * every release, so a hash here would fail the check for a reason that has nothing to do with
 * this port. The provider is the one already used by `verifyLegacy` and the README's table.
 */
private const val PINNED_URL =
    "https://raw.githubusercontent.com/recloudstream/extensions/builds/InternetArchiveProvider.cs3"
