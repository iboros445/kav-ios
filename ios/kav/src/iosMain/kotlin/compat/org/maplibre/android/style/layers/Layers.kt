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
}

abstract class Layer internal constructor(val id: String, private val type: String, private val source: String) {
    private val layout = JSONObject()
    private val paint = JSONObject()
    private var filter: Any? = null
    internal var map: NativeMap? = null   // set once the layer is in a style

    internal fun apply(props: Array<out PropertyValue<*>>) {
        for (p in props) (if (p.layout) layout else paint).put(p.name, p.value)
    }

    internal fun withFilterJson(f: Any) { filter = f }

    /** Changes a live layer's properties. */
    fun setProperties(vararg props: PropertyValue<*>) {
        val l = JSONObject(); val p = JSONObject()
        for (x in props) (if (x.layout) l else p).put(x.name, x.value)
        map?.setLayerProperties(id, l.toString(), p.toString())
    }

    internal fun toJsonString(): String = JSONObject().put("id", id).put("type", type).put("source", source)
        .put("layout", layout).put("paint", paint).also { o -> filter?.let { o.put("filter", it) } }.toString()
}

class LineLayer(id: String, source: String) : Layer(id, "line", source) {
    fun withProperties(vararg props: PropertyValue<*>): LineLayer { apply(props); return this }
    fun withFilter(e: Expression): LineLayer { withFilterJson(e.json); return this }
}

class CircleLayer(id: String, source: String) : Layer(id, "circle", source) {
    fun withProperties(vararg props: PropertyValue<*>): CircleLayer { apply(props); return this }
    fun withFilter(e: Expression): CircleLayer { withFilterJson(e.json); return this }
}

class SymbolLayer(id: String, source: String) : Layer(id, "symbol", source) {
    fun withProperties(vararg props: PropertyValue<*>): SymbolLayer { apply(props); return this }
    fun withFilter(e: Expression): SymbolLayer { withFilterJson(e.json); return this }
}
