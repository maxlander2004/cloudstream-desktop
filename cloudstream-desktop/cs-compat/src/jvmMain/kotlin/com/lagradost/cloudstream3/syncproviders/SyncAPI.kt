package com.lagradost.cloudstream3.syncproviders

/**
 * Upstream's `SyncAPI`, reduced to what extensions link against.
 *
 * A sync service (Simkl, AniList, Trakt…) is an implementation of this class; extensions reach
 * one through [AccountManager] and wrap it in a [SyncRepo]. Upstream this class extends
 * `AuthAPI` and carries the whole login/token machinery, which lives in the Android app module
 * and depends on its account store — so the desktop keeps the shape and the sync calls
 * themselves, and leaves authentication to [AccountManager].
 *
 * Every method has a default, so an extension that was compiled against the full upstream class
 * still loads: the members it overrides become plain additions, and the members it calls exist.
 */
abstract class SyncAPI {
    /** Set to true when the user changes something on the list, so the UI reloads it. */
    open var requireLibraryRefresh: Boolean = true

    open val mainUrl: String = "NONE"

    /** Which id space this service maps to, used to link library entries to providers. */
    open val syncIdName: SyncIdName? = null

    /** Modify the current status of an item. */
    open suspend fun updateStatus(
        auth: AuthData?,
        id: String,
        newStatus: AbstractSyncStatus,
    ): Boolean = throw NotImplementedError()

    /** Get the current status of an item. */
    open suspend fun status(auth: AuthData?, id: String): AbstractSyncStatus? =
        throw NotImplementedError()

    /** Get metadata about an item. */
    open suspend fun load(auth: AuthData?, id: String): SyncResult? = throw NotImplementedError()

    /** Get the current library/bookmarks of this service. */
    open suspend fun library(auth: AuthData?): LibraryMetadata? = throw NotImplementedError()

    /** Turns a provider page url into an id this service understands. */
    open fun urlToId(url: String): String? = null

    /** Upstream's abstract watch status an item can be in. */
    open class AbstractSyncStatus

    /** Upstream's per-item sync payload. */
    open class SyncResult

    /** One named list in a service's library. */
    open class LibraryList {
        open val name: String = ""

        open val items: List<Any?> = emptyList()
    }

    /** A service's whole library, grouped into lists. */
    open class LibraryMetadata {
        open val allLibraryLists: List<LibraryList> = emptyList()
    }

    /** Upstream's search hit used to match a provider item to a sync entry. */
    data class SyncSearchResult(
        val name: String,
        val apiName: String,
        val syncId: String,
        val url: String,
    )
}
