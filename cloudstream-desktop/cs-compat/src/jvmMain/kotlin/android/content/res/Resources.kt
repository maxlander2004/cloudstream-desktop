package android.content.res

import android.util.DisplayMetrics

/** Android's `Resources`; extensions only read display metrics from it. */
open class Resources {
    open fun getDisplayMetrics(): DisplayMetrics = DisplayMetrics()
}
