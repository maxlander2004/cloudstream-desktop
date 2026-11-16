package android.net

/**
 * Android's `Network`: one connected network.
 *
 * Extensions use it as an opaque handle for connectivity callbacks — they only pass it back to
 * the system, so an instance with a stable identity is all that is needed.
 */
open class Network {
    override fun toString(): String = "Network(desktop)"
}

/**
 * Android's `NetworkRequest`, describing the kind of network a caller wants.
 *
 * The desktop host always has a network (see [ConnectivityManager]), so this only has to exist
 * and be buildable; the request is never matched against anything.
 */
open class NetworkRequest {
    /** Android's `NetworkRequest.Builder`; capability and transport calls are recorded only. */
    class Builder {
        private val capabilities = mutableSetOf<Int>()
        private val transports = mutableSetOf<Int>()

        fun addCapability(capability: Int): Builder = apply { capabilities += capability }

        fun removeCapability(capability: Int): Builder = apply { capabilities -= capability }

        fun addTransportType(transportType: Int): Builder =
            apply { transports += transportType }

        fun removeTransportType(transportType: Int): Builder =
            apply { transports -= transportType }

        fun build(): NetworkRequest = NetworkRequest()
    }
}

/** Android's `NetworkCapabilities`, of which extensions use the named constants. */
object NetworkCapabilities {
    const val NET_CAPABILITY_INTERNET = 12
    const val NET_CAPABILITY_VALIDATED = 16
    const val NET_CAPABILITY_NOT_METERED = 11
    const val TRANSPORT_WIFI = 1
    const val TRANSPORT_CELLULAR = 0
    const val TRANSPORT_ETHERNET = 3
    const val TRANSPORT_VPN = 4
}
