//@cmp
// The MapLibre Android classes TileMap.kt drives, kept thin: they translate calls into style-spec JSON and forward
// them to a NativeMap (Swift, MapLibre Native for iOS). See NativeMap.kt.
package org.maplibre.android.maps

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import org.maplibre.android.camera.CameraUpdate
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.Source
import org.maplibre.geojson.FeatureCollection
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIScreen
import platform.UIKit.UIView
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import java.io.ByteArrayOutputStream

class MapLibreMapOptions private constructor() {
    fun textureMode(on: Boolean) = this
    fun compassEnabled(on: Boolean) = this
    fun logoEnabled(on: Boolean) = this
    fun attributionEnabled(on: Boolean) = this
    fun foregroundLoadColor(color: Int) = this

    companion object { fun createFromAttributes(ctx: Context): MapLibreMapOptions = MapLibreMapOptions() }
}

fun interface OnMapReadyCallback { fun onMapReady(map: MapLibreMap) }

class UiSettings { fun setAllGesturesEnabled(enabled: Boolean) {} }

/** No native map registered: an empty view, and the style never loads. */
private class StubMap : NativeMap {
    override val view: UIView = UIView()
    override fun setStyle(styleJson: String, onLoaded: () -> Unit) {}
    override fun addSource(id: String, geoJson: String) {}
    override fun setSourceData(id: String, geoJson: String) {}
    override fun addLayer(layerJson: String) {}
    override fun setLayerProperties(layerId: String, layoutJson: String, paintJson: String) {}
    override fun addImage(name: String, png: NSData, scale: Double) {}
    override fun setZoomLimits(min: Double, max: Double) {}
    override fun moveCamera(lat: Double, lon: Double, zoom: Double, bearing: Double, pitch: Double, padLeft: Double, padTop: Double, padRight: Double, padBottom: Double) {}
    override fun start() {}
    override fun stop() {}
    override fun destroy() {}
}

class Style internal constructor(private val native: NativeMap) {
    class Builder {
        internal var json = ""
        fun fromJson(j: String): Builder { json = j; return this }
    }

    fun interface OnStyleLoaded { fun onStyleLoaded(style: Style) }

    private val sources = HashMap<String, Source>()
    private val layers = HashMap<String, Layer>()
    private val images = HashMap<String, Bitmap>()

    var isFullyLoaded: Boolean = false
        internal set

    fun addSource(s: Source) {
        sources[s.id] = s
        if (s is GeoJsonSource) { s.map = native; native.addSource(s.id, FeatureCollection.EMPTY_JSON) }
    }

    fun getSource(id: String): Source? = sources[id]

    @Suppress("UNCHECKED_CAST")
    fun <T : Source> getSourceAs(id: String): T? = sources[id] as? T

    fun addLayer(l: Layer) { layers[l.id] = l; l.map = native; native.addLayer(l.toJsonString()) }
    fun getLayer(id: String): Layer? = layers[id]
    fun getImage(name: String): Bitmap? = images[name]

    fun addImage(name: String, bitmap: Bitmap) {
        images[name] = bitmap
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        val bytes = out.toByteArray()
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.convert()) }
        native.addImage(name, data, UIScreen.mainScreen.scale)
    }
}

class MapLibreMap internal constructor(private val native: NativeMap) {
    val uiSettings = UiSettings()
    private var minZoom = 0.0
    private var maxZoom = 24.0

    fun setMinZoomPreference(z: Double) { minZoom = z; native.setZoomLimits(minZoom, maxZoom) }
    fun setMaxZoomPreference(z: Double) { maxZoom = z; native.setZoomLimits(minZoom, maxZoom) }

    fun setStyle(builder: Style.Builder, callback: Style.OnStyleLoaded) {
        val style = Style(native)
        native.setStyle(builder.json) { style.isFullyLoaded = true; callback.onStyleLoaded(style) }
    }

    fun moveCamera(update: CameraUpdate) {
        val p = update.position
        native.moveCamera(p.target.latitude, p.target.longitude, p.zoom, p.bearing, p.tilt, p.padding[0], p.padding[1], p.padding[2], p.padding[3])
    }
}

class MapView(@Suppress("UNUSED_PARAMETER") ctx: Context, @Suppress("UNUSED_PARAMETER") options: MapLibreMapOptions) : View() {
    private val native: NativeMap = MapBridge.factory?.create() ?: StubMap()
    private val map = MapLibreMap(native)

    override val nativeView: UIView get() = native.view

    fun onCreate(state: Bundle?) {}
    fun onStart() = native.start()
    fun onResume() {}
    fun onPause() {}
    fun onStop() = native.stop()
    fun onDestroy() = native.destroy()
    fun getMapAsync(cb: OnMapReadyCallback) = cb.onMapReady(map)
}
