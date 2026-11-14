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
    override fun setStyle(styleJson: String, onLoaded: StyleLoaded) {}
    override fun addSource(id: String, geoJson: String) {}
    override fun setSourceData(id: String, geoJson: String) {}
    override fun addLayer(layerJson: String) {}
    override fun setLayerProperties(layerId: String, layoutJson: String, paintJson: String) {}
    override fun addImage(name: String, png: NSData, scale: Double) {}
    override fun setZoomLimits(min: Double, max: Double) {}
    override fun removeLayer(id: String) {}
    override fun removeSource(id: String) {}
    override fun removeImage(name: String) {}
    override fun addLayerBelow(layerJson: String, belowId: String) {}
    override fun setFrameListener(listener: FrameRendered?) {}
    override fun screenToMap(x: Double, y: Double, out: MapPoint) {}
    override fun visibleBounds(out: MapBounds) {}
    override fun setMaximumFps(fps: Int) {}
    override fun moveCamera(lat: Double, lon: Double, zoom: Double, bearing: Double, pitch: Double, padLeft: Double, padTop: Double, padRight: Double, padBottom: Double) {}
    override fun start() {}
    override fun stop() {}
    override fun destroy() {}
}

class Style internal constructor(private val native: NativeMap, private val sourceChanged: (String) -> Unit = {}) {
    class Builder {
        internal var json = ""
        internal val images = LinkedHashMap<String, Bitmap>()
        fun fromJson(j: String): Builder { json = j; return this }
        // Put in once the style has loaded (MapLibreMap.setStyle).
        fun withImage(id: String, image: Bitmap): Builder { images[id] = image; return this }
    }

    fun interface OnStyleLoaded { fun onStyleLoaded(style: Style) }

    private val sources = HashMap<String, Source>()
    private val layers = HashMap<String, Layer>()
    private val images = HashMap<String, Bitmap>()

    var isFullyLoaded: Boolean = false
        internal set

    fun addSource(s: Source) {
        sources[s.id] = s
        if (s is GeoJsonSource) {
            s.map = native
            s.changed = sourceChanged
            native.addSource(s.id, s.initial)
            sourceChanged(s.id)
        }
    }

    fun removeSource(id: String): Boolean {
        val s = sources.remove(id) ?: return false
        (s as? GeoJsonSource)?.let { it.map = null; it.changed = null }
        native.removeSource(id)
        return true
    }

    fun removeSource(s: Source): Boolean = removeSource(s.id)

    fun getSource(id: String): Source? = sources[id]

    @Suppress("UNCHECKED_CAST")
    fun <T : Source> getSourceAs(id: String): T? = sources[id] as? T

    fun addLayer(l: Layer) { layers[l.id] = l; l.map = native; native.addLayer(l.toJsonString()) }
    fun addLayerBelow(l: Layer, below: String) { layers[l.id] = l; l.map = native; native.addLayerBelow(l.toJsonString(), below) }

    fun removeLayer(id: String): Boolean {
        val l = layers.remove(id) ?: return false
        l.map = null
        native.removeLayer(id)
        return true
    }

    fun removeLayer(l: Layer): Boolean = removeLayer(l.id)

    fun removeImage(name: String) { images.remove(name); native.removeImage(name) }
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

class MapLibreMap internal constructor(private val native: NativeMap, private val sourceChanged: (String) -> Unit = {}) {
    val uiSettings = UiSettings()
    val projection = Projection(native)
    // MapLibre Android's level-of-detail and prefetch settings: MapLibre iOS keeps its own.
    var tileLodPitchThreshold: Double = 60.0 * kotlin.math.PI / 180
    var tileLodMinRadius: Double = 3.0
    @Suppress("UNUSED_PARAMETER") fun setPrefetchZoomDelta(delta: Int) {}
    private var minZoom = 0.0
    private var maxZoom = 24.0

    fun setMinZoomPreference(z: Double) { minZoom = z; native.setZoomLimits(minZoom, maxZoom) }
    fun setMaxZoomPreference(z: Double) { maxZoom = z; native.setZoomLimits(minZoom, maxZoom) }

    fun setStyle(builder: Style.Builder, callback: Style.OnStyleLoaded) {
        val style = Style(native, sourceChanged)
        native.setStyle(builder.json) {
            style.isFullyLoaded = true
            for ((id, image) in builder.images) style.addImage(id, image)
            callback.onStyleLoaded(style)
        }
    }

    fun moveCamera(update: CameraUpdate) {
        val p = update.position
        native.moveCamera(p.target.latitude, p.target.longitude, p.zoom, p.bearing, p.tilt, p.padding[0], p.padding[1], p.padding[2], p.padding[3])
    }
}

class Projection internal constructor(private val native: NativeMap) {
    fun fromScreenLocation(p: android.graphics.PointF): org.maplibre.android.geometry.LatLng {
        val out = MapPoint()
        native.screenToMap(p.x.toDouble(), p.y.toDouble(), out)
        return org.maplibre.android.geometry.LatLng(out.lat, out.lon)
    }

    val visibleRegion: org.maplibre.android.geometry.VisibleRegion get() {
        val b = MapBounds().also { native.visibleBounds(it) }
        return org.maplibre.android.geometry.VisibleRegion(org.maplibre.android.geometry.LatLngBounds(b.north, b.east, b.south, b.west))
    }
}

class MapView(ctx: Context, @Suppress("UNUSED_PARAMETER") options: MapLibreMapOptions) : View() {
    fun interface OnDidFinishRenderingFrameListener {
        fun onDidFinishRenderingFrame(fully: Boolean, frameEncodingTime: Double, frameRenderingTime: Double)
    }
    fun interface OnSourceChangedListener { fun onSourceChangedListener(id: String) }

    private val frameListeners = ArrayList<OnDidFinishRenderingFrameListener>()
    private val sourceListeners = ArrayList<OnSourceChangedListener>()
    private val native: NativeMap = MapBridge.factory?.create() ?: StubMap()
    private val map = MapLibreMap(native) { id -> sourceListeners.toList().forEach { it.onSourceChangedListener(id) } }

    override val context: Context = ctx
    override val nativeView: UIView get() = native.view
    // The map fades in once drawn whole (TileMap): its alpha is the native view's.
    override var alpha: Float
        get() = native.view.alpha.toFloat()
        set(v) { native.view.alpha = v.toDouble() }

    fun addOnDidFinishRenderingFrameListener(l: OnDidFinishRenderingFrameListener) {
        frameListeners.add(l)
        if (frameListeners.size == 1) native.setFrameListener(FrameRendered { fully ->
            frameListeners.toList().forEach { it.onDidFinishRenderingFrame(fully, 0.0, 0.0) }
        })
    }

    fun removeOnDidFinishRenderingFrameListener(l: OnDidFinishRenderingFrameListener) {
        frameListeners.remove(l)
        if (frameListeners.isEmpty()) native.setFrameListener(null)
    }

    fun addOnSourceChangedListener(l: OnSourceChangedListener) { sourceListeners.add(l) }
    fun removeOnSourceChangedListener(l: OnSourceChangedListener) { sourceListeners.remove(l) }

    fun setMaximumFps(fps: Int) = native.setMaximumFps(fps)

    fun onCreate(state: Bundle?) {}
    fun onStart() = native.start()
    fun onResume() {}
    fun onPause() {}
    fun onStop() = native.stop()
    fun onDestroy() = native.destroy()
    fun getMapAsync(cb: OnMapReadyCallback) = cb.onMapReady(map)
}
