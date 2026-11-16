package com.lagradost.cloudstream3.ui

/**
 * Upstream's `SyncWatchType`, used by extensions that talk to a sync service.
 *
 * The constants and their ids match the Android enum, so an extension that compares or
 * serialises them sees the same values. The resource ids are zero: the desktop UI is Compose
 * and never draws the enum's icon or label.
 */
@Suppress("unused")
enum class SyncWatchType(val internalId: Int) {
    NONE(-1),
    WATCHING(0),
    COMPLETED(1),
    ONHOLD(2),
    DROPPED(3),
    PLANTOWATCH(4),
    REWATCHING(5);

    val stringRes: Int get() = 0
    val iconRes: Int get() = 0

    companion object {
        fun fromInternalId(id: Int?): SyncWatchType =
            entries.find { value -> value.internalId == id } ?: NONE
    }
}
