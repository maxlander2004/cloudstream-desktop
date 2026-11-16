package android.os

/** Android's `PersistableBundle`, as far as extension bytecode links against it. */
class PersistableBundle(persistableBundle: PersistableBundle? = null) : java.io.Serializable {
    open fun putBoolean(key: String, value: Boolean) = Unit
    open fun getBoolean(key: String, defValue: Boolean): Boolean = defValue
    open fun putInt(key: String, value: Int) = Unit
    open fun getInt(key: String, defValue: Int): Int = defValue
    open fun putLong(key: String, value: Long) = Unit
    open fun getLong(key: String, defValue: Long): Long = defValue
    open fun putString(key: String, value: String?) = Unit
    open fun getString(key: String): String? = null
    open fun putAll(from: PersistableBundle?) = Unit
    open fun getPersistableBundle(key: String): PersistableBundle? = null
    open fun hasKey(key: String): Boolean = false
    open fun remove(key: String) = Unit
    open fun clear() = Unit
    open fun isEmpty(): Boolean = true
    companion object {
        const val CONTENT_SECURITY_POLICY = "android:content-security-policy"
    }
}
