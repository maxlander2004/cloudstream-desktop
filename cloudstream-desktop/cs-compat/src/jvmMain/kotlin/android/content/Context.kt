package android.content

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties

/**
 * The smallest Android `Context` an extension can be linked against.
 *
 * Original CloudStream extensions are compiled against the Android app, so their bytecode
 * names `android.content.Context` even when the class only ever reads a preference. The JVM
 * has no such class, so without this the extension fails with
 * `NoClassDefFoundError: com/lagradost/cloudstream3/CloudStreamApp` the first time a provider
 * touches `CloudStreamApp.context` (the donation helpers bundled into several repositories do
 * exactly that when a provider's home page loads).
 *
 * Only what extension code actually calls is implemented. UI entry points are accepted and
 * ignored: the desktop UI is Compose, not an Android activity, so there is nothing to start.
 */
open class Context {
    private val preferences = HashMap<String, SharedPreferences>()

    open fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        synchronized(preferences) {
            preferences.getOrPut(name) { PersistentPreferences(name) }
        }

    open fun getResources(): android.content.res.Resources = android.content.res.Resources()

    open fun getApplicationContext(): Context = this

    /** No activity exists to start; a plugin that opens a browser does so through the app. */
    open fun startActivity(intent: Intent) = Unit

    open fun getPackageName(): String = "app.cloudstream.desktop"

    open fun getFilesDir(): java.io.File = dataDir().toFile()

    open fun getCacheDir(): java.io.File = dataDir().resolve("cache").toFile()

    internal companion object {
        fun dataDir(): Path = Paths.get(System.getProperty("user.home"), ".cloudstream-desktop")
    }
}

/**
 * A preference file on disk, so an extension's saved settings survive a restart the way they
 * do on Android. Values are kept per file and written on every commit.
 */
private class PersistentPreferences(private val fileName: String) : SharedPreferences {
    private val file: Path = Context.dataDir()
        .resolve("legacy-prefs")
        .resolve(fileName.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".properties")

    private val props = Properties().apply {
        runCatching { Files.newInputStream(file).use { load(it) } }
    }

    private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> {
        val copy = HashMap<String, Any>(props.size)
        for (key in props.stringPropertyNames()) copy[key] = props.getProperty(key)
        return copy
    }

    override fun getString(key: String, defValue: String?): String? =
        props.getProperty(key) ?: defValue

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        props.getProperty(key)?.split('\n')?.toMutableSet() ?: defValues

    override fun getInt(key: String, defValue: Int): Int =
        props.getProperty(key)?.toIntOrNull() ?: defValue

    override fun getLong(key: String, defValue: Long): Long =
        props.getProperty(key)?.toLongOrNull() ?: defValue

    override fun getFloat(key: String, defValue: Float): Float =
        props.getProperty(key)?.toFloatOrNull() ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        props.getProperty(key)?.toBooleanStrictOrNull() ?: defValue

    override fun contains(key: String): Boolean = props.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        if (listener != null) synchronized(listeners) { listeners.add(listener) }
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        if (listener != null) synchronized(listeners) { listeners.remove(listener) }
    }

    private fun store(key: String, value: String?) {
        if (value == null) props.remove(key) else props.setProperty(key, value)
    }

    private fun save() {
        runCatching {
            Files.createDirectories(file.parent)
            Files.newOutputStream(file).use { props.store(it, "CloudStream Desktop extension preferences") }
        }
        val changed = synchronized(listeners) { listeners.toList() }
        changed.forEach { listener -> runCatching { listener.onSharedPreferenceChanged(this, null) } }
    }

    private inner class Editor : SharedPreferences.Editor {
        private val pending = LinkedHashMap<String, String?>()

        override fun putString(key: String, value: String?): SharedPreferences.Editor =
            apply { pending[key] = value }

        override fun putStringSet(
            key: String,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor = apply { pending[key] = values?.joinToString("\n") }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor =
            apply { pending[key] = value.toString() }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor =
            apply { pending[key] = value.toString() }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor =
            apply { pending[key] = value.toString() }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor =
            apply { pending[key] = value.toString() }

        override fun remove(key: String): SharedPreferences.Editor =
            apply { pending[key] = null }

        override fun clear(): SharedPreferences.Editor = apply {
            props.stringPropertyNames().forEach { pending[it] = null }
        }

        override fun commit(): Boolean {
            pending.forEach { (key, value) -> store(key, value) }
            pending.clear()
            save()
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
