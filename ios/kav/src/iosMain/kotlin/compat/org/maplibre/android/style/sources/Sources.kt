package org.maplibre.android.style.sources

import org.maplibre.android.maps.NativeMap
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection

abstract class Source internal constructor(val id: String)

class GeoJsonSource(id: String) : Source(id) {
    internal var map: NativeMap? = null
    // What the map is told once the data is sent (MapView.OnSourceChangedListener).
    internal var changed: ((String) -> Unit)? = null
    internal var initial: String = FeatureCollection.EMPTY_JSON

    constructor(id: String, feature: Feature) : this(id) { initial = FeatureCollection.fromFeatures(listOf(feature)).toJson() }
    constructor(id: String, features: FeatureCollection) : this(id) { initial = features.toJson() }

    fun setGeoJson(fc: FeatureCollection) {
        val m = map ?: return
        m.setSourceData(id, fc.toJson())
        changed?.invoke(id)
    }

    fun setGeoJson(feature: Feature) = setGeoJson(FeatureCollection.fromFeatures(listOf(feature)))
}
