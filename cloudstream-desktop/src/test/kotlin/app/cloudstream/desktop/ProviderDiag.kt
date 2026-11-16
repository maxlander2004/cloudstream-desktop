package app.cloudstream.desktop

import app.cloudstream.desktop.core.legacy.DexTranslator
import app.cloudstream.desktop.core.legacy.LegacyExtensions
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.plugins.Plugin
import java.lang.reflect.Modifier
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipFile

/**
 * Throwaway diagnostic for plugins that load but register no providers: reports what `load()`
 * actually did, and why registering failed if it threw inside a `catch`.
 */
fun main() {
    val names = arrayOf(
        "FshareProvider", "IPTV", "Stremio", "MonPlayerProvider", "M3UPlaylistPlayerProvider",
        "SubscriptionManager", "SyncProvider", "WatchParty", "Wrapped", "AutoExtensions",
    )
    val dir = Paths.get("build", "survey-jars")

    // Anything thrown and swallowed by a plugin still passes through our classloader's
    // loadClass and through registerMainAPI, so record both.
    for (name in names) {
        val archive = dir.resolve("$name.cs3")
        if (!Files.isRegularFile(archive)) {
            println("$name — no archive")
            continue
        }
        println("=".repeat(70))
        println(name)
        val jar = DexTranslator.translate(archive)
        val loader = URLClassLoader(arrayOf(jar.toUri().toURL()), LegacyExtensions::class.java.classLoader)

        val pluginClass = classNames(jar)
            .mapNotNull { runCatching { loader.loadClass(it) }.getOrNull() }
            .firstOrNull { Plugin::class.java.isAssignableFrom(it) && !Modifier.isAbstract(it.modifiers) }
        if (pluginClass == null) {
            println("  no plugin class")
            continue
        }
        val before = APIHolder.allProviders.size
        val plugin = pluginClass.getDeclaredConstructor().newInstance() as Plugin
        val result = runCatching { plugin.load() }
        val after = APIHolder.allProviders.size
        println("  load() -> ${result.exceptionOrNull()?.let { "THREW ${it::class.java.name}: ${it.message}" } ?: "returned normally"}")
        println("  providers: +${after - before}")
        if (after - before == 0) {
            // Inspect what load() tried to touch but could not.
            val missing = mutableListOf<String>()
            classNames(jar)
                .mapNotNull { runCatching { loader.loadClass(it) }.exceptionOrNull() }
                .map { e -> generateSequence(e) { it.cause }.last().message ?: e.toString() }
                .distinct()
                .forEach { missing += it }
            if (missing.isNotEmpty()) {
                println("  classes that failed to load:")
                missing.take(12).forEach { println("    $it") }
            }
            val mainApis = classNames(jar)
                .mapNotNull { runCatching { loader.loadClass(it) }.getOrNull() }
                .filter { MainAPI::class.java.isAssignableFrom(it) && !Modifier.isAbstract(it.modifiers) }
            println("  MainAPI subclasses: ${mainApis.map { it.name }}")
            for (cls in mainApis) {
                val ctor = cls.declaredConstructors.firstOrNull()
                println("    ${cls.simpleName} ctor params: ${ctor?.parameterTypes?.joinToString { it.simpleName }}")
            }
        }
    }
}

private fun classNames(jar: java.nio.file.Path): List<String> = ZipFile(jar.toFile()).use { zip ->
    zip.entries().asSequence()
        .map { it.name }
        .filter { it.endsWith(".class") }
        .map { it.removeSuffix(".class").replace('/', '.') }
        .toList()
}
