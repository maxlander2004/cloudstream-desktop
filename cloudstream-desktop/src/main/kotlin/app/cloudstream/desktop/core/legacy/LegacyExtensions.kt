package app.cloudstream.desktop.core.legacy

import android.content.Context
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
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
    /**
     * The context extensions receive. The desktop host has no activity, so this is the stand-in
     * the loader already hands to `Plugin.loadPlugin`; a plugin that opens a screen with it is
     * a no-op rather than a crash.
     */
    internal fun context(): Context = CommonActivity.activity ?: Context()

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

    private class Loaded(
        val archive: Path,
        val plugin: BasePlugin,
        /** Providers this plugin contributed; grows if it registers during the batch event. */
        val providers: MutableList<MainAPI>,
        val loader: ClassLoader,
    )

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
                // Providers the plugin added from the batch event are not in that list; they are
                // recognisable by the class loader that defined them.
                APIHolder.allProviders.removeAll { it::class.java.classLoader === entry.loader }
                // Release the translated archive too: on Windows an open jar cannot be
                // deleted, which would break uninstall and in-place updates.
                runCatching { (entry.loader as? URLClassLoader)?.close() }
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
            loadPlugin(plugin, CommonActivity.activity ?: Context())
        } catch (t: Throwable) {
            throw IllegalStateException(
                "${manifest.name ?: manifest.pluginClassName} failed to load: ${t.message ?: t::class.simpleName}",
                t,
            )
        }

        val added = APIHolder.allProviders.filter { provider -> !before.contains(provider) }

        // A plugin that registers nothing is not an error: upstream plugins may contribute only
        // extractors, video-click actions or a settings screen, and some register their
        // providers from `MainActivity.afterPluginsLoadedEvent` instead of from `load()`.
        synchronized(loaded) { loaded += Loaded(archive, plugin, added.toMutableList(), loader) }

        return LegacyLoad(manifest, added, loader)
    }

    /**
     * Fires `MainActivity.afterPluginsLoadedEvent`, like `PluginManager.mainPluginsLoaded`.
     *
     * Upstream fires it once per batch, after every plugin in the batch is registered. The
     * desktop loads archives one at a time, so the host calls this after its own load loop
     * (`ExtensionHost.reload`). Plugins that subscribe to it — a handful register their
     * providers there rather than in `load()` — would otherwise never run.
     */
    fun notifyPluginsLoaded() {
        if (synchronized(loaded) { loaded.isEmpty() }) return
        runCatching { MainActivity.afterPluginsLoadedEvent.invoke(false) }

        // Subscribers register providers while the event runs; give each new provider to the
        // plugin whose class loader defined it, so the UI lists it and unload removes it.
        synchronized(loaded) {
            val known = identitySetOf(loaded.flatMap { it.providers })
            for (provider in APIHolder.allProviders) {
                if (known.contains(provider)) continue
                val owner = loaded.firstOrNull { it.loader === provider::class.java.classLoader }
                owner?.providers?.add(provider)
            }
        }
    }

    /**
     * The providers one archive contributed, including any it registered from the batch event.
     *
     * Call this after [notifyPluginsLoaded]: the whole point of the event is that the set grows
     * after [load] returned.
     */
    fun providersOf(archive: Path): List<MainAPI> = synchronized(loaded) {
        loaded.firstOrNull { it.archive == archive }?.providers?.toList().orEmpty()
    }

    /**
     * Runs a plugin's entry point exactly like `PluginManager.loadPlugin` does.
     *
     * A `Plugin` gets `load(context)`, whose default calls the cross-platform `load()`, so both
     * generations of extension work. Extensions built against the current plugin API override
     * only `load(context)`; calling the plain `load()` on those runs the base class's no-op, the
     * plugin registers no providers and it looks installed-but-broken in the UI.
     */
    internal fun loadPlugin(plugin: BasePlugin, context: Context) {
        if (plugin is Plugin) plugin.load(context) else plugin.load()
    }

    private fun identitySetOf(providers: List<MainAPI>): Set<MainAPI> =
        Collections.newSetFromMap(IdentityHashMap<MainAPI, Boolean>()).apply { addAll(providers) }
}
