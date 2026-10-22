package com.lagradost.api

/**
 * Stand-in for the class upstream generates with BuildKonfig from `local.properties`.
 *
 * Only the two metaprovider keys live here. They are the app author's own MyDramaList/Trakt
 * credentials in upstream builds; extensions do not read them, so they stay empty and the
 * corresponding metaproviders report themselves as unconfigured.
 */
object BuildConfig {
    const val MDL_API_KEY: String = ""
    const val TRAKT_CLIENT_ID: String = ""
}
