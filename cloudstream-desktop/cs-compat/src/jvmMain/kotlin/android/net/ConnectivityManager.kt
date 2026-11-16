package android.net

/** Android's `NetworkInfo`, reported as always connected: the desktop has no radio. */
open class NetworkInfo {
    open fun isConnected(): Boolean = true

    open fun isAvailable(): Boolean = true

    open fun isConnectedOrConnecting(): Boolean = true

    open fun getType(): Int = ConnectivityManager.TYPE_WIFI

    open fun getTypeName(): String = "WIFI"

    open fun getState(): Any = "CONNECTED"
}

/** Android's `ConnectivityManager`. */
open class ConnectivityManager {
    open fun getActiveNetworkInfo(): NetworkInfo = NetworkInfo()

    open fun getNetworkInfo(type: Int): NetworkInfo = NetworkInfo()

    open fun isActiveNetworkMetered(): Boolean = false

    /**
     * Android's connectivity callbacks.
     *
     * Extensions watch for a network coming back to retry a failed request. The desktop is
     * always online, so a registration is accepted and — like a device whose network never
     * changes — no callback follows. Extensions always keep a timeout path for that.
     */
    open class NetworkCallback {
        open fun onAvailable(network: Network) = Unit

        open fun onLost(network: Network) = Unit

        open fun onUnavailable() = Unit

        open fun onCapabilitiesChanged(network: Network, capabilities: Any?) = Unit
    }

    open fun registerNetworkCallback(request: NetworkRequest, callback: NetworkCallback) = Unit

    open fun unregisterNetworkCallback(callback: NetworkCallback) = Unit

    open fun registerDefaultNetworkCallback(callback: NetworkCallback) = Unit

    companion object {
        const val TYPE_MOBILE = 0
        const val TYPE_WIFI = 1
        const val TYPE_ETHERNET = 9
        const val TYPE_NONE = -1
        const val CONNECTIVITY_ACTION = "android.net.conn.CONNECTIVITY_CHANGE"
    }
}
