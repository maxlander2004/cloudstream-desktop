package app.cloudstream.desktop.core

import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties
import java.nio.file.Files

/**
 * All user preferences, persisted as a plain properties file next to the extensions folder —
 * the same role DataStore plays in the Android app. Every key, default and value range here
 * mirrors the upstream mobile source (`app/src/main/res/xml/settings_*.xml` and the compose
 * settings screens) so a value is literally the original app's setting running on the desktop.
 */
class Settings {
    private val file: Path =
        Paths.get(System.getProperty("user.home"), ".cloudstream-desktop", "settings.properties")

    private val props = Properties()

    init {
        runCatching { Files.newInputStream(file).use { props.load(it) } }
    }

    private fun get(key: String, default: String) = props.getProperty(key, default)
    private fun set(key: String, value: String) {
        props.setProperty(key, value)
        save()
    }
    private fun getInt(key: String, default: Int) = get(key, default.toString()).toIntOrNull() ?: default
    private fun getBool(key: String, default: Boolean) = get(key, default.toString()).toBooleanStrictOrNull() ?: default

    /** Generic stored switch for the original's player/layout preferences. */
    fun flag(key: String, default: Boolean): Boolean = getBool(key, default)
    fun flag(key: String, default: Boolean, value: Boolean) = set(key, value.toString())

    private fun getList(key: String, default: String = ""): List<String> =
        get(key, default).split(',').map { it.trim() }.filter { it.isNotEmpty() }

    // ---------------------------------------------------------------- Player
    /**
     * The original's *Preferred video player*: a command line, exactly what the app's
     * player preference means once playback is handed to an external player on desktop.
     */
    var playerCommand: String
        get() = get("player.command", "")
        set(value) = set("player.command", value)

    /** settings_player.xml first row: Update watch progress (episode_sync_enabled, default true). */
    var episodeSync: Boolean
        get() = getBool("player.episodeSync", true)
        set(value) = set("player.episodeSync", value.toString())

    /** double_tap_seek_time_key — SeekBar 5..60 step 5, default 10. */
    var seekAmount: Int
        get() = getInt("player.seekAmount", 10).coerceIn(5, 60)
        set(value) = set("player.seekAmount", value.coerceIn(5, 60).toString())

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
    /** settings_general.xml: Download path (download_path_pref). */
    var downloadsDir: String
        get() = get("downloads.dir", Paths.get(System.getProperty("user.home"), "Downloads", "CloudStream").toString())
        set(value) = set("downloads.dir", value)

    /** Parallel downloads — SeekBar 1..10, default 3. */
    var parallelDownloads: Int
        get() = getInt("downloads.parallel", 3).coerceIn(1, 10)
        set(value) = set("downloads.parallel", value.coerceIn(1, 10).toString())

    /** Concurrent connections — SeekBar 1..10, default 3. */
    var concurrentConnections: Int
        get() = getInt("downloads.connections", 3).coerceIn(1, 10)
        set(value) = set("downloads.connections", value.coerceIn(1, 10).toString())

    /** "originalUrl=replacementUrl"; Clone site bypass. */
    var cloneSite: String
        get() = get("bypass.cloneSite", "")
        set(value) = set("bypass.cloneSite", value.trim())

    /**
     * DNS over HTTPS — the original's dns_pref_values array: 0 None, 1 Google, 2 Cloudflare,
     * 4 AdGuard, 5 DNS.WATCH, 6 Quad9, 7 DNS.SB, 8 Canadian Shield (3 is commented out upstream).
     */
    var dns: String
        get() = get("bypass.dns", "0")
        set(value) = set("bypass.dns", value)

    /** jsDelivr/GitHub Proxy switch (jsdelivr_proxy_key, default false). */
    var githubProxy: Boolean
        get() = getBool("bypass.githubProxy", false)
        set(value) = set("bypass.githubProxy", value.toString())

    /** "Give a benene to the devs" — the original's banana counter. */
    var bananas: Int
        get() = getInt("general.bananas", 0)
        set(value) = set("general.bananas", value.toString())

    // ---------------------------------------------------------------- Providers
    /** Empty means every language; otherwise provider languages, lowercase. */
    var providerLanguages: List<String>
        get() = getList("providers.languages")
        set(value) = set("providers.languages", value.joinToString(",") { it.trim().lowercase() }.trim(','))

    /** Empty means all media; otherwise ItemType names, uppercase. */
    var preferredMedia: List<String>
        get() = getList("providers.media")
        set(value) = set("providers.media", value.joinToString(",") { it.trim().uppercase() }.trim(','))

    /**
     * Display Dubbed/Subbed Anime — the original's multiselect over DubStatus:
     * None / Dub / Sub (display_sub_key). Empty means none selected.
     */
    var displayDubSub: List<String>
        get() = getList("providers.dubsub", "Dub,Sub")
        set(value) = set("providers.dubsub", value.joinToString(",") { it.trim() }.trim(','))

    /** enable_nsfw_on_providers_key, default false. */
    var nsfwEnabled: Boolean
        get() = getBool("providers.nsfw", false)
        set(value) = set("providers.nsfw", value.toString())

    // ---------------------------------------------------------------- Layout
    /** app_theme_key — the original's theme names; desktop renders Dark/Flashbang as light. */
    var appTheme: String
        get() = get("layout.theme", "Dark")
        set(value) = set("layout.theme", value)

    /** primary_color_key — the original's themes_overlay_names. */
    var primaryColor: String
        get() = get("layout.primaryColor", "Normal")
        set(value) = set("layout.primaryColor", value)

    /** app_layout_key — Automatic / Phone / TV / Emulator; stored like the original. */
    var appLayout: String
        get() = get("layout.appLayout", "Automatic")
        set(value) = set("layout.appLayout", value)

    /** poster_size_key — SeekBar 0..15, default 0; larger means larger posters. */
    var posterSize: Int
        get() = getInt("layout.posterSize", 0).coerceIn(0, 15)
        set(value) = set("layout.posterSize", value.coerceIn(0, 15).toString())

    /** bottom_title_key, default true: put the title under the poster. */
    var posterTitleUnder: Boolean
        get() = getBool("layout.posterTitle", true)
        set(value) = set("layout.posterTitle", value.toString())

    /** overscan_key — SeekBar 0..100, TV only in the original; stored for fidelity. */
    var overscan: Int
        get() = getInt("layout.overscan", 0).coerceIn(0, 100)
        set(value) = set("layout.overscan", value.coerceIn(0, 100).toString())

    /** advanced_search (default true). */
    var advancedSearch: Boolean
        get() = getBool("layout.advancedSearch", true)
        set(value) = set("layout.advancedSearch", value.toString())

    /** search_suggestions_enabled (default true). */
    var searchSuggestions: Boolean
        get() = getBool("layout.searchSuggestions", true)
        set(value) = set("layout.searchSuggestions", value.toString())

    /** show_trailers_key (default true). */
    var trailersEnabled: Boolean
        get() = getBool("layout.trailers", true)
        set(value) = set("layout.trailers", value.toString())

    /** show_kitsu_posters_key (default true). */
    var kitsuPosters: Boolean
        get() = getBool("layout.kitsuPosters", true)
        set(value) = set("layout.kitsuPosters", value.toString())

    /** show_cast_in_details_key (default true). */
    var showCast: Boolean
        get() = getBool("layout.castPanel", true)
        set(value) = set("layout.castPanel", value.toString())

    /** show_fillers_key (default false). */
    var showFillers: Boolean
        get() = getBool("layout.fillers", false)
        set(value) = set("layout.fillers", value.toString())

    /** show_player_metadata_key (default true). */
    var playerMetadataOverlay: Boolean
        get() = getBool("layout.playerMetadata", true)
        set(value) = set("layout.playerMetadata", value.toString())

    /** tv_layout_clock_key (default false, TV only upstream). */
    var showClock: Boolean
        get() = getBool("layout.showClock", false)
        set(value) = set("layout.showClock", value.toString())

    /** random_button_key (default false) — Random Button on Homepage and Library. */
    var randomButton: Boolean
        get() = getBool("layout.randomButton", false)
        set(value) = set("layout.randomButton", value.toString())

    /** confirm_exit_key — Auto (-1) / Show (0) / Don't Show (1), default Auto. */
    var confirmExit: String
        get() = get("layout.confirmExit", "-1")
        set(value) = set("layout.confirmExit", value)

    /** pref_filter_search_quality_key — quality labels hidden from search results. */
    var filterSearchQuality: List<String>
        get() = getList("layout.filterQuality")
        set(value) = set("layout.filterQuality", value.joinToString(",") { it.trim() }.trim(','))

    /** poster_ui_key — which elements render on a poster (rating, episode, title, hd, dub, sub). */
    var posterElements: List<String>
        get() = getList("layout.posterElements", "rating,episode,title,hd,dub,sub")
        set(value) = set("layout.posterElements", value.joinToString(",") { it.trim().lowercase() }.trim(','))

    // ---------------------------------------------------------------- Updates and Backup
    /** auto_update_key — Show app updates, default true. */
    var showAppUpdates: Boolean
        get() = getBool("updates.checkOnStartup", true)
        set(value) = set("updates.checkOnStartup", value.toString())

    /** install_prerelease_key. */
    var installPrerelease: Boolean
        get() = getBool("updates.prerelease", false)
        set(value) = set("updates.prerelease", value.toString())

    /** auto_update_plugins_key — Automatic plugin updates, default true. */
    var autoPluginUpdates: Boolean
        get() = getBool("plugins.autoUpdate", true)
        set(value) = set("plugins.autoUpdate", value.toString())

    /** auto_download_plugins_key — Automatically download plugins (default off upstream). */
    var autoDownloadPlugins: Boolean
        get() = getBool("plugins.autoInstallAll", false)
        set(value) = set("plugins.autoInstallAll", value.toString())

    /** backup_path_key. */
    var backupDir: String
        get() = get("backup.dir", Paths.get(System.getProperty("user.home"), ".cloudstream-desktop", "backup").toString())
        set(value) = set("backup.dir", value)

    /** automatic_backup_key — periodic_work_values: 0/3/6/12/24/72/168 hours, default 0 (None). */
    var backupFrequency: Int
        get() = getInt("backup.frequency", 0)
        set(value) = set("backup.frequency", value.toString())

    // ---------------------------------------------------------------- Accounts and Security
    /** skip_startup_account_select_key (default false). */
    var skipAccountSelection: Boolean
        get() = getBool("accounts.skipSelection", false)
        set(value) = set("accounts.skipSelection", value.toString())

    companion object {
        /** dns_pref × dns_pref_values from the upstream array.xml, as name to stored value. */
        val dnsProviders = linkedMapOf(
            "0" to "None",
            "1" to "Google",
            "2" to "Cloudflare",
            "4" to "AdGuard",
            "5" to "DNS.WATCH",
            "6" to "Quad9",
            "7" to "DNS.SB",
            "8" to "Canadian Shield",
        )

        /** themes_names from array.xml; the desktop theme maps Flashbang to light. */
        val themeNames = listOf(
            "Dark", "Gray", "Amoled", "Flashbang", "System",
            "Material You", "Dracula", "Lavender Dreams", "Silent Blue",
        )

        /** themes_overlay_names — Primary color choices, with the desktop accent per name. */
        val primaryColors = linkedMapOf(
            "Normal" to 0xFF3D50FA,
            "Dandelion Yellow" to 0xFFF5C518,
            "Carnation Pink" to 0xFFFF7EB6,
            "Orange" to 0xFFFF8A00,
            "Dark Green" to 0xFF2E7D32,
            "Maroon" to 0xFF800000,
            "Navy Blue" to 0xFF1A237E,
            "Grey" to 0xFF9E9E9E,
            "White" to 0xFFFFFFFF,
            "Cool Blue" to 0xFF2196F3,
            "Brown" to 0xFF795548,
            "Cool" to 0xFF00BCD4,
            "Fire" to 0xFFFF5722,
            "Burple" to 0xFF673AB7,
        )

        /** confirm_exit values → labels (array.xml). */
        val confirmExitOptions = linkedMapOf(
            "-1" to "Auto",
            "0" to "Show",
            "1" to "Don't Show",
        )

        /** periodic_work values → labels (array.xml: None, 3h, 6h, 12h, 24h, 3d, …). */
        val backupFrequencies = linkedMapOf(
            "0" to "None", "3" to "3h", "6" to "6h", "12" to "12h",
            "24" to "24h", "72" to "3d", "168" to "7d",
        )

        /** app_layout choices (array.xml). */
        val appLayouts = linkedMapOf(
            "Automatic" to "Automatic",
            "Phone" to "Phone",
            "TV" to "TV",
            "Emulator" to "Emulator",
        )

        /** SearchQuality labels — the original's Hide selected video quality dialog. */
        val searchQualities = listOf(
            "BluRay", "Cam", "Cam Rip", "DVD", "HD", "HQ", "HD Cam", "Telecine",
            "Telesync", "WorkPrint", "SD", "4K", "UHD", "SDR", "HDR", "WebRip",
        )

        /** The upstream legal notice text (donottranslate-strings.xml). */
        const val LEGAL_NOTICE =
            "Any legal issues regarding the content on this application should be taken up " +
                "with the actual file hosts and providers themselves as we are not affiliated " +
                "with them."
    }

    private fun save() {
        runCatching {
            Files.createDirectories(file.parent)
            Files.newOutputStream(file).use { props.store(it, "CloudStream Desktop") }
        }
    }
}
