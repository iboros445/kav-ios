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
    /** Zoom is MapLibre's; padding is in screen pixels (left, top, right, bottom). */
    fun moveCamera(lat: Double, lon: Double, zoom: Double, bearing: Double, pitch: Double, padLeft: Double, padTop: Double, padRight: Double, padBottom: Double)
    fun start()
    fun stop()
    fun destroy()
}

interface NativeMapFactory { fun create(): NativeMap }

/** A plain interface rather than a Kotlin lambda, so Swift sees a simple protocol with one method. */
fun interface StyleLoaded { fun loaded() }

object MapBridge {
    /** Set from Swift: `MapBridge.shared.factory = MapLibreFactory()` */
    var factory: NativeMapFactory? = null
}
