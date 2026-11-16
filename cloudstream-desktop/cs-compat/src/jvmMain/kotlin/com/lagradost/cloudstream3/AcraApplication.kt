package com.lagradost.cloudstream3

/**
 * Upstream's deprecated `AcraApplication` alias, kept for the same reason upstream keeps it:
 * older extensions still call it.
 *
 * Every member forwards to [CloudStreamApp], exactly as upstream's shim does, so an extension
 * that saves a setting through `AcraApplication.setKey` reads it back through
 * `CloudStreamApp.getKey`.
 */
@Suppress("DEPRECATION")
class AcraApplication {
    companion object {
        val context get() = CloudStreamApp.context

        fun removeKeys(folder: String): Int? = CloudStreamApp.removeKeys(folder)

        fun <T> setKey(path: String, value: T) = CloudStreamApp.setKey(path, value)

        fun <T> setKey(folder: String, path: String, value: T) =
            CloudStreamApp.setKey(folder, path, value)

        fun <T : Any> getKey(path: String, defVal: T?): T? = CloudStreamApp.getKey(path, defVal)

        fun <T : Any> getKey(path: String): T? = CloudStreamApp.getKey(path)

        fun <T : Any> getKey(folder: String, path: String): T? =
            CloudStreamApp.getKey(folder, path)

        fun <T : Any> getKey(folder: String, path: String, defVal: T?): T? =
            CloudStreamApp.getKey(folder, path, defVal)

        fun removeKey(folder: String, path: String) = CloudStreamApp.removeKey(folder, path)

        fun removeKey(path: String) = CloudStreamApp.removeKey(path)

        fun getKeys(folder: String): List<String>? = CloudStreamApp.getKeys(folder)
    }
}
