package app.cloudstream.desktop.core

import app.cloudstream.desktop.api.StreamLink
import java.awt.Desktop
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties

/**
 * Playback is delegated to a real desktop player instead of re-implementing ExoPlayer.
 *
 * Upstream's own architecture note says each platform should implement playback natively,
 * so this hands the stream URL to mpv/VLC (or the default browser as a fallback).
 */
object Player {
    fun open(link: StreamLink, command: String): String {
        val configured = command.trim()
        if (configured.isNotEmpty()) {
            return runCatching {
                val parts = configured.split(' ').filter { it.isNotBlank() }
                ProcessBuilder(parts + headerArgs(parts.first(), link.headers) + link.url).start()
                "Sent to ${parts.first()}"
            }.getOrElse { "Could not start \"$configured\": ${it.message}" }
        }

        return runCatching {
            val desktop = Desktop.getDesktop()
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                desktop.browse(URI(link.url))
                "Opened in your default browser"
            } else {
                "No player configured — set one in Settings"
            }
        }.getOrElse { "Could not open the stream: ${it.message}" }
    }

    /**
     * Most extension streams are protected by a Referer check, so pass the source's headers
     * to players that accept them. Unrecognised players just get the URL.
     */
    internal fun headerArgs(executable: String, headers: Map<String, String>): List<String> {
        if (headers.isEmpty()) return emptyList()
        val name = executable.substringAfterLast('/').substringAfterLast('\\').lowercase()

        return when {
            name.contains("mpv") -> listOf(
                "--http-header-fields=" + headers.entries.joinToString(",") { "${it.key}: ${it.value}" },
            )

            name.contains("vlc") -> headers["Referer"]?.let { listOf("--http-referrer=$it") }.orEmpty()

            else -> emptyList()
        }
    }
}

class Settings {
    private val file: Path =
        Paths.get(System.getProperty("user.home"), ".cloudstream-desktop", "settings.properties")

    private val props = Properties()

    init {
        runCatching { Files.newInputStream(file).use { props.load(it) } }
    }

    var playerCommand: String
        get() = props.getProperty("player.command", "")
        set(value) {
            props.setProperty("player.command", value)
            save()
        }

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
            props.setProperty(
                "repository.urls",
                value.joinToString(";") { it.trim() }.trim(';'),
            )
            save()
        }

    private fun save() {
        runCatching {
            Files.createDirectories(file.parent)
            Files.newOutputStream(file).use { props.store(it, "CloudStream Desktop") }
        }
    }
}
