package android.os

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Android's `Build` class, as far as extension bytecode links against it. The desktop JVM has no
 * device identity; the constants are plausible fakes that let providers that probe the SDK level
 * load and run.
 */
object Build {
    object VERSION {
        const val RELEASE: String = "14"
        const val SDK_INT: Int = 34
        const val SECURITY_PATCH: String = "2024-01-01"
        const val HARDWARE: String = "generic"
        const val BOOTLOADER: String = "unknown"
        const val RADIO: String = "unknown"
        const val SERIAL: String = "unknown"
        const val UNKNOWN: Int = 0
    }

    const val PRODUCT: String = "cloudstream-desktop"
    const val DEVICE: String = "cloudstream-desktop"
    const val HARDWARE: String = "cloudstream-desktop"
    const val MANUFACTURER: String = "Freebuff"
    const val BRAND: String = "cloudstream-desktop"
    const val MODEL: String = "CloudStream Desktop"
    const val BOARD: String = "unknown"
    const val CPU_ABI: String = "x86_64"
    const val CPU_ABI2: String = "unknown"
    const val DEVICE_PRODUCT: String = "cloudstream-desktop"
    const val DISPLAY: String = "CloudStream Desktop"
    const val USER: String = "desktop"
    const val HOST: String = "desktop"
    const val TAGS: String = "release-keys"
    const val FINGERPRINT: String =
        "Freebuff/cloudstream-desktop/cloudstream-desktop:14/34:user/release-keys"
    const val ID: String = "release-keys"
    const val TYPE: String = "user"
    const val ENCODING_KEY: String = "unknown"
    val SUPPORTED_64_BIT_ABIS: List<String> = listOf("x86_64")
    val SUPPORTED_32_BIT_ABIS: List<String> = listOf("x86")
    val SUPPORTED_ABIS: List<String> = listOf("x86_64")
    const val INSECURE_CODEC: Boolean = false
    const val INSECURE_VM: Boolean = false
    const val UNKNOWN: String = "unknown"

    val securityPatchCurrent: String
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
