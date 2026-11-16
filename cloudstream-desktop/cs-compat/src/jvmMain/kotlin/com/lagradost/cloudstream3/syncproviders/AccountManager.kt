package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.syncproviders.providers.AniListApi
import com.lagradost.cloudstream3.syncproviders.providers.SimklApi

/**
 * Upstream's `AccountManager`: the app's store of signed-in accounts and shared sync clients.
 *
 * Extensions only ever ask it for a client — `AccountManager.simklApi`, `AccountManager.aniListApi`
 * — and wrap that in a `SyncRepo`. The desktop host has no account store (there is no OAuth
 * redirect to come back to without a browser session), so the clients exist, are shared, and
 * report no signed-in user. Everything an extension does with a logged-out session behaves the
 * way it does on a phone that never signed in.
 */
class AccountManager {
    companion object {
        /** The same instance every call, like upstream's lazily created singleton. */
        val simklApi: SimklApi by lazy { SimklApi() }

        val aniListApi: AniListApi by lazy { AniListApi() }

        /** No account is ever signed in. */
        const val NONE_ID = 0
    }
}
