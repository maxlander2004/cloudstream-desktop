package com.lagradost.cloudstream3.ui.player

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Upstream's `SubtitleOrigin`: where a subtitle track came from. */
enum class SubtitleOrigin {
    EXTERNAL,
    EMBEDDED_IN_VIDEO,
}

/**
 * Upstream's `SubtitleData`: one subtitle track a provider returned.
 *
 * Copied from the Android app module, where `LinkLoadingResult` and the player keep it. The
 * language helpers that upstream derives from its locale tables are left out — an extension
 * only builds these and hands them back to the host.
 */
@Serializable
data class SubtitleData(
    @SerialName("originalName") val originalName: String,
    @SerialName("nameSuffix") val nameSuffix: String,
    @SerialName("url") val url: String,
    @SerialName("origin") val origin: SubtitleOrigin,
    @SerialName("mimeType") val mimeType: String,
    @SerialName("headers") val headers: Map<String, String>,
    @SerialName("languageCode") val languageCode: String?,
) {
    /** Internal id for the player, unique per track. */
    fun getId(): String =
        if (origin == SubtitleOrigin.EMBEDDED_IN_VIDEO) url else "$url|$name"

    /** Upstream's fixed-up url: some extensions omit the scheme. */
    fun getFixedUrl(): String = if (url.startsWith("//")) "https:$url" else url

    @SerialName("name")
    val name: String = "$originalName $nameSuffix"
}
