package android.app

/**
 * Android's `ActivityManager`, reduced to the memory query extensions use to size their caches.
 *
 * Extensions that stream video ask the system how much RAM is left and lower their buffer or
 * thread count accordingly. There is no system service to ask on the desktop, so the numbers
 * come from the JVM's own heap.
 */
open class ActivityManager {
    /** Fills [outInfo] the way `ActivityManager.getMemoryInfo` does on a real device. */
    open fun getMemoryInfo(outInfo: MemoryInfo) {
        val runtime = Runtime.getRuntime()
        outInfo.totalMem = runtime.maxMemory()
        outInfo.availMem = runtime.freeMemory()
        outInfo.threshold = runtime.maxMemory() / 8
        outInfo.lowMemory = false
    }

    open fun getRunningAppProcesses(): List<Any> = emptyList()

    /** Android's `ActivityManager.MemoryInfo`, whose fields extensions read after the call. */
    open class MemoryInfo {
        @JvmField
        var availMem: Long = 0

        @JvmField
        var totalMem: Long = 0

        @JvmField
        var threshold: Long = 0

        @JvmField
        var lowMemory: Boolean = false
    }
}
