package app.cloudstream.desktop.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The Updates and Backup settings' data backup: one zip containing the settings file and the
 * installed extension archives, restorable on another machine.
 */
object Backup {
    fun settingsFile(): Path =
        Paths.get(System.getProperty("user.home"), ".cloudstream-desktop", "settings.properties")

    /** Writes a timestamped zip into [dir] and returns it. */
    fun create(dir: String, extensionsDir: Path): Path {
        val target = Paths.get(dir)
        Files.createDirectories(target)
        val zip = target.resolve(
            "cloudstream-backup-" + LocalDateTime.now().toString().replace(':', '-') + ".zip",
        )

        ZipOutputStream(Files.newOutputStream(zip)).use { out ->
            settingsFile().takeIf { Files.isRegularFile(it) }?.let { file ->
                out.putNextEntry(ZipEntry("settings.properties"))
                Files.copy(file, out)
                out.closeEntry()
            }
            if (Files.isDirectory(extensionsDir)) {
                Files.walk(extensionsDir).use { stream ->
                    stream.filter { Files.isRegularFile(it) }.forEach { file ->
                        val name = "extensions/" + extensionsDir.relativize(file).joinToString("/") { it.toString() }
                        out.putNextEntry(ZipEntry(name))
                        Files.copy(file, out)
                        out.closeEntry()
                    }
                }
            }
        }
        return zip
    }

    /** Restores settings and extensions from [zip]; returns a human-readable summary. */
    fun restore(zip: Path, extensionsDir: Path): String {
        var settings = 0
        var extensions = 0
        ZipInputStream(Files.newInputStream(zip)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                when {
                    entry.name == "settings.properties" && !entry.isDirectory -> {
                        Files.createDirectories(settingsFile().parent)
                        Files.copy(input, settingsFile(), StandardCopyOption.REPLACE_EXISTING)
                        settings++
                    }

                    entry.name.startsWith("extensions/") && !entry.isDirectory -> {
                        val relative = entry.name.removePrefix("extensions/")
                        if (relative.isNotBlank()) {
                            Files.createDirectories(extensionsDir)
                            Files.copy(
                                input,
                                extensionsDir.resolve(relative),
                                StandardCopyOption.REPLACE_EXISTING,
                            )
                            extensions++
                        }
                    }
                }
                input.closeEntry()
            }
        }
        check(settings + extensions > 0) { "that file contains no CloudStream backup data" }
        return "Restored ${settings} settings file and ${extensions} extension(s) — restart to apply"
    }
}
