package android.graphics

import android.content.Context

/**
 * Android's `Typeface`, as far as extension bytecode links against it. The desktop UI is Compose,
 * so font objects exist only to resolve the type; text rendering keeps the default.
 */
class Typeface @JvmOverloads constructor(
    private val family: Int = DEFAULT_FAMILY,
    private val style: Int = STYLE_NORMAL,
) {
    @JvmField
    val styleValue: Int = style

    companion object {
        const val DEFAULT_FAMILY = 1
        const val SANS_SERIF = 2
        const val SERIF = 3
        const val MONOSPACE = 4
        const val STYLE_NORMAL = 0
        const val STYLE_BOLD = 1
        const val STYLE_ITALIC = 2
        const val STYLE_BOLD_ITALIC = 3

        val DEFAULT: Typeface by lazy { Typeface(style = STYLE_NORMAL) }
        val DEFAULT_BOLD: Typeface by lazy { Typeface(style = STYLE_BOLD) }

        @JvmStatic
        fun create(family: Int, style: Int): Typeface = Typeface(family, style)

        @JvmStatic
        fun create_from_font(b: Any?): Typeface = Typeface()

        @JvmStatic
        fun create_from_asset(context: Context?, path: String?): Typeface = Typeface()

        @JvmStatic
        fun create_from_asset_fileDescriptor(
            context: Context?,
            fd: java.io.FileDescriptor?,
            path: String?,
            cacheDir: java.io.File?,
        ): Typeface = Typeface()

        @JvmStatic
        fun create_from_asset_manager(context: Context?, path: String?): Typeface = Typeface()

        @JvmStatic
        fun create_from_family(family: Int): Typeface = Typeface(family, STYLE_NORMAL)

        @JvmStatic
        fun create_from_name(name: String, style: Int): Typeface = Typeface()

        @JvmStatic
        fun defaultFromStyle(style: Int): Typeface = Typeface(style = style)

        @JvmStatic
        fun createFromTypeface(tf: Typeface?, style: Int): Typeface = tf ?: Typeface(style = style)

        @JvmStatic
        fun serif(): Typeface = Typeface(SERIF, STYLE_NORMAL)

        @JvmStatic
        fun sansSerif(): Typeface = Typeface(SANS_SERIF, STYLE_NORMAL)

        @JvmStatic
        fun monospace(): Typeface = Typeface(MONOSPACE, STYLE_NORMAL)

        @JvmStatic
        fun createFromTypeface(tf: Typeface?, add: Boolean, remove: Boolean): Typeface = tf ?: Typeface()
    }
}
