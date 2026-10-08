package uk.noammm.kav.ios

import org.maplibre.android.maps.MapBridge
import org.maplibre.android.maps.NativeMapFactory

/**
 * Called once from Swift at launch (KavApp.init) with the MapLibre Native implementation in MapBridge.swift.
 * Referencing NativeMapFactory here is what exports it (and NativeMap, StyleLoaded) to Swift.
 */
fun registerMapFactory(factory: NativeMapFactory) {
    MapBridge.factory = factory
}
