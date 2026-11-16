package android.util

/**
 * Android's `Log`, routed to stdout so an extension's diagnostics show up in the app log
 * exactly like they do in logcat on Android.
 */
@Suppress("unused")
object Log {
    @JvmStatic
    fun v(tag: String?, msg: String?): Int = print("V", tag, msg)

    @JvmStatic
    fun v(tag: String?, msg: String?, tr: Throwable?): Int = print("V", tag, msg, tr)

    @JvmStatic
    fun d(tag: String?, msg: String?): Int = print("D", tag, msg)

    @JvmStatic
    fun d(tag: String?, msg: String?, tr: Throwable?): Int = print("D", tag, msg, tr)

    @JvmStatic
    fun i(tag: String?, msg: String?): Int = print("I", tag, msg)

    @JvmStatic
    fun i(tag: String?, msg: String?, tr: Throwable?): Int = print("I", tag, msg, tr)

    @JvmStatic
    fun w(tag: String?, msg: String?): Int = print("W", tag, msg)

    @JvmStatic
    fun w(tag: String?, msg: String?, tr: Throwable?): Int = print("W", tag, msg, tr)

    @JvmStatic
    fun e(tag: String?, msg: String?): Int = print("E", tag, msg)

    @JvmStatic
    fun e(tag: String?, msg: String?, tr: Throwable?): Int = print("E", tag, msg, tr)

    @JvmStatic
    fun wtf(tag: String?, msg: String?): Int = print("E", tag, msg)

    @JvmStatic
    fun wtf(tag: String?, tr: Throwable?): Int = print("E", tag, tr?.message, tr)

    @JvmStatic
    fun wtf(tag: String?, msg: String?, tr: Throwable?): Int = print("E", tag, msg, tr)

    @JvmStatic
    fun isLoggable(tag: String?, level: Int): Boolean = true

    @JvmStatic
    fun getStackTraceString(tr: Throwable?): String =
        tr?.stackTraceToString() ?: ""

    @JvmStatic
    fun println(priority: Int, tag: String?, msg: String?): Int = print("I", tag, msg)

    private fun print(level: String, tag: String?, msg: String?, tr: Throwable? = null): Int {
        System.out.println("$level/${tag ?: "CloudStream"}: ${msg ?: ""}")
        tr?.printStackTrace(System.out)
        return 0
    }
}
