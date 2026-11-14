package org.maplibre.android.style.layers

import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.maps.NativeMap
import org.maplibre.android.style.expressions.Expression

object Property {
    const val LINE_CAP_ROUND = "round"
    const val LINE_JOIN_ROUND = "round"
    const val ICON_ROTATION_ALIGNMENT_MAP = "map"
    const val ICON_ROTATION_ALIGNMENT_VIEWPORT = "viewport"
    const val SYMBOL_Z_ORDER_AUTO = "auto"
    const val SYMBOL_Z_ORDER_VIEWPORT_Y = "viewport-y"
    const val SYMBOL_Z_ORDER_SOURCE = "source"
}

// A property's transition. The native map sets none on any property (MapBridge.swift sets the style's to zero),
// which is what Kav asks for wherever it sets one (TransitionOptions(0, 0)).
class TransitionOptions(val duration: Long, val delay: Long, val isEnablePlacementTransitions: Boolean = true) {
}

class PropertyValue<T> internal constructor(internal val name: String, internal val value: Any, internal val layout: Boolean)

/** Style-spec property names; which of layout and paint each one lives in is fixed by the spec. */
object PropertyFactory {
    private fun v(e: Expression) = e.json
    private fun <T> layout(n: String, value: Any) = PropertyValue<T>(n, value, true)
    private fun <T> paint(n: String, value: Any) = PropertyValue<T>(n, value, false)

    fun lineCap(s: String) = layout<String>("line-cap", s)
    fun lineJoin(s: String) = layout<String>("line-join", s)
    fun lineSortKey(e: Expression) = layout<Float>("line-sort-key", v(e))
    fun lineColor(e: Expression) = paint<String>("line-color", v(e))
    fun lineWidth(e: Expression) = paint<Float>("line-width", v(e))
    fun lineDasharray(a: Array<Float>) = paint<Array<Float>>("line-dasharray", JSONArray().also { j -> a.forEach { j.put(it.toDouble()) } })

    fun circleSortKey(e: Expression) = layout<Float>("circle-sort-key", v(e))
    fun circleColor(e: Expression) = paint<String>("circle-color", v(e))
    fun circleRadius(e: Expression) = paint<Float>("circle-radius", v(e))
    fun circleStrokeColor(e: Expression) = paint<String>("circle-stroke-color", v(e))
    fun circleStrokeWidth(e: Expression) = paint<Float>("circle-stroke-width", v(e))

    fun iconImage(e: Expression) = layout<String>("icon-image", v(e))
    fun iconRotate(e: Expression) = layout<Float>("icon-rotate", v(e))
    fun iconAnchor(e: Expression) = layout<String>("icon-anchor", v(e))
    fun iconRotationAlignment(s: String) = layout<String>("icon-rotation-alignment", s)
    fun iconAllowOverlap(b: Boolean) = layout<Boolean>("icon-allow-overlap", b)
    fun iconIgnorePlacement(b: Boolean) = layout<Boolean>("icon-ignore-placement", b)
    fun iconOpacity(e: Expression) = paint<Float>("icon-opacity", v(e))
    fun iconOpacity(f: Float) = paint<Float>("icon-opacity", f.toDouble())
    fun iconRotate(f: Float) = layout<Float>("icon-rotate", f.toDouble())
    fun symbolSortKey(e: Expression) = layout<Float>("symbol-sort-key", v(e))
    fun symbolZOrder(s: String) = layout<String>("symbol-z-order", s)

    fun lineOpacity(f: Float) = paint<Float>("line-opacity", f.toDouble())
    fun lineOpacity(e: Expression) = paint<Float>("line-opacity", v(e))
    fun circleOpacity(f: Float) = paint<Float>("circle-opacity", f.toDouble())
    fun circleOpacity(e: Expression) = paint<Float>("circle-opacity", v(e))
    fun circleStrokeOpacity(f: Float) = paint<Float>("circle-stroke-opacity", f.toDouble())
    fun circleStrokeOpacity(e: Expression) = paint<Float>("circle-stroke-opacity", v(e))
    fun circleRadius(f: Float) = paint<Float>("circle-radius", f.toDouble())

    // A colour constant goes as ["to-color", "rgba(...)"], which the native side reads as an expression.
    private fun colour(c: Int): Any = JSONArray().put("to-color").put(
        "rgba(${(c shr 16) and 0xFF},${(c shr 8) and 0xFF},${c and 0xFF},${((c ushr 24) and 0xFF) / 255.0})")
    fun fillExtrusionColor(c: Int) = paint<String>("fill-extrusion-color", colour(c))
    fun fillExtrusionColor(e: Expression) = paint<String>("fill-extrusion-color", v(e))
    fun fillExtrusionHeight(e: Expression) = paint<Float>("fill-extrusion-height", v(e))
    fun fillExtrusionHeight(f: Float) = paint<Float>("fill-extrusion-height", f.toDouble())
    fun fillExtrusionBase(e: Expression) = paint<Float>("fill-extrusion-base", v(e))
    fun fillExtrusionBase(f: Float) = paint<Float>("fill-extrusion-base", f.toDouble())
    fun fillExtrusionOpacity(f: Float) = paint<Float>("fill-extrusion-opacity", f.toDouble())
    fun fillExtrusionOpacity(e: Expression) = paint<Float>("fill-extrusion-opacity", v(e))
}

abstract class Layer internal constructor(val id: String, private val type: String, private val source: String) {
    private val layout = JSONObject()
    private val paint = JSONObject()
    private var filter: Any? = null
    internal var map: NativeMap? = null   // set once the layer is in a style

    internal fun applyProps(props: Array<out PropertyValue<*>>) {
        for (p in props) (if (p.layout) layout else paint).put(p.name, p.value)
    }

    internal fun withFilterJson(f: Any) { filter = f }

    /** Changes a live layer's properties. */
    fun setProperties(vararg props: PropertyValue<*>) {
        val l = JSONObject(); val p = JSONObject()
        for (x in props) (if (x.layout) l else p).put(x.name, x.value)
        applyProps(props)
        map?.setLayerProperties(id, l.toString(), p.toString())
    }

    internal fun toJsonString(): String = JSONObject().put("id", id).put("type", type).put("source", source)
        .put("layout", layout).put("paint", paint).also { o -> filter?.let { o.put("filter", it) } }.toString()
}

class LineLayer(id: String, source: String) : Layer(id, "line", source) {
    var lineOpacityTransition: TransitionOptions? = null
    fun withProperties(vararg props: PropertyValue<*>): LineLayer { applyProps(props); return this }
    fun withFilter(e: Expression): LineLayer { withFilterJson(e.json); return this }
}

class CircleLayer(id: String, source: String) : Layer(id, "circle", source) {
    var circleOpacityTransition: TransitionOptions? = null
    var circleStrokeOpacityTransition: TransitionOptions? = null
    var circleRadiusTransition: TransitionOptions? = null
    fun withProperties(vararg props: PropertyValue<*>): CircleLayer { applyProps(props); return this }
    fun withFilter(e: Expression): CircleLayer { withFilterJson(e.json); return this }
}

class SymbolLayer(id: String, source: String) : Layer(id, "symbol", source) {
    var iconOpacityTransition: TransitionOptions? = null
    fun withProperties(vararg props: PropertyValue<*>): SymbolLayer { applyProps(props); return this }
    fun withFilter(e: Expression): SymbolLayer { withFilterJson(e.json); return this }
}

class FillExtrusionLayer(id: String, source: String) : Layer(id, "fill-extrusion", source) {
    var fillExtrusionOpacityTransition: TransitionOptions? = null
    fun withProperties(vararg props: PropertyValue<*>): FillExtrusionLayer { applyProps(props); return this }
    fun withFilter(e: Expression): FillExtrusionLayer { withFilterJson(e.json); return this }
}
