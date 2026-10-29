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
import java.util.Comparator
import java.util.zip.ZipFile

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
 * One archive in the extensions folder, whether or not it is currently loaded.
 *
 * This is what the Extensions screen lists, so a disabled extension still shows its name and
 * version instead of looking uninstalled.
 */
data class InstalledArchive(
    val file: Path,
    val name: String,
    val version: Int,
    val enabled: Boolean,
    val legacy: Boolean,
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

    /** Loaders of the loaded `.jar` extensions, closed on reload so the files unlock. */
    private val jarLoaders = mutableListOf<URLClassLoader>()

    fun extensionsDir(): Path =
        Paths.get(System.getProperty("user.home"), ".cloudstream-desktop", "extensions")

    fun ensureExtensionsDir(): Path {
        val dir = extensionsDir()
        runCatching { Files.createDirectories(dir) }
        return dir
    }

    /**
     * `~/.cloudstream-desktop/plugins.disabled`, one file name per line.
     *
     * The Android app keeps the same information in its plugin data and treats a disabled
     * plugin as installed-but-not-loaded; a plain list of names is the desktop equivalent.
     */
    private fun disabledFile(): Path =
        Paths.get(System.getProperty("user.home"), ".cloudstream-desktop", "plugins.disabled")

    fun disabled(): Set<String> = runCatching {
        Files.readAllLines(disabledFile()).map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }.getOrDefault(emptySet())

    /** Enables or disables an extension; the caller reloads afterwards. */
    fun setEnabled(file: Path, enabled: Boolean) {
        val name = file.fileName.toString()
        val current = disabled()
        val next = if (enabled) current - name else current + name
        if (next == current) return

        runCatching {
            Files.createDirectories(disabledFile().parent)
            Files.write(disabledFile(), next.sorted())
        }
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
        // Installing is also how an extension gets re-enabled.
        setEnabled(target, true)

        loadAll().let { result ->
            result.loaded.firstOrNull { it.file == target }
                ?: run {
                    // Best effort: a cleanup problem must never mask why the archive was
                    // rejected (on Windows the copy can be transiently locked).
                    runCatching { Files.deleteIfExists(target) }
                    error(result.errors.firstOrNull { it.startsWith(target.fileName.toString()) }
                        ?: "${source.fileName} does not contain a loadable extension")
                }
        }
    }

    /**
     * Installs an extension from a url, the way the Android app installs from a `.cs3` link.
     * The archive keeps the name the link points at, so a later update replaces it in place.
     */
    suspend fun installFromUrl(url: String): Result<LoadedExtension> = runCatching {
        val trimmed = url.trim()
        require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            "$trimmed is not an http(s) url"
        }

        val remote = trimmed.substringAfterLast('/').substringBefore('?').substringBefore('#')
            .ifBlank { "extension.cs3" }
        val fileName = if (isSupported(Paths.get(remote))) remote else "$remote.cs3"

        val dir = Files.createTempDirectory("cloudstream-extension")
        try {
            val download = dir.resolve(fileName)
            Files.write(download, Http.getBytes(trimmed))
            install(download).getOrThrow()
        } finally {
            runCatching {
                Files.walk(dir).use { paths ->
                    paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
                }
            }
        }
    }

    /**
     * Removes an extension, its providers and its enabled/disabled entry — the desktop
     * equivalent of `PluginManager.deletePlugin`.
     */
    fun uninstall(file: Path): Result<Unit> = runCatching<Unit> {
        require(Files.isRegularFile(file)) { "${file.fileName} is not installed" }

        // Providers and class loaders go first: an open jar cannot be deleted on Windows, and
        // a surviving provider would keep the removed extension reachable.
        release()
        Files.deleteIfExists(file)
        setEnabled(file, true)
        loadAll()
    }

    fun isSupported(file: Path): Boolean =
        LegacyExtensions.looksLikeLegacy(file) ||
            file.fileName.toString().endsWith(".jar", ignoreCase = true)

    /**
     * Every archive in the extensions folder with the name and version from its manifest.
     * Nothing is executed, so this also describes disabled and currently broken extensions.
     */
    fun installed(): List<InstalledArchive> {
        val dir = ensureExtensionsDir()
        val disabled = disabled()

        val archives = runCatching {
            Files.list(dir).use { stream ->
                stream.filter { Files.isRegularFile(it) && isSupported(it) }.sorted().toList()
            }
        }.getOrDefault(emptyList())

        return archives.mapNotNull { file ->
            val legacy = LegacyExtensions.looksLikeLegacy(file)
            val manifest = runCatching { readManifest(file) }.getOrNull() ?: return@mapNotNull null
            InstalledArchive(
                file = file,
                name = manifest.name ?: file.fileName.toString(),
                version = manifest.version ?: 0,
                enabled = file.fileName.toString() !in disabled,
                legacy = legacy,
            )
        }
    }

    private fun readManifest(file: Path): PluginManifest =
        ZipFile(file.toFile()).use { zip ->
            val entry = zip.getEntry("manifest.json")
                ?: error("no manifest.json inside ${file.fileName}")
            json.decodeFromString<PluginManifest>(
                zip.getInputStream(entry).use { it.readBytes().decodeToString() },
            )
        }

    fun loadAll(): ExtensionLoadResult {
        // Providers live in a process-wide registry; drop the previous set first.
        release()

        val dir = ensureExtensionsDir()
        val disabled = disabled()
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
            if (archive.fileName.toString() in disabled) continue
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

    /** Unregisters every provider and closes every archive, so files can be replaced. */
    private fun release() {
        LegacyExtensions.unloadAll()
        synchronized(jarLoaders) {
            jarLoaders.forEach { runCatching { it.close() } }
            jarLoaders.clear()
        }
    }

    private fun loadNative(jar: Path): LoadedExtension {
        val loader = URLClassLoader(arrayOf(jar.toUri().toURL()), Catalog::class.java.classLoader)
        return try {
            val manifest = loader.getResourceAsStream("manifest.json")?.use { stream ->
                json.decodeFromString<PluginManifest>(stream.readBytes().decodeToString())
            } ?: error("no manifest.json inside the archive")

            val instance = loader.loadClass(manifest.pluginClassName)
                .getDeclaredConstructor()
                .newInstance() as? Catalog
                ?: error("${manifest.pluginClassName} does not implement Catalog")

            synchronized(jarLoaders) { jarLoaders += loader }
            LoadedExtension(manifest, instance, jar)
        } catch (t: Throwable) {
            // The loader opens the archive to search it. Leaving it open after a failed load
            // keeps the file locked — on Windows install() could then not remove the rejected
            // copy. Successful loads keep the loader, since the catalog needs its classes.
            runCatching { loader.close() }
            throw t
        }
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
