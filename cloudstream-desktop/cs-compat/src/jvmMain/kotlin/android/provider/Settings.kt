package android.provider

import android.content.ContentResolver

/**
 * Android's `Settings` provider.
 *
 * Extensions read `Settings.Secure.getString(resolver, Settings.Secure.ANDROID_ID)` to get a
 * stable per-install identifier — typically to key a device in a cloud-sync account. The
 * desktop host has no such setting, so [Secure] answers with a fixed, non-identifying value for
 * that one key and null for everything else; a provider that requires a real device id degrades
 * the same way it does on an emulator that reports none.
 */
class Settings {
    /** Android's `Settings.Secure`, holding the device-level settings extensions read. */
    class Secure {
        companion object {
            const val ANDROID_ID = "android_id"

            /** A stable value per install, generated once and kept in the desktop data dir. */
            @JvmStatic
            fun getString(resolver: ContentResolver?, name: String?): String? = when (name) {
                ANDROID_ID -> desktopAndroidId()
                else -> null
            }

            @JvmStatic
            fun getInt(resolver: ContentResolver?, name: String?, def: Int): Int = def

            @JvmStatic
            fun putString(resolver: ContentResolver?, name: String?, value: String?) = Unit
        }
    }

    /** Android's `Settings.Global`, which extensions only probe for a locale or a flag. */
    class Global {
        companion object {
            const val DEVICE_NAME = "device_name"
            const val ADB_ENABLED = "adb_enabled"

            @JvmStatic
            fun getString(resolver: ContentResolver?, name: String?): String? = null

            @JvmStatic
            fun getInt(resolver: ContentResolver?, name: String?, def: Int): Int = def
        }
    }
}

/**
 * A stable pseudo device id for this installation.
 *
 * Derived from the user name and the desktop data directory so it survives restarts, and
 * deliberately not derived from any hardware identifier.
 */
private fun desktopAndroidId(): String {
    val seed = "${System.getProperty("user.name")}@${System.getProperty("user.home")}"
    val digest = java.security.MessageDigest.getInstance("SHA-256")
        .digest(seed.toByteArray())
    return digest.take(8).joinToString("") { "%02x".format(it) }
}
