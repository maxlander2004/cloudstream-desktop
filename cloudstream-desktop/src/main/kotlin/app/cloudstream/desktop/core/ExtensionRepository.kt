package app.cloudstream.desktop.core

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** The `repo.json` a repository publishes: a name plus the plugin lists it is built from. */
@Serializable
data class RepositoryManifest(
    @SerialName("name") val name: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("manifestVersion") val manifestVersion: Int = 1,
    @SerialName("pluginLists") val pluginLists: List<String> = emptyList(),
)

/**
 * One extension offered by a repository. Same shape the Android app parses
 * (`com.lagradost.cloudstream3.plugins.SitePlugin`), so any official repository works as-is.
 */
@Serializable
data class RepositoryPlugin(
    /** Url of the `.cs3` archive. */
    @SerialName("url") val url: String,
    @SerialName("name") val name: String,
    /** Stable identifier; the archive is installed under this name. */
    @SerialName("internalName") val internalName: String,
    /** Increases on every release; a higher value than the installed one means an update. */
    @SerialName("version") val version: Int = 1,
    @SerialName("apiVersion") val apiVersion: Int = 1,
    /** 0: down, 1: ok, 2: slow, 3: beta. Repositories use it to disable providers remotely. */
    @SerialName("status") val status: Int = 1,
    @SerialName("authors") val authors: List<String> = emptyList(),
    @SerialName("description") val description: String? = null,
    @SerialName("repositoryUrl") val repositoryUrl: String? = null,
    @SerialName("tvTypes") val tvTypes: List<String>? = null,
    @SerialName("language") val language: String? = null,
    @SerialName("iconUrl") val iconUrl: String? = null,
    @SerialName("fileSize") val fileSize: Long? = null,
    @SerialName("fileHash") val fileHash: String? = null,
) {
    val statusLabel: String
        get() = when (status) {
            0 -> "Down"
            2 -> "Slow"
            3 -> "Beta"
            else -> "Ok"
        }
}

data class RepositoryState(
    val name: String,
    val description: String?,
    val plugins: List<RepositoryPlugin>,
)

/** Several repositories merged into what the Extensions screen lists. */
data class RepositorySet(
    val repositories: List<RepositoryState>,
    /** Repositories that could not be fetched, as url to reason. */
    val failures: List<Pair<String, String>> = emptyList(),
) {
    val pluginCount: Int get() = repositories.sumOf { it.plugins.size }
}

/**
 * The extension repository exactly as the original CloudStream app uses it: fetch a `repo.json`
 * manifest, follow its plugin lists, and download the `.cs3` files they advertise. The official
 * repository is [OFFICIAL_REPOSITORY] and ships only legal sources.
 */
object ExtensionRepository {
    const val OFFICIAL_REPOSITORY =
        "https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json"

    /** The repository the `megarepo` shortcode resolves to. */
    const val MEGA_REPOSITORY =
        "https://raw.githubusercontent.com/self-similarity/MegaRepo/builds/repo.json"

    /**
     * The only extension MegaRepo ships. It exists to add repositories on Android, where it
     * is built against app internals the desktop host does not have, so after the expansion
     * below has done its job natively the entry is not offered as an install.
     */
    const val MEGA_PLUGIN = "MegaProvider"

    /**
     * Every known community repository, in the same database the Mega plugin reads on Android.
     * That plugin is built against Android-only app internals and cannot run here, so the app
     * does its job itself when the mega repository is requested.
     */
    const val COMMUNITY_REPOSITORIES =
        "https://raw.githubusercontent.com/recloudstream/cs-repos/master/repos-db.json"

    /** Same rule upstream uses to tell a shortcode from a url. */
    private val SHORTCODE = Regex("^[a-zA-Z0-9!_-]+$")

    /** Upstream accepts `cloudstreamrepo://` and `https://cs.repo/` shortcuts too. */
    fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return OFFICIAL_REPOSITORY

        val stripped = when (trimmed) {
            "https://cs.repo", "http://cs.repo" -> ""
            else -> trimmed
                .removePrefix("cloudstreamrepo://")
                .removePrefix("https://cs.repo/")
                .removePrefix("http://cs.repo/")
        }
        if (stripped.isBlank()) return OFFICIAL_REPOSITORY
        return if (stripped.startsWith("http://") || stripped.startsWith("https://")) {
            stripped
        } else {
            "https://$stripped"
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Turns what was typed in the repository field into a url. A bare word is a shortcode and
     * is resolved through the same redirect services upstream uses, so `megarepo` gives the
     * Mega repository exactly like the Android app.
     */
    suspend fun resolve(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return OFFICIAL_REPOSITORY
        if (!SHORTCODE.matches(trimmed)) return normalizeUrl(trimmed)

        val target = if (trimmed.startsWith("!")) {
            Http.redirectTarget("https://py.md/${trimmed.removePrefix("!")}")
        } else {
            Http.redirectTarget("https://cutt.ly/$trimmed")
        }.orEmpty()

        return if (target.startsWith("http://") || target.startsWith("https://")) {
            target
        } else {
            normalizeUrl(trimmed)
        }
    }

    /**
     * Fetches every configured repository at once and merges them into one listing. The
     * `megarepo` shortcode expands to the whole community database, like the Mega plugin does
     * on Android; one extension offered by several repositories is listed once, and a
     * repository that cannot be reached is reported instead of failing the rest.
     */
    suspend fun fetchAll(inputs: List<String>): RepositorySet = coroutineScope {
        val resolved = inputs.filter { it.isNotBlank() }
            .ifEmpty { listOf(OFFICIAL_REPOSITORY) }
            .map { resolve(it) }
        val expandMega = resolved.any { it == MEGA_REPOSITORY }

        val urls = resolved
            .flatMap { url ->
                if (url == MEGA_REPOSITORY) communityRepositories() + url else listOf(url)
            }
            .distinct()

        val states = java.util.Collections.synchronizedList(mutableListOf<RepositoryState>())
        val failures = java.util.Collections.synchronizedList(mutableListOf<Pair<String, String>>())
        val gate = Semaphore(8)

        urls.map { url ->
            async {
                gate.withPermit {
                    runCatching { fetchOne(url) }
                        .onSuccess { states += it }
                        .onFailure { failures += url to (it.message ?: "unreachable") }
                }
            }
        }.awaitAll()

        val merged = mergeRepositories(states.toList()).let { repositories ->
            if (!expandMega) return@let repositories
            repositories.mapNotNull { state ->
                val plugins = state.plugins.filter { it.internalName != MEGA_PLUGIN }
                if (plugins.isEmpty()) null else state.copy(plugins = plugins)
            }
        }

        RepositorySet(
            repositories = merged,
            failures = failures.toList(),
        )
    }

    /** One repository: either a `repo.json` manifest or a bare plugin list. */
    private suspend fun fetchOne(url: String): RepositoryState {
        val element = json.parseToJsonElement(Http.get(url))

        if (element is JsonArray) {
            val plugins = json.decodeFromJsonElement<List<RepositoryPlugin>>(element)
            return RepositoryState(name = url.substringAfterLast('/'), description = null, plugins = plugins)
        }

        val manifest = json.decodeFromJsonElement<RepositoryManifest>(element)
        check(manifest.pluginLists.isNotEmpty()) { "repository has no plugin lists" }

        val plugins = manifest.pluginLists.take(10).flatMap { listUrl ->
            runCatching {
                json.decodeFromString<List<RepositoryPlugin>>(Http.get(listUrl))
            }.getOrElse { emptyList() }
        }
        check(plugins.isNotEmpty()) { "no extensions found in this repository" }

        return RepositoryState(
            name = manifest.name ?: url.substringAfterLast('/'),
            description = manifest.description,
            plugins = plugins,
        )
    }

    /** The community database the Android Mega plugin reads, as repository urls. */
    private suspend fun communityRepositories(): List<String> =
        runCatching {
            json.parseToJsonElement(Http.get(COMMUNITY_REPOSITORIES)).let { element ->
                (element as? JsonArray).orEmpty().mapNotNull { entry ->
                    when (entry) {
                        is JsonPrimitive -> entry.contentOrNull
                        is JsonObject -> entry["url"]?.let {
                            (it as? JsonPrimitive)?.contentOrNull
                        }
                        else -> null
                    }
                }
            }
        }.getOrElse { emptyList() }

    /** One entry per extension across all repositories; the first one offering it wins. */
    internal fun mergeRepositories(states: List<RepositoryState>): List<RepositoryState> {
        val seen = mutableSetOf<String>()
        return states.mapNotNull { state ->
            val plugins = state.plugins.filter { seen.add(it.internalName) }
            if (plugins.isEmpty()) null else state.copy(plugins = plugins)
        }
    }

    /** `sha256-<hex digest>`, the format repositories publish in `fileHash`. */
    fun sha256(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            var read = input.read(buffer)
            while (read != -1) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return "sha256-" + digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Downloads an extension into the extensions folder as `<internalName>.cs3`. The size and
     * hash published by the repository are verified before the file is moved in place, so a
     * tampered or truncated download fails instead of being installed. Installing again with a
     * higher [RepositoryPlugin.version] is an update.
     */
    suspend fun install(plugin: RepositoryPlugin, extensionsDir: Path): Path {
        Files.createDirectories(extensionsDir)
        val temp = Files.createTempFile(extensionsDir, plugin.internalName, ".download")
        try {
            val bytes = Http.getBytes(plugin.url)
            Files.write(temp, bytes)

            plugin.fileSize?.let { expected ->
                check(bytes.size.toLong() == expected) {
                    "size mismatch: got ${bytes.size} bytes, repository says $expected"
                }
            }
            plugin.fileHash?.let { expected ->
                val actual = sha256(temp)
                check(actual.equals(expected, ignoreCase = true)) {
                    "hash mismatch: got $actual, repository says $expected"
                }
            }

            val target = extensionsDir.resolve("${sanitize(plugin.internalName)}.cs3")
            try {
                Files.move(
                    temp,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            }
            return target
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    private fun sanitize(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
