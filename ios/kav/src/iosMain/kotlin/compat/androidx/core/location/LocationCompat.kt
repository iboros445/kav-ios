package androidx.core.location

import android.location.Location
import android.location.LocationHub
import android.location.LocationManager
import android.os.Looper

interface LocationListenerCompat {
    fun onLocationChanged(location: Location)
    fun onProviderEnabled(provider: String) {}
    fun onProviderDisabled(provider: String) {}
}

class LocationRequestCompat private constructor(val intervalMillis: Long, val quality: Int) {
    class Builder(private val interval: Long) {
        private var quality = QUALITY_BALANCED_POWER_ACCURACY
        fun setQuality(q: Int): Builder { quality = q; return this }
        fun build(): LocationRequestCompat = LocationRequestCompat(interval, quality)
    }

    companion object {
        const val QUALITY_HIGH_ACCURACY = 100
        const val QUALITY_BALANCED_POWER_ACCURACY = 102
        const val QUALITY_LOW_POWER = 104
    }
}

object LocationManagerCompat {
    fun isLocationEnabled(lm: LocationManager): Boolean = lm.isLocationEnabled

    @Suppress("UNUSED_PARAMETER")
    fun requestLocationUpdates(
        lm: LocationManager, provider: String, request: LocationRequestCompat,
        listener: LocationListenerCompat, looper: Looper,
    ) {
        if (provider != LocationManager.FUSED_PROVIDER) throw IllegalArgumentException("provider doesn't exist: $provider")
        if (lm.subscriptions.containsKey(listener)) return
        val cb: (Location) -> Unit = { listener.onLocationChanged(it) }
        lm.subscriptions[listener] = cb
        LocationHub.addLocationListener(cb)
    }

    fun removeUpdates(lm: LocationManager, listener: LocationListenerCompat) {
        lm.subscriptions.remove(listener)?.let { LocationHub.removeLocationListener(it) }
    }
}
