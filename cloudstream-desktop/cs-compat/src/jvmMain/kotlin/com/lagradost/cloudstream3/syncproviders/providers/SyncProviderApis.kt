package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.syncproviders.SyncIdName

/**
 * Upstream's `SimklApi`, reduced to the sync service's identity.
 *
 * Extensions reach the app's shared Simkl client through `AccountManager.simklApi` and wrap it
 * in a `SyncRepo` to read the user's lists. Upstream this class holds the whole Simkl protocol;
 * here it links and answers, and every call that needs an account or the network reports
 * `NotImplementedError`, which `SyncRepo` turns into a failed `Result` the extension already
 * handles.
 */
class SimklApi : SyncAPI() {
    override val mainUrl: String = "https://api.simkl.com"
    override val syncIdName: SyncIdName = SyncIdName.Simkl
}

/**
 * Upstream's `AniListApi`, reduced like [SimklApi].
 *
 * This is the client extensions get from `AccountManager.aniListApi`; AnoList's public
 * endpoints would work anonymously, but the desktop has no token store to sign in with, so the
 * calls stay unimplemented rather than silently unauthenticated.
 */
class AniListApi : SyncAPI() {
    override val mainUrl: String = "https://graphql.anilist.co"
    override val syncIdName: SyncIdName = SyncIdName.Anilist
}
