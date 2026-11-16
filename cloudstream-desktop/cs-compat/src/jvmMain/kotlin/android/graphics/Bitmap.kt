package android.graphics

/** Android's `Bitmap`, which extension callbacks receive from WebView/UI code. */
open class Bitmap {
    open fun getWidth(): Int = 0

    open fun getHeight(): Int = 0

    open fun recycle() = Unit

    open fun isRecycled(): Boolean = false
}
