package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKeyClass
import com.lagradost.cloudstream3.CloudStreamApp.Companion.removeKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKeyClass
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJsonLiteral
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * Upstream's `DataStore`, ported to the desktop.
 *
 * On Android this lives in the app module, so it was not part of the `library` sources the
 * compatibility layer compiles — extensions that save a setting through it failed to link
 * with `ClassNotFoundException: com.lagradost.cloudstream3.utils.DataStore`. The port is
 * upstream's implementation: per-provider values live in the `rebuild_preference` file through
 * the stand-in `Context`, so they persist across restarts exactly like on a device.
 */
const val DOWNLOAD_HEADER_CACHE = "download_header_cache"
const val DOWNLOAD_HEADER_CACHE_BACKUP = "BACKUP_download_header_cache"
const val DOWNLOAD_EPISODE_CACHE = "download_episode_cache"
const val DOWNLOAD_EPISODE_CACHE_BACKUP = "BACKUP_download_episode_cache"
const val VIDEO_PLAYER_BRIGHTNESS = "video_player_alpha_key"
const val USER_SELECTED_HOMEPAGE_API = "home_api_used"
const val USER_PROVIDER_API = "user_custom_sites"
const val PREFERENCES_NAME = "rebuild_preference"

class PreferenceDelegate<T : Any>(
    val key: String,
    val default: T,
) {
    private val klass: KClass<out T> = default::class

    private var cache: T? = null

    operator fun getValue(self: Any?, property: KProperty<*>): T =
        cache ?: getKeyClass(key, klass.java).also { newCache -> cache = newCache } ?: default

    operator fun setValue(self: Any?, property: KProperty<*>, t: T?) {
        cache = t
        if (t == null) removeKey(key) else setKeyClass(key, t)
    }
}

/** Upstream's batching editor, for writes that touch many keys at once. */
data class Editor(
    val editor: SharedPreferences.Editor,
) {
    fun <T> setKeyRaw(path: String, value: T) {
        if (isStringSet(value)) {
            @Suppress("UNCHECKED_CAST")
            editor.putStringSet(path, (value as Set<String>).toMutableSet())
        } else {
            when (value) {
                is Boolean -> editor.putBoolean(path, value)
                is Int -> editor.putInt(path, value)
                is String -> editor.putString(path, value)
                is Float -> editor.putFloat(path, value)
                is Long -> editor.putLong(path, value)
            }
        }
    }

    private fun isStringSet(value: Any?): Boolean =
        value is Set<*> && value.filterIsInstance<String>().size == value.size

    fun apply() {
        editor.apply()
        System.gc()
    }
}

object DataStore {
    @Deprecated(
        "Please use methods from AppUtils to parse JSON.",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith("com.lagradost.cloudstream3.mapper"),
    )
    val mapper = com.lagradost.cloudstream3.mapper

    private fun getPreferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun Context.getSharedPrefs(): SharedPreferences = getPreferences(this)

    fun getFolderName(folder: String, path: String): String = "$folder/$path"

    fun editor(context: Context, isEditingAppSettings: Boolean = false): Editor {
        val editor: SharedPreferences.Editor =
            if (isEditingAppSettings) context.getDefaultSharedPrefs().edit()
            else context.getSharedPrefs().edit()
        return Editor(editor)
    }

    fun Context.getDefaultSharedPrefs(): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(this)

    fun Context.getKeys(folder: String): List<String> {
        val fixedFolder = folder.trimEnd('/') + "/"
        return this.getSharedPrefs().getAll().keys.filter { it.startsWith(fixedFolder) }
    }

    fun Context.removeKey(folder: String, path: String) {
        removeKey(getFolderName(folder, path))
    }

    fun Context.containsKey(folder: String, path: String): Boolean =
        containsKey(getFolderName(folder, path))

    fun Context.containsKey(path: String): Boolean = getSharedPrefs().contains(path)

    fun Context.removeKey(path: String) {
        try {
            val prefs = getSharedPrefs()
            if (prefs.contains(path)) prefs.edit { remove(path) }
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun Context.removeKeys(folder: String): Int {
        val keys = getKeys("$folder/")
        return try {
            getSharedPrefs().edit { keys.forEach { value -> remove(value) } }
            keys.size
        } catch (e: Exception) {
            logError(e)
            0
        }
    }

    fun <T> Context.setKey(path: String, value: T) {
        try {
            getSharedPrefs().edit { putString(path, value?.toJsonLiteral()) }
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun <T : Any> Context.getKey(path: String, valueType: Class<T>): T? = try {
        val value: String = getSharedPrefs().getString(path, null) ?: return null
        parseJson(value, valueType.kotlin)
    } catch (_: Exception) {
        null
    }

    fun <T> Context.setKey(folder: String, path: String, value: T) {
        setKey(getFolderName(folder, path), value)
    }

    inline fun <reified T : Any> Context.getKey(path: String, defVal: T?): T? = try {
        val value: String = getSharedPrefs().getString(path, null) ?: return defVal
        parseJson<T>(value)
    } catch (_: Exception) {
        null
    }

    inline fun <reified T : Any> Context.getKey(path: String): T? = getKey(path, null)

    inline fun <reified T : Any> Context.getKey(folder: String, path: String): T? =
        getKey(getFolderName(folder, path), null)

    inline fun <reified T : Any> Context.getKey(folder: String, path: String, defVal: T?): T? =
        getKey(getFolderName(folder, path), defVal) ?: defVal
}
