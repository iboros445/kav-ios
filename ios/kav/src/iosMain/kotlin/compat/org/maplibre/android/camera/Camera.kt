package org.maplibre.android.camera

import org.maplibre.android.geometry.LatLng

class CameraPosition private constructor(
    val target: LatLng, val zoom: Double, val bearing: Double, val tilt: Double, val padding: DoubleArray,
) {
    class Builder {
        private var target = LatLng(0.0, 0.0)
        private var zoom = 0.0
        private var bearing = 0.0
        private var tilt = 0.0
        private var padding = doubleArrayOf(0.0, 0.0, 0.0, 0.0)

        fun target(t: LatLng): Builder { target = t; return this }
        fun zoom(z: Double): Builder { zoom = z; return this }
        fun bearing(b: Double): Builder { bearing = b; return this }
        fun tilt(t: Double): Builder { tilt = t; return this }
        /** left, top, right, bottom, in pixels */
        fun padding(left: Double, top: Double, right: Double, bottom: Double): Builder { padding = doubleArrayOf(left, top, right, bottom); return this }
        fun build(): CameraPosition = CameraPosition(target, zoom, bearing, tilt, padding)
    }
}

class CameraUpdate internal constructor(internal val position: CameraPosition)

object CameraUpdateFactory {
    fun newCameraPosition(p: CameraPosition): CameraUpdate = CameraUpdate(p)
}
