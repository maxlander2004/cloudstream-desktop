package android.content.pm

/** Android's `ApplicationInfo`, holding the fields extensions read. */
open class ApplicationInfo {
    var packageName: String = "app.cloudstream.desktop"
    var name: String? = null
    var processName: String? = null
    var flags: Int = 0
    var targetSdkVersion: Int = 34
    var minSdkVersion: Int = 21
    var uid: Int = 0

    /**
     * Where the app's APK lives; extensions read it to hash the install's signature.
     *
     * The desktop host is not installed from an APK, so this is an empty string — an
     * extension that hashes it gets no signature, which is what it does on a device whose
     * app was not installed the way it expects.
     */
    @JvmField
    var sourceDir: String = ""

    @JvmField
    var publicSourceDir: String = ""

    @JvmField
    var dataDir: String = ""

    /** The app label a launcher would show, which is all a provider wants it for. */
    open fun loadLabel(pm: PackageManager): CharSequence = name ?: packageName

    companion object {
        const val FLAG_DEBUGGABLE = 1 shl 1
        const val FLAG_SYSTEM = 1 shl 0
    }
}

/** Android's `PackageInfo`, holding the fields extensions read. */
open class PackageInfo {
    var packageName: String = "app.cloudstream.desktop"
    var versionName: String? = null
    var versionCode: Int = 0

    /** The install's signing certificates, which some extensions fingerprint for licensing. */
    @JvmField
    var signingInfo: SigningInfo = SigningInfo()
}

/**
 * Android's `SigningInfo`: the signing certificates an install was verified against.
 *
 * Nothing signs a desktop install, so there are none. An extension that gates a feature on a
 * known certificate therefore finds none and does not enable it — the honest answer, rather
 * than a fabricated certificate it would trust.
 */
open class SigningInfo {
    open fun getApkContentsSigners(): Array<Signature> = emptyArray()

    open fun getSigningCertificateHistory(): Array<Signature> = emptyArray()

    open fun hasMultipleSigners(): Boolean = false

    open fun hasPastSigningCertificates(): Boolean = false
}

/** Android's `Signature`, an X.509 certificate as bytes. Never constructed here. */
open class Signature(private val bytes: ByteArray = ByteArray(0)) {
    open fun toByteArray(): ByteArray = bytes

    open fun toCharsString(): String = bytes.joinToString("") { "%02x".format(it) }

    override fun equals(other: Any?): Boolean = other is Signature && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()
}
