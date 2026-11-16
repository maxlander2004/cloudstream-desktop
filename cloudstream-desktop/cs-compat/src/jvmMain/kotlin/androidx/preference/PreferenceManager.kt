package androidx.preference

import android.content.Context
import android.content.SharedPreferences

/**
 * AndroidX's `PreferenceManager`.
 *
 * Upstream's DataStore keeps app-wide settings in the default preference file, so extensions
 * that call this directly read the same backend the desktop uses for its own preferences.
 */
object PreferenceManager {
    const val KEY_HAS_SET_DEFAULT_VALUES = "_has_set_default_values"

    @JvmStatic
    fun getDefaultSharedPreferences(context: Context): SharedPreferences =
        context.getSharedPreferences(DEFAULT_FILE_NAME, Context.MODE_PRIVATE)

    @JvmStatic
    fun getDefaultSharedPreferencesName(context: Context): String = DEFAULT_FILE_NAME

    private const val DEFAULT_FILE_NAME = "default_preferences"
}
