package com.lagradost.cloudstream3.plugins

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Upstream's `PluginData`: the record the app keeps for an installed extension.
 *
 * Extensions occasionally read it to find out their own file path or version. The desktop
 * host stores the same fields (`ExtensionHost` keeps its own copy), so the type only needs to
 * match upstream's shape for extension bytecode to link.
 */
@Serializable
data class PluginData(
    @SerialName("internalName") val internalName: String,
    @SerialName("url") val url: String?,
    @SerialName("isOnline") val isOnline: Boolean,
    @SerialName("filePath") val filePath: String,
    @SerialName("version") val version: Int,
)
