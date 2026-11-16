package android.app

import android.content.Context
import android.os.Bundle

/**
 * Android's `Application`.
 *
 * The megarepo helper that most community extensions are built on reaches the running process
 * with `context.applicationContext as Application` and then calls
 * `registerActivityLifecycleCallbacks` so it can notice the app leaving the foreground. Without
 * this class that cast failed during plugin load with
 * `ClassNotFoundException: android.app.Application`, which is why every extension using the
 * helper refused to load at all.
 *
 * The desktop app is Compose and has no activity to report, so the callbacks are accepted and
 * kept but never invoked. Nothing an extension does inside them is required for its providers
 * to register or play.
 */
open class Application : Context() {

    private val lifecycleCallbacks = mutableListOf<ActivityLifecycleCallbacks>()

    /** On Android an application is its own application context, and casts rely on it. */
    override fun getApplicationContext(): Context = this

    open fun onCreate() = Unit

    open fun onTerminate() = Unit

    open fun onLowMemory() = Unit

    open fun onTrimMemory(level: Int) = Unit

    open fun getProcessName(): String? = "app.cloudstream.desktop"

    open fun registerActivityLifecycleCallbacks(callbacks: ActivityLifecycleCallbacks?) {
        if (callbacks != null) synchronized(lifecycleCallbacks) { lifecycleCallbacks += callbacks }
    }

    open fun unregisterActivityLifecycleCallbacks(callbacks: ActivityLifecycleCallbacks?) {
        if (callbacks != null) synchronized(lifecycleCallbacks) { lifecycleCallbacks -= callbacks }
    }

    /**
     * Nested on purpose: on Android the binary name is
     * `android.app.Application$ActivityLifecycleCallbacks`, and that is the name extension
     * bytecode implements and passes to [registerActivityLifecycleCallbacks].
     *
     * Every method has a no-op body so an extension that only overrides the few callbacks it
     * cares about can still be instantiated here; Android's copy is abstract with the same
     * signatures, so implementing it is the same thing.
     */
    interface ActivityLifecycleCallbacks {
        fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {}
        fun onActivityStarted(activity: Activity?) {}
        fun onActivityResumed(activity: Activity?) {}
        fun onActivityPaused(activity: Activity?) {}
        fun onActivityStopped(activity: Activity?) {}
        fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) {}
        fun onActivityDestroyed(activity: Activity?) {}
    }

    companion object {
        /**
         * The single process-wide application object, matching Android's one `Application`.
         *
         * Contexts hand this out from `getApplicationContext()`: extension code casts the
         * result to [Application], so the object it gets back has to actually be one.
         */
        val instance: Application by lazy { Application() }
    }
}
