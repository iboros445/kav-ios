//@cmp
// MapLibre Android's MapSnapshotter, for the trip picture ShareSheet makes. MapLibre iOS has MLNMapSnapshotter, but
// MapBridge.swift doesn't expose it yet, so a snapshot fails at once and the share card goes without its map picture
// (UPSTREAM_SYNC.md, Known differences).
package org.maplibre.android.snapshotter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style

class MapSnapshot internal constructor(val bitmap: Bitmap) {
    @Suppress("UNUSED_PARAMETER")
    fun pixelForLatLng(latLng: LatLng): PointF = PointF()
}

@Suppress("UNUSED_PARAMETER")
class MapSnapshotter(context: Context, options: Options) {
    class Options(val width: Int, val height: Int) {
        fun withPixelRatio(ratio: Float): Options = this
        fun withStyleBuilder(builder: Style.Builder): Options = this
        fun withCameraPosition(position: CameraPosition?): Options = this
        fun withLogo(show: Boolean): Options = this
        fun withAttribution(show: Boolean): Options = this
    }

    fun interface SnapshotReadyCallback { fun onSnapshotReady(snapshot: MapSnapshot) }
    fun interface ErrorHandler { fun onError(error: String) }

    fun start(callback: SnapshotReadyCallback, errorHandler: ErrorHandler? = null) {
        errorHandler?.onError("Map pictures aren't available on iPhone yet")
    }

    fun cancel() {}
}
