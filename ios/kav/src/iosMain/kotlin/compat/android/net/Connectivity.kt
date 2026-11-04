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
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsIsWWAN

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

    /** Cellular counts as metered, as on Android; Wi-Fi does not (iOS's Low Data Mode is not visible here). */
    val isActiveNetworkMetered: Boolean get() = memScoped {
        val ref = SCNetworkReachabilityCreateWithName(null, "apple.com") ?: return@memScoped false
        val flags = alloc<SCNetworkReachabilityFlagsVar>()
        SCNetworkReachabilityGetFlags(ref, flags.ptr) && (flags.value and kSCNetworkReachabilityFlagsIsWWAN) != 0u
    }
    fun getNetworkCapabilities(network: Network): NetworkCapabilities = NetworkCapabilities(online())
}
