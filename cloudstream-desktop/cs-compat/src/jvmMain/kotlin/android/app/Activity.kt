package android.app

import android.content.Context
import android.content.Intent

/**
 * Android's `Activity`. The desktop app has no activity, so extensions that require one
 * (donation prompts, share sheets, dialogs) simply find none and return.
 */
open class Activity : Context() {
    var intent: Intent? = null

    override fun getApplicationContext(): Context = this

    open fun isFinishing(): Boolean = false

    open fun isDestroyed(): Boolean = false

    open fun runOnUiThread(action: Runnable) {
        action.run()
    }

    open fun finish() = Unit
}
