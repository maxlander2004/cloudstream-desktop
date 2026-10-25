package app.cloudstream.desktop.core

import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Kept in step with the release tags this repository publishes. */
object AppVersion {
    const val VALUE = "1.1.2"
}

data class UpdateCheck(
    val version: String,
    val url: String,
    val prerelease: Boolean,
    val newer: Boolean,
)

/**
 * The Updates and Backup settings check this project's own GitHub releases — the same flow
 * the Android app performs against the CloudStream repository.
 */
object AppUpdates {
    const val REPOSITORY = "maxlander2004/cloudstream-desktop"

    suspend fun check(includePrerelease: Boolean): UpdateCheck? {
        val endpoint = if (includePrerelease) {
            "https://api.github.com/repos/$REPOSITORY/releases?per_page=1"
        } else {
            "https://api.github.com/repos/$REPOSITORY/releases/latest"
        }

        val element = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .parseToJsonElement(Http.get(endpoint))

        val release = if (includePrerelease) {
            runCatching { element.jsonArray.firstOrNull() }.getOrNull() ?: return null
        } else {
            element
        }.jsonObject

        val tag = release["tag_name"]?.jsonPrimitive?.content ?: return null
        val url = release["html_url"]?.jsonPrimitive?.content.orEmpty()
        val prerelease = release["prerelease"]?.jsonPrimitive?.boolean ?: false
        val version = tag.removePrefix("v")

        return UpdateCheck(
            version = version,
            url = url,
            prerelease = prerelease,
            newer = isNewer(version, AppVersion.VALUE),
        )
    }

    /** "1.2.0" is newer than "1.1.0"; segments compared numerically. */
    internal fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left > right
        }
        return false
    }
}
