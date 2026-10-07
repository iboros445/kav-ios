package org.maplibre.android.style.sources

import org.maplibre.android.maps.NativeMap
import org.maplibre.geojson.FeatureCollection

abstract class Source internal constructor(val id: String)

class GeoJsonSource(id: String) : Source(id) {
    internal var map: NativeMap? = null

    fun setGeoJson(fc: FeatureCollection) { map?.setSourceData(id, fc.toJson()) }
}
