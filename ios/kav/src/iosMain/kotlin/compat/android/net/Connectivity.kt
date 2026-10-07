package android.net

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.SystemConfiguration.SCNetworkReachabilityCreateWithName
import platform.SystemConfiguration.SCNetworkReachabilityFlagsVar
import platform.SystemConfiguration.SCNetworkReachabilityGetFlags
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsConnectionRequired
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsReachable

class Network internal constructor()

class NetworkCapabilities internal constructor(private val online: Boolean) {
    fun hasCapability(c: Int): Boolean = online && (c == NET_CAPABILITY_INTERNET || c == NET_CAPABILITY_VALIDATED)

    companion object {
        const val NET_CAPABILITY_INTERNET = 12
        const val NET_CAPABILITY_VALIDATED = 16
    }
}

class ConnectivityManager constructor() {
    private fun online(): Boolean = memScoped {
        val ref = SCNetworkReachabilityCreateWithName(null, "apple.com") ?: return@memScoped true
        val flags = alloc<SCNetworkReachabilityFlagsVar>()
        if (!SCNetworkReachabilityGetFlags(ref, flags.ptr)) return@memScoped true
        val f = flags.value
        (f and kSCNetworkReachabilityFlagsReachable) != 0u && (f and kSCNetworkReachabilityFlagsConnectionRequired) == 0u
    }

    val activeNetwork: Network? get() = if (online()) Network() else null
    fun getNetworkCapabilities(network: Network): NetworkCapabilities = NetworkCapabilities(online())
}
