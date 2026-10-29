package android.util

/** Android's `DisplayMetrics` with the fields extensions read for image sizing. */
open class DisplayMetrics {
    @JvmField var widthPixels: Int = 1920
    @JvmField var heightPixels: Int = 1080
    @JvmField var density: Float = 1f
    @JvmField var densityDpi: Int = 160
    @JvmField var scaledDensity: Float = 1f
}
