// The seam between the MapLibre Android API shim and the real map. ios/app/Kav/MapBridge.swift implements these
// interfaces on top of MapLibre Native for iOS and registers the factory at launch (through :kavkit's
// registerMapFactory, since only :kavkit is exported to Swift). With no factory registered the
// app still runs: the map is an empty view and no style ever loads.
package org.maplibre.android.maps

import platform.Foundation.NSData
import platform.UIKit.UIView

interface NativeMap {
    val view: UIView
    /** Replaces the base style. [onLoaded] runs once it is usable (sources and layers may then be added). */
    fun setStyle(styleJson: String, onLoaded: StyleLoaded)
    fun addSource(id: String, geoJson: String)
    fun setSourceData(id: String, geoJson: String)
    /** One style-spec layer object (id, type, source, filter, layout, paint). */
    fun addLayer(layerJson: String)
    fun setLayerProperties(layerId: String, layoutJson: String, paintJson: String)
    fun addImage(name: String, png: NSData, scale: Double)
    fun setZoomLimits(min: Double, max: Double)
    fun removeLayer(id: String)
    fun removeSource(id: String)
    fun removeImage(name: String)
    /** Like addLayer, drawn under the layer [belowId] (on top when there is none). */
    fun addLayerBelow(layerJson: String, belowId: String)
    /** Told after each frame drawn, and whether it was drawn whole (every tile in). Null stops it. */
    fun setFrameListener(listener: FrameRendered?)
    /** The place at a point on the view, in pixels from its top left. */
    fun screenToMap(x: Double, y: Double, out: MapPoint)
    /** The part of the world the view shows. */
    fun visibleBounds(out: MapBounds)
    fun setMaximumFps(fps: Int)
    /** Zoom is MapLibre's; padding is in screen pixels (left, top, right, bottom). */
    fun moveCamera(lat: Double, lon: Double, zoom: Double, bearing: Double, pitch: Double, padLeft: Double, padTop: Double, padRight: Double, padBottom: Double)
    fun start()
    fun stop()
    fun destroy()
}

interface NativeMapFactory { fun create(): NativeMap }

/** A plain interface rather than a Kotlin lambda, so Swift sees a simple protocol with one method. */
fun interface StyleLoaded { fun loaded() }
fun interface FrameRendered { fun rendered(fully: Boolean) }

/** Filled in by the native map (screenToMap). */
class MapPoint { var lat: Double = 0.0; var lon: Double = 0.0 }

/** Filled in by the native map (visibleBounds). */
class MapBounds { var south: Double = 0.0; var west: Double = 0.0; var north: Double = 0.0; var east: Double = 0.0 }

object MapBridge {
    /** Set from Swift: `MapBridge.shared.factory = MapLibreFactory()` */
    var factory: NativeMapFactory? = null
}
