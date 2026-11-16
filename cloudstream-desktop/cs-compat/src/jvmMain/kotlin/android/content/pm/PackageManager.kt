package android.content.pm

/**
 * Android's `PackageManager`.
 *
 * Extensions ask it what is installed — some providers only offer a "play on device" option
 * when the app for a streaming site is present. The desktop is not a device with an app store,
 * so the answers are those of a phone with nothing third-party installed: the host app itself
 * is ``getPackageInfo``-able, everything else is missing. That keeps a provider from crashing
 * and makes it hide the option it cannot offer, which is what this host wants.
 */
open class PackageManager {
    open fun getPackageInfo(packageName: String, flags: Int): PackageInfo = PackageInfo().apply {
        this.packageName = packageName
    }

    open fun getApplicationInfo(packageName: String, flags: Int): ApplicationInfo =
        ApplicationInfo().apply { this.packageName = packageName }

    open fun getInstalledPackages(flags: Int): List<PackageInfo> = emptyList()

    open fun getInstalledApplications(flags: Int): List<ApplicationInfo> = emptyList()

    open fun getApplicationLabel(info: ApplicationInfo): CharSequence =
        info.name ?: info.packageName

    /** Nothing is ever missing a permission the host asks about. */
    open fun checkPermission(permission: String, packageName: String): Int = PERMISSION_GRANTED

    companion object {
        const val PERMISSION_GRANTED = 0
        const val PERMISSION_DENIED = -1

        const val GET_ACTIVITIES = 1
        const val GET_META_DATA = 128
        const val GET_SIGNATURES = 64
        const val MATCH_DISABLED_COMPONENTS = 512
    }
}
