package android.content

/**
 * Android's `ContextWrapper`.
 *
 * Extensions unwrap a context to reach the activity or application behind it
 * (`context as? ContextWrapper)?.baseContext`). The desktop host's contexts have no wrapper
 * around them, so [getBaseContext] answers with the context itself — the same thing Android
 * does for a context that was never wrapped.
 */
open class ContextWrapper(private val base: Context? = null) : Context() {
    open fun getBaseContext(): Context = base ?: this
}
