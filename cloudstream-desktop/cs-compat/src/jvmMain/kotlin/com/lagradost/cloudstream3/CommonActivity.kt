package com.lagradost.cloudstream3

import android.app.Activity

/**
 * Upstream's single activity, which extensions use to get a UI context
 * (`CommonActivity.INSTANCE.activity`).
 *
 * The desktop app has no activity: its window is a Compose window. `activity` therefore stays
 * null, which is exactly the case extensions already handle — prompts, donation dialogs and
 * share sheets are skipped when there is no activity to show them in.
 */
object CommonActivity {
    @Volatile
    var activity: Activity? = null

    /** Upstream exposes the current activity through this helper; always null here. */
    @JvmStatic
    fun getCurrentActivity(): Activity? = activity
}
