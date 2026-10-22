package app.cloudstream.desktop.core.legacy

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.BasePlugin
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.IdentityHashMap
import java.util.zip.ZipFile

@Serializable
data class LegacyManifest(
    val name: String? = null,
    val version: Int? = null,
    @SerialName("pluginClassName") val pluginClassName: String,
    val requiresResources: Boolean = false,
)

data class LegacyLoad(
    val manifest: LegacyManifest,
    val providers: List<MainAPI>,
    val classLoader: ClassLoader,
)

/**
 * Loads real CloudStream extension archives (`.cs`, `.cs3`, `.apk`) in the desktop JVM.
 *
 * This follows `PluginManager.loadPlugin` step for step: read `manifest.json` from the
 * archive, resolve `pluginClassName`, instantiate it as a [BasePlugin] and call `load()`.
 * Plugins register their providers through `registerMainAPI`, which appends to
 * `APIHolder.allProviders` — so the providers a plugin contributed are exactly the entries
 * that appeared while it was loading.
 *
 * The two unavoidable differences from Android are mechanical: `classes.dex` is translated
 * to JVM bytecode first, and the parent class loader is this application's, which carries a
 * JVM build of upstream's own `library` module (see the `:cs-compat` project).
 */
object LegacyExtensions {
    private val json = Json { ignoreUnknownKeys = true }

    fun looksLikeLegacy(file: Path): Boolean {
        val name = file.fileName.toString().lowercase()
        return name.endsWith(".cs") || name.endsWith(".cs3") || name.endsWith(".apk")
    }

    fun manifestOf(archive: Path): LegacyManifest {
        require(Files.isRegularFile(archive)) { "${archive.fileName} is not a file" }
        ZipFile(archive.toFile()).use { zip ->
            val entry = zip.getEntry("manifest.json")
                ?: error("no manifest.json inside ${archive.fileName}")
            val text = zip.getInputStream(entry).use { it.readBytes().decodeToString() }
            return json.decodeFromString<LegacyManifest>(text)
        }
    }

    private class Loaded(val plugin: BasePlugin, val providers: List<MainAPI>)

    private val loaded = mutableListOf<Loaded>()

    /**
     * Unregisters everything loaded so far, mirroring `PluginManager.unloadPlugin`.
     *
     * `APIHolder` is a process-wide singleton, so without this every reload would append
     * another set of providers and keep the previous extension's class loader reachable.
     */
    fun unloadAll() {
        synchronized(loaded) {
            for (entry in loaded) {
                runCatching { entry.plugin.beforeUnload() }
                APIHolder.allProviders.removeAll(entry.providers)
            }
            loaded.clear()
        }
    }

    fun load(archive: Path): LegacyLoad {
        // Same one-time runtime setup the Android app performs in CommonActivity.
        initNewPipe()

        val manifest = manifestOf(archive)
        val jar = DexTranslator.translate(archive)

        val loader = URLClassLoader(
            arrayOf(jar.toUri().toURL()),
            LegacyExtensions::class.java.classLoader,
        )

        val pluginClass = try {
            loader.loadClass(manifest.pluginClassName)
        } catch (t: Throwable) {
            throw IllegalStateException(
                "${manifest.pluginClassName} could not be loaded: ${t.message ?: t::class.simpleName}",
                t,
            )
        }

        val instance = try {
            pluginClass.getDeclaredConstructor().newInstance()
        } catch (t: Throwable) {
            throw IllegalStateException(
                "${manifest.pluginClassName} could not be instantiated: ${t.cause?.message ?: t.message}",
                t,
            )
        }

        val plugin = instance as? BasePlugin
            ?: error("${manifest.pluginClassName} is not a BasePlugin")

        val before = identitySetOf(APIHolder.allProviders)

        plugin.filename = archive.toAbsolutePath().toString()
        try {
            plugin.load()
        } catch (t: Throwable) {
            throw IllegalStateException(
                "${manifest.name ?: manifest.pluginClassName} failed to load: ${t.message ?: t::class.simpleName}",
                t,
            )
        }

        val added = APIHolder.allProviders.filter { provider -> !before.contains(provider) }
        require(added.isNotEmpty()) {
            "${manifest.name ?: manifest.pluginClassName} loaded but registered no providers"
        }

        synchronized(loaded) { loaded += Loaded(plugin, added) }

        return LegacyLoad(manifest, added, loader)
    }

    private fun identitySetOf(providers: List<MainAPI>): Set<MainAPI> =
        Collections.newSetFromMap(IdentityHashMap<MainAPI, Boolean>()).apply { addAll(providers) }
}
