package android.net

/** Android's `Uri`; only the string form extensions care about. */
open class Uri private constructor(val value: String) {
    override fun toString(): String = value

    companion object {
        @JvmStatic
        fun parse(uriString: String): Uri = Uri(uriString)
    }
}
