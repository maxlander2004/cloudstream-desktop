package androidx.core.content

import android.content.SharedPreferences

/**
 * AndroidX's `edit {}` helper, which extensions inline but upstream's DataStore calls
 * directly. Inlined by the Kotlin compiler here too, so it links the same way.
 */
inline fun SharedPreferences.edit(
    commit: Boolean = false,
    action: SharedPreferences.Editor.() -> Unit,
) {
    val editor = edit()
    editor.action()
    if (commit) editor.commit() else editor.apply()
}
