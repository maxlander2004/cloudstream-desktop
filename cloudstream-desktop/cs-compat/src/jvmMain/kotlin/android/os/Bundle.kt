package android.os

/**
 * Android's `Bundle`: a string-keyed map. Extensions use it for intent extras, fragment
 * arguments and saved state, none of which have an Android container here.
 */
open class Bundle {
    private val values = LinkedHashMap<String?, Any?>()

    constructor()

    constructor(other: Bundle?) {
        if (other != null) values.putAll(other.values)
    }

    open fun putString(key: String?, value: String?) = apply { values[key] = value }

    open fun putStringArrayList(key: String?, value: ArrayList<String>?) = apply { values[key] = value }

    open fun putCharSequence(key: String?, value: CharSequence?) = apply { values[key] = value }

    open fun putInt(key: String?, value: Int) = apply { values[key] = value }

    open fun putLong(key: String?, value: Long) = apply { values[key] = value }

    open fun putFloat(key: String?, value: Float) = apply { values[key] = value }

    open fun putBoolean(key: String?, value: Boolean) = apply { values[key] = value }

    open fun putDouble(key: String?, value: Double) = apply { values[key] = value }

    open fun putByteArray(key: String?, value: ByteArray?) = apply { values[key] = value }

    open fun putIntArray(key: String?, value: IntArray?) = apply { values[key] = value }

    open fun putSerializable(key: String?, value: java.io.Serializable?) = apply { values[key] = value }

    open fun putParcelable(key: String?, value: Any?) = apply { values[key] = value }

    open fun putParcelableArrayList(key: String?, value: ArrayList<*>?) = apply { values[key] = value }

    open fun putBundle(key: String?, value: Bundle?) = apply { values[key] = value }

    open fun putAll(bundle: Bundle?) {
        if (bundle != null) values.putAll(bundle.values)
    }

    open fun getString(key: String?): String? = values[key] as? String

    open fun getString(key: String?, defaultValue: String?): String? =
        values[key] as? String ?: defaultValue

    open fun getCharSequence(key: String?): CharSequence? = values[key] as? CharSequence

    open fun getInt(key: String?): Int = values[key] as? Int ?: 0

    open fun getInt(key: String?, defaultValue: Int): Int = values[key] as? Int ?: defaultValue

    open fun getLong(key: String?): Long = values[key] as? Long ?: 0L

    open fun getLong(key: String?, defaultValue: Long): Long =
        values[key] as? Long ?: defaultValue

    open fun getFloat(key: String?): Float = values[key] as? Float ?: 0f

    open fun getBoolean(key: String?): Boolean = values[key] as? Boolean ?: false

    open fun getBoolean(key: String?, defaultValue: Boolean): Boolean =
        values[key] as? Boolean ?: defaultValue

    open fun getDouble(key: String?): Double = values[key] as? Double ?: 0.0

    open fun getByteArray(key: String?): ByteArray? = values[key] as? ByteArray

    open fun getIntArray(key: String?): IntArray? = values[key] as? IntArray

    open fun getStringArrayList(key: String?): ArrayList<String>? =
        values[key] as? ArrayList<String>

    @Suppress("UNCHECKED_CAST")
    open fun <T : Any> getSerializable(key: String?): T? = values[key] as? T

    open fun <T : Any> getParcelable(key: String?): T? = null

    open fun <T : Any> getParcelableArrayList(key: String?): ArrayList<T>? = null

    open fun getBundle(key: String?): Bundle? = values[key] as? Bundle

    open fun containsKey(key: String?): Boolean = values.containsKey(key)

    open fun isEmpty(): Boolean = values.isEmpty()

    open fun size(): Int = values.size

    open fun keySet(): Set<String> = values.keys.filterNotNull().toSet()

    open fun remove(key: String?) {
        values.remove(key)
    }

    open fun clear() = values.clear()

    open fun get(key: String?): Any? = values[key]

    override fun toString(): String = values.toString()
}
