package app.cloudstream.desktop

import app.cloudstream.desktop.core.AppUpdates
import app.cloudstream.desktop.core.AppVersion
import app.cloudstream.desktop.core.Http
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Live check of the in-app updater against this repository's published releases — the same
 * endpoints the startup update banner and Settings → Updates and Backup call.
 *
 * Run with `./gradlew verifyUpdater`; it needs network access and expects the release for the
 * version in `AppVersion` to be published already, so run it after a release rather than on
 * the build that is about to create one.
 */
fun main() = runBlocking {
    var failures = 0
    fun report(label: String, ok: Boolean, detail: String) {
        println("${label.padEnd(16)}: ${if (ok) "ok" else "FAILED"} — $detail")
        if (!ok) failures++
    }

    println("${"build".padEnd(16)}: ${AppVersion.VALUE} (${AppUpdates.REPOSITORY})")

    // The check the Settings screen runs, on both sides of its installPrerelease toggle.
    val stable = runCatching { AppUpdates.check(includePrerelease = false) }.getOrNull()
    val withPrerelease = runCatching { AppUpdates.check(includePrerelease = true) }.getOrNull()

    if (stable == null) {
        report("latest release", false, "no release returned for ${AppUpdates.REPOSITORY}")
        kotlin.system.exitProcess(1)
    }
    println("${"release".padEnd(16)}: v${stable.version} prerelease=${stable.prerelease}")
    println("${"url".padEnd(16)}: ${stable.url}")

    report(
        "matches build",
        stable.version == AppVersion.VALUE,
        "updater offers v${stable.version}, this build is ${AppVersion.VALUE}",
    )
    report(
        "no self-update",
        !stable.newer,
        "newer=${stable.newer} for ${AppVersion.VALUE}",
    )

    // The previous release, and the comparison an install still on it performs: the same
    // isNewer call, with that build's version as `current`. This is what makes the updater
    // offer v1.1.2 to someone on the last release.
    val previous = publishedTags().firstOrNull { it.removePrefix("v") != AppVersion.VALUE }
    if (previous == null) {
        report("update offered", false, "no published release other than ${AppVersion.VALUE}")
    } else {
        report(
            "update offered",
            AppUpdates.isNewer(AppVersion.VALUE, previous),
            "an install on $previous is offered ${AppVersion.VALUE}",
        )
    }

    report(
        "prerelease path",
        withPrerelease != null &&
            withPrerelease.newer == AppUpdates.isNewer(withPrerelease.version, AppVersion.VALUE),
        withPrerelease?.let { "v${it.version} prerelease=${it.prerelease}" }
            ?: "no release returned",
    )

    if (failures > 0) {
        println("$failures failure(s)")
        kotlin.system.exitProcess(1)
    }
    println("OK: the updater offers v${stable.version} and flags it newer than $previous")
}

private val releasesJson = Json { ignoreUnknownKeys = true }

/** Published release tags, newest first — drafts are invisible without a token. */
private suspend fun publishedTags(): List<String> = releasesJson
    .parseToJsonElement(
        Http.get("https://api.github.com/repos/${AppUpdates.REPOSITORY}/releases?per_page=10"),
    )
    .jsonArray
    .map { it.jsonObject }
    .filter { release -> release["prerelease"]?.jsonPrimitive?.booleanOrNull != true }
    .mapNotNull { release -> release["tag_name"]?.jsonPrimitive?.content }