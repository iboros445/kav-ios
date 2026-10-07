// The GeoJSON classes TileMap.kt builds features with. Each can print itself as GeoJSON for the native map.
package org.maplibre.geojson

import org.json.JSONArray
import org.json.JSONObject

class Point private constructor(val longitude: Double, val latitude: Double) {
    internal fun coordinates(): JSONArray = JSONArray().put(longitude).put(latitude)
    internal fun toJson(): JSONObject = JSONObject().put("type", "Point").put("coordinates", coordinates())

    companion object { fun fromLngLat(lon: Double, lat: Double): Point = Point(lon, lat) }
}

class LineString private constructor(private val points: List<Point>) {
    internal fun toJson(): JSONObject = JSONObject().put("type", "LineString").put("coordinates", JSONArray().also { a -> points.forEach { a.put(it.coordinates()) } })

    companion object { fun fromLngLats(points: List<Point>): LineString = LineString(points) }
}

class Feature private constructor(private val geometry: Any) {
    private val props = JSONObject()

    fun addStringProperty(key: String, value: String?) { props.put(key, value) }
    fun addNumberProperty(key: String, value: Number?) { props.put(key, value?.toDouble()) }
    fun addBooleanProperty(key: String, value: Boolean?) { props.put(key, value) }

    internal fun toJson(): JSONObject = JSONObject().put("type", "Feature")
        .put("geometry", when (geometry) { is Point -> geometry.toJson(); is LineString -> geometry.toJson(); else -> JSONObject.NULL })
        .put("properties", props)

    companion object {
        fun fromGeometry(point: Point): Feature = Feature(point)
        fun fromGeometry(line: LineString): Feature = Feature(line)
    }
}

class FeatureCollection private constructor(private val features: List<Feature>) {
    fun toJson(): String = JSONObject().put("type", "FeatureCollection")
        .put("features", JSONArray().also { a -> features.forEach { a.put(it.toJson()) } }).toString()

    companion object {
        fun fromFeatures(features: List<Feature>): FeatureCollection = FeatureCollection(features)
        internal val EMPTY_JSON = """{"type":"FeatureCollection","features":[]}"""
    }
}
