package app.cloudstream.desktop.core

import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties
import java.nio.file.Files

/**
 * All user preferences, persisted as a plain properties file next to the extensions folder —
 * the same role DataStore plays in the Android app. Keys mirror the original settings names
 * so a value is recognisable from the app it was copied from.
 */
class Settings {
    private val file: Path =
        Paths.get(System.getProperty("user.home"), ".cloudstream-desktop", "settings.properties")

    private val props = Properties()

    init {
        runCatching { Files.newInputStream(file).use { props.load(it) } }
        // The HTTP layer reads these toggles per request, and the resolver reads the DoH
        // choice per lookup, so both are handed this instance as soon as one exists.
        Http.use(this)
        Doh.configure(this)
    }

    private fun get(key: String, default: String) = props.getProperty(key, default)
    private fun set(key: String, value: String) {
        props.setProperty(key, value)
        save()
    }
    private fun getInt(key: String, default: Int) = get(key, default.toString()).toIntOrNull() ?: default
    private fun getBool(key: String, default: Boolean) = get(key, default.toString()).toBooleanStrictOrNull() ?: default

    // ---------------------------------------------------------------- Player
    var playerCommand: String
        get() = get("player.command", "")
        set(value) = set("player.command", value)

    /** Play inside the app with the embedded VLC engine instead of an external player. */
    var inAppPlayer: Boolean
        get() = getBool("player.inApp", true)
        set(value) = set("player.inApp", value.toString())

    /** Upstream's *Autoplay next episode*. */
    var autoplayNext: Boolean
        get() = getBool("player.autoplayNext", true)
        set(value) = set("player.autoplayNext", value.toString())

    /** Upstream's *Resume from last position*. */
    var resumePlayback: Boolean
        get() = getBool("player.resume", true)
        set(value) = set("player.resume", value.toString())

    /** Turn a subtitle track on as soon as an episode starts, like upstream's auto-select. */
    var subtitleAutoLoad: Boolean
        get() = getBool("player.subtitleAuto", true)
        set(value) = set("player.subtitleAuto", value.toString())

    /** Preferred subtitle language as a short code; empty accepts the first track offered. */
    var subtitleLanguage: String
        get() = get("player.subtitleLanguage", "")
        set(value) = set("player.subtitleLanguage", value.trim().lowercase())

    /** Where to look for libvlc when VLC is not installed in the usual place. */
    var vlcPath: String
        get() = get("player.vlcPath", "")
        set(value) = set("player.vlcPath", value.trim())

    /** Subtitle search source; "off" until an API key is entered. */
    var subtitleProvider: String
        get() = get("player.subtitleProvider", "off")
        set(value) = set("player.subtitleProvider", value)

    /** SubDL API key, created on the user's own SubDL account. */
    var subdlApiKey: String
        get() = get("player.subdlApiKey", "")
        set(value) = set("player.subdlApiKey", value.trim())

    /** Language subtitle search asks for first. */
    var subtitleSearchLanguage: String
        get() = get("player.subtitleSearchLanguage", "en")
        set(value) = set("player.subtitleSearchLanguage", value.trim().lowercase())

    /** Push watched episodes and bookmarks to the linked AniList/MyAnimeList account. */
    var trackSync: Boolean
        get() = getBool("accounts.sync", true)
        set(value) = set("accounts.sync", value.toString())

    // ---------------------------------------------------------------- Repositories
    /**
     * Repositories listed on the Extensions screen, the official one by default. Shortcodes
     * like `megarepo` are allowed here; they are resolved when the list is fetched.
     */
    var repositoryUrls: List<String>
        get() = (props.getProperty("repository.urls")
            ?: props.getProperty("repository.url")
            ?: ExtensionRepository.OFFICIAL_REPOSITORY)
            .split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf(ExtensionRepository.OFFICIAL_REPOSITORY) }
        set(value) {
            set(
                "repository.urls",
                value.joinToString(";") { it.trim() }.trim(';'),
            )
        }

    // ---------------------------------------------------------------- General
    var downloadsDir: String
        get() = get("downloads.dir", Paths.get(System.getProperty("user.home"), "Downloads", "CloudStream").toString())
        set(value) = set("downloads.dir", value)

    /** How many different items download in parallel, the original's Parallel downloads. */
    var parallelDownloads: Int
        get() = getInt("downloads.parallel", 3).coerceIn(1, 10)
        set(value) = set("downloads.parallel", value.coerceIn(1, 10).toString())

    /** How many ranged connections each download may use — the original's Concurrent connections. */
    var concurrentConnections: Int
        get() = getInt("downloads.connections", 3).coerceIn(1, 10)
        set(value) = set("downloads.connections", value.coerceIn(1, 10).toString())

    /** "originalUrl=replacementUrl"; mirrors upstream's Clone site bypass. */
    var cloneSite: String
        get() = get("bypass.cloneSite", "")
        set(value) = set("bypass.cloneSite", value.trim())

    /** One of [dohProviders] keys; applied to the app's own HTTP lookups. */
    var doh: String
        get() = get("bypass.doh", "off")
        set(value) {
            set("bypass.doh", value)
            // Live: the resolver reads this on every lookup, so no restart is involved.
            Doh.provider = value
        }

    /** Bypass raw.githubusercontent.com blocking through jsDelivr, like the original toggle. */
    var githubProxy: Boolean
        get() = getBool("bypass.githubProxy", false)
        set(value) = set("bypass.githubProxy", value.toString())

    var downloadSubtitles: Boolean
        get() = getBool("downloads.subtitles", false)
        set(value) = set("downloads.subtitles", value.toString())

    var archiveDownloads: Boolean
        get() = getBool("downloads.archive", false)
        set(value) = set("downloads.archive", value.toString())

    // ---------------------------------------------------------------- Providers
    /** Empty means every language; otherwise provider languages, lowercase. */
    var providerLanguages: List<String>
        get() = get("providers.languages", "").split(',').map { it.trim() }.filter { it.isNotEmpty() }
        set(value) = set("providers.languages", value.joinToString(",") { it.trim().lowercase() }.trim(','))

    /** Empty means all media; otherwise ItemType names, uppercase. */
    var preferredMedia: List<String>
        get() = get("providers.media", "").split(',').map { it.trim() }.filter { it.isNotEmpty() }
        set(value) = set("providers.media", value.joinToString(",") { it.trim().uppercase() }.trim(','))

    var displayDubbedSubbed: Boolean
        get() = getBool("providers.dubbed", true)
        set(value) = set("providers.dubbed", value.toString())

    var nsfwEnabled: Boolean
        get() = getBool("providers.nsfw", false)
        set(value) = set("providers.nsfw", value.toString())

    // ---------------------------------------------------------------- Layout
    /** "dark" or "light". */
    var appTheme: String
        get() = get("layout.theme", "dark")
        set(value) = set("layout.theme", value)

    /** 0 = smallest posters (most columns), like the original's slider. */
    var posterSize: Int
        get() = getInt("layout.posterSize", 2).coerceIn(0, 6)
        set(value) = set("layout.posterSize", value.coerceIn(0, 6).toString())

    /** Defaults to the original's look: the title drawn over the poster. */
    var posterTitleUnder: Boolean
        get() = getBool("layout.posterTitle", false)
        set(value) = set("layout.posterTitle", value.toString())

    var accentColor: String
        get() = get("layout.accentColor", "#3D50FA")
        set(value) = set("layout.accentColor", value)

    var advancedSearch: Boolean
        get() = getBool("layout.advancedSearch", true)
        set(value) = set("layout.advancedSearch", value.toString())

    /** Index into the current source list for the chosen homepage provider, like upstream's
     * home provider selector. -1 means use the first source. */
    var homepageProviderIndex: Int
        get() = getInt("layout.homepageProvider", -1)
        set(value) = set("layout.homepageProvider", value.toString())

    // ---------------------------------------------------------------- Updates and Backup
    var showAppUpdates: Boolean
        get() = getBool("updates.checkOnStartup", true)
        set(value) = set("updates.checkOnStartup", value.toString())

    var installPrerelease: Boolean
        get() = getBool("updates.prerelease", false)
        set(value) = set("updates.prerelease", value.toString())

    var autoPluginUpdates: Boolean
        get() = getBool("plugins.autoUpdate", true)
        set(value) = set("plugins.autoUpdate", value.toString())

    var autoDownloadPlugins: Boolean
        get() = getBool("plugins.autoInstallAll", false)
        set(value) = set("plugins.autoInstallAll", value.toString())

    var backupDir: String
        get() = get("backup.dir", Paths.get(System.getProperty("user.home"), ".cloudstream-desktop", "backup").toString())
        set(value) = set("backup.dir", value)

    companion object {
        /** DNS-over-HTTPS resolvers offered in General, by display name. */
        val dohProviders = linkedMapOf(
            "off" to "Off",
            "cloudflare" to "Cloudflare (1.1.1.1)",
            "google" to "Google (8.8.8.8)",
        )
    }

    internal fun save() {
        runCatching {
            Files.createDirectories(file.parent)
            Files.newOutputStream(file).use { props.store(it, "CloudStream Desktop") }
        }
    }
}
