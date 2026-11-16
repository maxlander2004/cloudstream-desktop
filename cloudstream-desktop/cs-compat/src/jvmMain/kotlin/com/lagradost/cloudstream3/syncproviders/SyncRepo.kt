package com.lagradost.cloudstream3.syncproviders

/**
 * Upstream's `AuthUser`: the signed-in account a sync service reports.
 *
 * Only the null-ness of it matters to extensions at load time — a provider checks
 * `repo.authUser() != null` before offering personal lists.
 */
data class AuthUser(
    val id: Int,
    val name: String? = null,
)

/** Upstream's `AuthData`: a user plus the token used to talk to the service. */
data class AuthData(
    val user: AuthUser,
    val token: Any? = null,
)

/**
 * Upstream's `SyncRepo`: the safe wrapper extensions use instead of calling [SyncAPI] directly.
 *
 * Upstream it extends `AuthRepo`, which owns the account store in the Android app module. The
 * desktop has no account store, so [authUser] answers null and [library] asks the service
 * anonymously. That is what a desktop session without a signed-in account looks like: the two
 * extensions that need this class load and run their public pages, and their personal-list
 * pages report "login required" instead of crashing.
 */
class SyncRepo(val api: SyncAPI) {
    val syncIdName get() = api.syncIdName

    var requireLibraryRefresh: Boolean
        get() = api.requireLibraryRefresh
        set(value) {
            api.requireLibraryRefresh = value
        }

    fun authUser(): AuthUser? = null

    fun authToken(): Any? = null

    val isAvailable get() = true

    suspend fun updateStatus(id: String, newStatus: SyncAPI.AbstractSyncStatus): Result<Boolean> =
        runCatching { api.updateStatus(null, id, newStatus) }

    suspend fun status(id: String): Result<SyncAPI.AbstractSyncStatus?> = runCatching {
        api.status(null, id)
    }

    suspend fun load(id: String): Result<SyncAPI.SyncResult?> = runCatching { api.load(null, id) }

    suspend fun library(): Result<SyncAPI.LibraryMetadata?> = runCatching { api.library(null) }
}
