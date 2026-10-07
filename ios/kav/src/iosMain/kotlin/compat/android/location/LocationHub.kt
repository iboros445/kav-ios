// CoreLocation, once. Every Android location and heading API in the compat layer sits on this single manager.
package android.location

import kotlinx.cinterop.useContents
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLHeading
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLDistanceFilterNone
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSDate
import platform.Foundation.NSProcessInfo
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject

class Location(var provider: String? = null) {
    var latitude: Double = 0.0
    var longitude: Double = 0.0
    var accuracy: Float = 0f
    var speed: Float = 0f
    var bearing: Float = 0f
    var altitude: Double = 0.0
    var time: Long = 0
    var elapsedRealtimeNanos: Long = 0
    private var hasAcc = false
    private var hasSp = false

    fun hasAccuracy(): Boolean = hasAcc
    fun setAccuracy(v: Float) { accuracy = v; hasAcc = true }
    fun hasSpeed(): Boolean = hasSp
    fun hasBearing(): Boolean = bearing >= 0f

    internal companion object {
        fun from(l: CLLocation): Location = Location("fused").also {
            l.coordinate.useContents { it.latitude = latitude; it.longitude = longitude }
            if (l.horizontalAccuracy >= 0) it.setAccuracy(l.horizontalAccuracy.toFloat())
            if (l.speed >= 0) { it.speed = l.speed.toFloat(); it.hasSp = true }
            it.bearing = l.course.toFloat()
            it.altitude = l.altitude
            val ageS = NSDate().timeIntervalSince1970 - l.timestamp.timeIntervalSince1970
            it.time = (l.timestamp.timeIntervalSince1970 * 1000).toLong()
            it.elapsedRealtimeNanos = ((NSProcessInfo.processInfo.systemUptime - ageS) * 1_000_000_000.0).toLong()
        }
    }
}

internal object LocationHub {
    val manager = CLLocationManager()

    private val locationListeners = ArrayList<(Location) -> Unit>()
    private val headingListeners = ArrayList<(Double) -> Unit>()
    private val authWaiters = ArrayList<(Boolean, Boolean) -> Unit>()
    var lastKnown: Location? = null; private set

    private val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            for (raw in didUpdateLocations) {
                val l = Location.from(raw as? CLLocation ?: continue)
                lastKnown = l
                locationListeners.toList().forEach { it(l) }
            }
        }

        override fun locationManager(manager: CLLocationManager, didUpdateHeading: CLHeading) {
            val h = if (didUpdateHeading.trueHeading >= 0) didUpdateHeading.trueHeading else didUpdateHeading.magneticHeading
            headingListeners.toList().forEach { it(h) }
        }

        override fun locationManager(manager: CLLocationManager, didFailWithError: platform.Foundation.NSError) {}

        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            if (status != kCLAuthorizationStatusNotDetermined) {
                val waiters = authWaiters.toList(); authWaiters.clear()
                waiters.forEach { it(fine, coarse) }
                if (locationListeners.isNotEmpty()) startLocation()
            }
        }
    }

    init {
        manager.delegate = delegate
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = kCLDistanceFilterNone
    }

    val status: CLAuthorizationStatus get() = manager.authorizationStatus
    private val granted get() = status == kCLAuthorizationStatusAuthorizedWhenInUse || status == kCLAuthorizationStatusAuthorizedAlways

    /** Precise location is what Android calls FINE; "approximate" on iOS is COARSE only. */
    val coarse: Boolean get() = granted
    val fine: Boolean get() = granted && manager.accuracyAuthorization == platform.CoreLocation.CLAccuracyAuthorization.CLAccuracyAuthorizationFullAccuracy
    val servicesEnabled: Boolean get() = CLLocationManager.locationServicesEnabled()

    fun requestAuthorization(done: (fine: Boolean, coarse: Boolean) -> Unit) {
        if (status != kCLAuthorizationStatusNotDetermined) { done(fine, coarse); return }
        authWaiters.add(done)
        manager.requestWhenInUseAuthorization()
    }

    private fun startLocation() { if (granted) manager.startUpdatingLocation() }

    fun addLocationListener(l: (Location) -> Unit) {
        locationListeners.add(l)
        if (locationListeners.size == 1) startLocation()
    }

    fun removeLocationListener(l: (Location) -> Unit) {
        locationListeners.remove(l)
        if (locationListeners.isEmpty()) manager.stopUpdatingLocation()
    }

    fun addHeadingListener(l: (Double) -> Unit) {
        headingListeners.add(l)
        if (headingListeners.size == 1) manager.startUpdatingHeading()
    }

    fun removeHeadingListener(l: (Double) -> Unit) {
        headingListeners.remove(l)
        if (headingListeners.isEmpty()) manager.stopUpdatingHeading()
    }
}
