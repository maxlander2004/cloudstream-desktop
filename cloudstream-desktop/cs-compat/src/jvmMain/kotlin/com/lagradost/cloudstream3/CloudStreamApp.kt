package com.lagradost.cloudstream3

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * Stand-in for the Android application class extensions are compiled against.
 *
 * Extensions reach the running app through `CloudStreamApp.context` (preferences, resources)
 * and the companion helpers below. On Android this class *is* the `Application`; here it is a
 * plain object that owns a stand-in [Context], because the desktop UI is Compose and has no
 * application/activity of its own.
 *
 * Without it, an extension whose code touches `CloudStreamApp` fails to load with
 * `NoClassDefFoundError: com/lagradost/cloudstream3/CloudStreamApp` even though the missing
 * member is only a preference read.
 */
class CloudStreamApp {
    companion object {
        /** The desktop stand-in for `applicationContext`. */
        private val desktopContext: Context = object : Context() {}

        var context: Context? = desktopContext
            private set

        @Volatile
        var exceptionHandler: Thread.UncaughtExceptionHandler? = null

        /**
         * Keys an extension saves with `setKey` / `getKey`.
         *
         * Upstream keeps these in Android DataStore; the desktop keeps them in memory, which
         * matches how the rest of the desktop app treats per-session provider settings.
         */
        private val keys = ConcurrentHashMap<String, Any>()

        private fun path(folder: String?, path: String): String =
            if (folder.isNullOrBlank()) path else "$folder/$path"

        @Suppress("UNCHECKED_CAST")
        fun <T : Any> getKeyClass(path: String, valueType: Class<T>): T? =
            keys[path]?.takeIf { valueType.isInstance(it) } as? T

        fun <T : Any> setKeyClass(path: String, value: T) {
            keys[path] = value
        }

        fun removeKeys(folder: String): Int? {
            val prefix = "$folder/"
            val removed = keys.keys.count { it == folder || it.startsWith(prefix) }
            keys.keys.removeIf { it == folder || it.startsWith(prefix) }
            return removed
        }

        fun <T> setKey(path: String, value: T) {
            if (value == null) keys.remove(path) else keys[path] = value
        }

        fun <T> setKey(folder: String, path: String, value: T) {
            setKey(path(folder, path), value)
        }

        @Suppress("UNCHECKED_CAST")
        fun <T : Any> getKey(path: String, defVal: T?): T? = (keys[path] as? T) ?: defVal

        @Suppress("UNCHECKED_CAST")
        fun <T : Any> getKey(path: String): T? = keys[path] as? T

        @Suppress("UNCHECKED_CAST")
        fun <T : Any> getKey(folder: String, path: String): T? = getKey<T>(path(folder, path))

        @Suppress("UNCHECKED_CAST")
        fun <T : Any> getKey(folder: String, path: String, defVal: T?): T? =
            (keys[path(folder, path)] as? T) ?: defVal

        fun getKeys(folder: String): List<String>? {
            val prefix = "$folder/"
            return keys.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }
        }

        fun removeKey(folder: String, path: String) {
            keys.remove(path(folder, path))
        }

        fun removeKey(path: String) {
            keys.remove(path)
        }

        /** Extensions open web pages through the system browser; the desktop app owns that. */
        fun openBrowser(url: String, fallbackWebView: Boolean = false, fragment: Any? = null) {
            openBrowser(url, null)
        }

        fun openBrowser(url: String, activity: android.app.Activity?) {
            runCatching {
                val desktop = Class.forName("java.awt.Desktop").getMethod("getDesktop")
                val instance = desktop.invoke(null)
                instance.javaClass
                    .getMethod("browse", java.net.URI::class.java)
                    .invoke(instance, java.net.URI(url))
            }
        }
    }
}
