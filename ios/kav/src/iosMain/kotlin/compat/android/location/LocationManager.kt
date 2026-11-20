package android.location

import androidx.core.location.LocationListenerCompat

class LocationManager constructor() {
    val allProviders: List<String> get() = listOf(FUSED_PROVIDER, PASSIVE_PROVIDER)
    fun isProviderEnabled(provider: String): Boolean = provider == FUSED_PROVIDER && LocationHub.servicesEnabled
    fun getLastKnownLocation(provider: String): Location? = if (provider == PASSIVE_PROVIDER || provider == FUSED_PROVIDER) LocationHub.lastKnown else null
    val isLocationEnabled: Boolean get() = LocationHub.servicesEnabled

    internal val subscriptions = HashMap<LocationListenerCompat, (Location) -> Unit>()

    companion object {
        const val GPS_PROVIDER = "gps"
        const val NETWORK_PROVIDER = "network"
        const val PASSIVE_PROVIDER = "passive"
        const val FUSED_PROVIDER = "fused"
    }
}
