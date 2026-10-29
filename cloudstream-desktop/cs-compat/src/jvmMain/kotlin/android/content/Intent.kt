package android.content

/**
 * Android's `Intent`, reduced to the calls extensions make when they hand a URL to the system.
 * Nothing is launched on the desktop; the app opens browsers through its own UI.
 */
open class Intent {
    var action: String? = null
    var data: android.net.Uri? = null
    var flags: Int = 0

    private val extras = HashMap<String, Any?>()

    constructor()

    constructor(action: String) {
        this.action = action
    }

    constructor(context: Context, cls: Class<*>) {
        this.componentClass = cls
    }

    /** Not an Android type; kept so the context constructor above stores something. */
    var componentClass: Class<*>? = null
        private set

    fun setAction(action: String): Intent = apply { this.action = action }

    fun setData(uri: android.net.Uri?): Intent = apply { data = uri }

    fun setFlags(flags: Int): Intent = apply { this.flags = flags }

    fun addFlags(flags: Int): Intent = apply { this.flags = this.flags or flags }

    fun putExtra(key: String, value: String?): Intent = apply { extras[key] = value }

    fun putExtra(key: String, value: Int): Intent = apply { extras[key] = value }

    fun putExtra(key: String, value: Long): Intent = apply { extras[key] = value }

    fun putExtra(key: String, value: Boolean): Intent = apply { extras[key] = value }

    fun getStringExtra(key: String): String? = extras[key] as? String

    fun getIntExtra(key: String, defValue: Int): Int = extras[key] as? Int ?: defValue

    fun getBooleanExtra(key: String, defValue: Boolean): Boolean =
        extras[key] as? Boolean ?: defValue
}
