package com.lagradost.cloudstream3

import android.app.Activity
import androidx.appcompat.app.AppCompatActivity

/**
 * Upstream's single activity, which extensions use to get a UI context
 * (`CommonActivity.INSTANCE.activity`).
 *
 * The desktop app's window is a Compose window, so there is no Android activity to hand out.
 * What extensions get instead is [DesktopActivity]: an `AppCompatActivity` instance with no
 * window behind it. That matters because plugins are loaded with their *activity* on Android
 * (`PluginManager.loadPlugin(plugin, activity)`), and several of them cast that parameter
 * straight to `AppCompatActivity` to open their settings screen. Passing the application
 * instead threw `ClassCastException` during load, before the plugin registered anything.
 *
 * Anything that actually draws (dialogs, fragment transactions, `findViewById`) finds the
 * stubs' no-ops, which is the same thing extensions already handle on a device with no
 * activity available.
 */
object CommonActivity {
    @Volatile
    var activity: Activity? = DesktopActivity

    /** Upstream exposes the current activity through this helper. */
    @JvmStatic
    fun getCurrentActivity(): Activity? = activity
}

/**
 * The one desktop "activity", shared like Android's single resumed activity.
 *
 * A plain `object` rather than a subclass per caller: extensions compare the activity they
 * were given against `CommonActivity.activity` and expect one instance.
 */
object DesktopActivity : AppCompatActivity()
