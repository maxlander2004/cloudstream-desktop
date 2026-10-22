package app.cloudstream.desktop.core

import app.cloudstream.desktop.api.Catalog
import app.cloudstream.desktop.core.legacy.LegacyCatalog
import app.cloudstream.desktop.core.legacy.LegacyExtensions
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

@Serializable
data class PluginManifest(
    val name: String? = null,
    val version: Int? = null,
    val description: String? = null,
    @SerialName("pluginClassName") val pluginClassName: String,
    val requiresResources: Boolean = false,
)

data class LoadedExtension(
    val manifest: PluginManifest,
    val catalog: Catalog,
    val file: Path,
    /** True when this came from an original CloudStream `.cs`/`.cs3` archive. */
    val legacy: Boolean = false,
)

data class ExtensionLoadResult(
    val loaded: List<LoadedExtension> = emptyList(),
    val errors: List<String> = emptyList(),
)

/**
 * Loads extensions from the `~/.cloudstream-desktop/extensions` folder.
 *
 * Two kinds are supported and they share one code path in the UI:
 *
 *  - Original CloudStream archives (`.cs`, `.cs3`, `.apk`). These carry Dalvik bytecode and
 *    are loaded by [LegacyExtensions] through [LegacyCatalog], one catalog per provider the
 *    plugin registers. This is how the existing extension ecosystem is used.
 *  - Desktop `.jar` extensions implementing [Catalog], loaded with `URLClassLoader`.
 */
object ExtensionHost {
    private val json = Json { ignoreUnknownKeys = true }

    fun extensionsDir(): Path =
        Paths.get(System.getProperty("user.home"), ".cloudstream-desktop", "extensions")

    fun ensureExtensionsDir(): Path {
        val dir = extensionsDir()
        runCatching { Files.createDirectories(dir) }
        return dir
    }

    /**
     * Copies a downloaded extension into the extensions folder. The copy is validated by
     * loading it there, so a broken archive is reported instead of silently installed.
     */
    fun install(source: Path): Result<LoadedExtension> = runCatching {
        require(Files.isRegularFile(source)) { "${source.fileName} is not a file" }
        require(isSupported(source)) {
            "${source.fileName} is not an extension (expected .cs, .cs3, .apk or .jar)"
        }

        val target = ensureExtensionsDir().resolve(source.fileName)
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)

        loadAll().let { result ->
            result.loaded.firstOrNull { it.file == target }
                ?: run {
                    Files.deleteIfExists(target)
                    error(result.errors.firstOrNull { it.startsWith(target.fileName.toString()) }
                        ?: "${source.fileName} does not contain a loadable extension")
                }
        }
    }

    fun isSupported(file: Path): Boolean =
        LegacyExtensions.looksLikeLegacy(file) ||
            file.fileName.toString().endsWith(".jar", ignoreCase = true)

    fun loadAll(): ExtensionLoadResult {
        // Providers live in a process-wide registry; drop the previous set first.
        LegacyExtensions.unloadAll()

        val dir = ensureExtensionsDir()
        val loaded = mutableListOf<LoadedExtension>()
        val errors = mutableListOf<String>()

        val archives = runCatching {
            Files.list(dir).use { stream ->
                stream.filter { Files.isRegularFile(it) && isSupported(it) }
                    .sorted()
                    .toList()
            }
        }.getOrElse {
            return ExtensionLoadResult(errors = listOf("Could not read ${dir}: ${it.message}"))
        }

        for (archive in archives) {
            try {
                if (LegacyExtensions.looksLikeLegacy(archive)) {
                    loaded += loadLegacy(archive)
                } else {
                    loaded += loadNative(archive)
                }
            } catch (t: Throwable) {
                val message = t.message ?: t::class.simpleName ?: "unknown error"
                errors += "${archive.fileName}: $message"
            }
        }

        return ExtensionLoadResult(loaded, errors)
    }

    private fun loadNative(jar: Path): LoadedExtension {
        val loader = URLClassLoader(arrayOf(jar.toUri().toURL()), Catalog::class.java.classLoader)
        val manifest = loader.getResourceAsStream("manifest.json")?.use { stream ->
            json.decodeFromString<PluginManifest>(stream.readBytes().decodeToString())
        } ?: error("no manifest.json inside the archive")

        val instance = loader.loadClass(manifest.pluginClassName)
            .getDeclaredConstructor()
            .newInstance() as? Catalog
            ?: error("${manifest.pluginClassName} does not implement Catalog")

        return LoadedExtension(manifest, instance, jar)
    }

    /** One CloudStream plugin can register several providers; each becomes its own catalog. */
    private fun loadLegacy(archive: Path): List<LoadedExtension> {
        val result = LegacyExtensions.load(archive)
        val pluginName = result.manifest.name ?: archive.fileName.toString()

        return result.providers.map { provider ->
            LoadedExtension(
                manifest = PluginManifest(
                    name = provider.name,
                    version = result.manifest.version,
                    pluginClassName = result.manifest.pluginClassName,
                ),
                catalog = LegacyCatalog(provider, pluginName),
                file = archive,
                legacy = true,
            )
        }
    }
}
