package org.maplibre.android.geometry

data class LatLng(val latitude: Double, val longitude: Double)

class LatLngBounds internal constructor(val latitudeNorth: Double, val longitudeEast: Double, val latitudeSouth: Double, val longitudeWest: Double) {
    val center: LatLng get() = LatLng((latitudeNorth + latitudeSouth) / 2, (longitudeEast + longitudeWest) / 2)
    val latitudeSpan: Double get() = kotlin.math.abs(latitudeNorth - latitudeSouth)
    val longitudeSpan: Double get() = kotlin.math.abs(longitudeEast - longitudeWest)
}

class VisibleRegion internal constructor(val latLngBounds: LatLngBounds)
