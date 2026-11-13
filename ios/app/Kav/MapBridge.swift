import Foundation
import UIKit
import MapLibre
import KavKit

// The real map behind Kav's MapLibre Android shim (ios/kav/.../org/maplibre/android/maps). Upstream keeps the camera
// and gestures in Compose and uses MapLibre only as a renderer, so this view takes no touches and just draws what
// TileMap.kt asks for: a base style, GeoJSON sources, style-spec layers, images and a camera.
//
// Layers arrive as style-spec JSON. Each property is set through KVC on the matching MLN*StyleLayer from
// NSExpression(mglJSONObject:), so a property upstream starts using needs at most one entry in `propertyNames`.

final class MapLibreFactory: NSObject, KavNativeMapFactory {
    func create() -> KavNativeMap { MapLibreNativeMap() }
}

final class MapLibreNativeMap: NSObject, KavNativeMap, MLNMapViewDelegate {
    private let mapView: MLNMapView
    private var pendingLoaded: KavStyleLoaded?
    private var frameListener: KavFrameRendered?

    var view: UIView { mapView }

    override init() {
        mapView = MLNMapView(frame: CGRect(x: 0, y: 0, width: 1, height: 1))
        super.init()
        mapView.delegate = self
        mapView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        mapView.isUserInteractionEnabled = false
        mapView.logoView.isHidden = true
        mapView.attributionButton.isHidden = true
        mapView.compassView.isHidden = true
        mapView.showsScale = false
        mapView.automaticallyAdjustsContentInset = false
        mapView.backgroundColor = .clear
    }

    // MARK: style

    func setStyle(styleJson: String, onLoaded: KavStyleLoaded) {
        // Android's asset:// has no iOS equivalent: point glyphs and sprites at the copies in the app bundle.
        let assets = "file://" + Bundle.main.bundlePath + "/assets/"
        let json = styleJson.replacingOccurrences(of: "asset://", with: assets)
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("kav-style-\(UUID().uuidString).json")
        do {
            try json.write(to: url, atomically: true, encoding: .utf8)
        } catch {
            NSLog("Kav map: cannot write style: \(error)")
            return
        }
        pendingLoaded = onLoaded
        mapView.styleURL = url
    }

    func mapView(_ mapView: MLNMapView, didFinishLoading style: MLNStyle) {
        // Kav eases nothing the map would: its fades are drawn step by step (TransitionOptions(0, 0) on Android).
        style.transition = MLNTransition(duration: 0, delay: 0)
        let done = pendingLoaded
        pendingLoaded = nil
        done?.loaded()
    }

    func mapViewDidFailLoadingMap(_ mapView: MLNMapView, withError error: Error) {
        Self.log("style failed to load: \(error)")
    }

    // MARK: sources

    private func shape(_ geoJson: String) -> MLNShape? {
        guard let data = geoJson.data(using: .utf8) else { return nil }
        return try? MLNShape(data: data, encoding: String.Encoding.utf8.rawValue)
    }

    func addSource(id: String, geoJson: String) {
        guard let style = mapView.style, style.source(withIdentifier: id) == nil else { return }
        style.addSource(MLNShapeSource(identifier: id, shape: shape(geoJson), options: nil))
    }

    func setSourceData(id: String, geoJson: String) {
        (mapView.style?.source(withIdentifier: id) as? MLNShapeSource)?.shape = shape(geoJson)
    }

    func removeSource(id: String) {
        guard let style = mapView.style, let source = style.source(withIdentifier: id) else { return }
        style.removeSource(source)
    }

    // MARK: layers

    /// Style-spec names whose MLN property name is not the plain camel-case form.
    private static let propertyNames: [String: String] = [
        "icon-image": "iconImageName",
        "icon-rotate": "iconRotation",
        "icon-allow-overlap": "iconAllowsOverlap",
        "icon-ignore-placement": "iconIgnoresPlacement",
        "line-dasharray": "lineDashPattern",
        "text-field": "text",
        "text-allow-overlap": "textAllowsOverlap",
        "text-ignore-placement": "textIgnoresPlacement",
    ]

    private static func key(_ specName: String) -> String {
        if let k = propertyNames[specName] { return k }
        let parts = specName.split(separator: "-")
        return parts.enumerated().map { $0.offset == 0 ? String($0.element) : $0.element.capitalized }.joined()
    }

    /// A style-spec value: an expression array starts with an operator name; anything else is a literal.
    private static func expression(_ value: Any) -> NSExpression {
        if let a = value as? [Any], let op = a.first as? String, !op.isEmpty {
            return NSExpression(mglJSONObject: a)
        }
        return NSExpression(forConstantValue: value)
    }

    /// A map error, to the console and to Documents/kav.log (Files app → Kav), where Kotlin's Log writes too: a phone
    /// without a Mac shows nothing else.
    static func log(_ message: String) {
        NSLog("Kav map: \(message)")
        guard let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first,
              let line = "E/KavMap: \(message)\n".data(using: .utf8) else { return }
        let url = docs.appendingPathComponent("kav.log")
        if let h = try? FileHandle(forWritingTo: url) {
            h.seekToEndOfFile()
            h.write(line)
            h.closeFile()
        } else {
            try? line.write(to: url)
        }
    }

    /// Each property on its own: one MapLibre refuses (it raises) is logged and left at its default, and the rest still
    /// apply.
    private static func apply(_ props: [String: Any], to layer: MLNStyleLayer) {
        for (name, value) in props {
            let k = key(name)
            guard layer.responds(to: NSSelectorFromString(k)) else {
                log("\(type(of: layer)) has no property \(k) for \(name)")
                continue
            }
            if let failed = KavCatch({ layer.setValue(expression(value), forKey: k) }) {
                log("layer \(layer.identifier): \(name) = \(value) refused: \(failed)")
            }
        }
    }

    func addLayer(layerJson: String) { insert(layerJson, below: nil) }

    func addLayerBelow(layerJson: String, belowId: String) { insert(layerJson, below: belowId) }

    func removeLayer(id: String) {
        guard let style = mapView.style, let layer = style.layer(withIdentifier: id) else { return }
        style.removeLayer(layer)
    }

    private func insert(_ layerJson: String, below: String?) {
        guard let style = mapView.style,
              let data = layerJson.data(using: .utf8),
              let obj = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              let id = obj["id"] as? String, let type = obj["type"] as? String,
              let sourceId = obj["source"] as? String,
              let source = style.source(withIdentifier: sourceId),
              style.layer(withIdentifier: id) == nil else {
            Self.log("cannot add layer \(layerJson.prefix(120))")
            return
        }
        let layer: MLNVectorStyleLayer
        switch type {
        case "line": layer = MLNLineStyleLayer(identifier: id, source: source)
        case "circle": layer = MLNCircleStyleLayer(identifier: id, source: source)
        case "symbol": layer = MLNSymbolStyleLayer(identifier: id, source: source)
        case "fill": layer = MLNFillStyleLayer(identifier: id, source: source)
        case "fill-extrusion": layer = MLNFillExtrusionStyleLayer(identifier: id, source: source)
        default:
            Self.log("unsupported layer type \(type)")
            return
        }
        // MapLibre raises an Objective-C exception for a filter or value it can't take, which would end the app: a
        // refused filter leaves the layer unfiltered, a refused property its default, and the map still opens.
        if let filter = obj["filter"],
           let failed = KavCatch({ layer.predicate = NSPredicate(mglJSONObject: filter) }) {
            Self.log("layer \(id): filter \(filter) refused: \(failed)")
        }
        Self.apply(obj["layout"] as? [String: Any] ?? [:], to: layer)
        Self.apply(obj["paint"] as? [String: Any] ?? [:], to: layer)
        let failed = KavCatch {
            if let below, let under = style.layer(withIdentifier: below) {
                style.insertLayer(layer, below: under)
            } else {
                style.addLayer(layer)
            }
        }
        if let failed { Self.log("layer \(id) not added: \(failed)") }
    }

    func setLayerProperties(layerId: String, layoutJson: String, paintJson: String) {
        guard let layer = mapView.style?.layer(withIdentifier: layerId) else { return }
        for json in [layoutJson, paintJson] {
            if let data = json.data(using: .utf8),
               let props = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] {
                Self.apply(props, to: layer)
            }
        }
    }

    func addImage(name: String, png: Data, scale: Double) {
        guard let image = UIImage(data: png, scale: CGFloat(scale)) else { return }
        mapView.style?.setImage(image, forName: name)
    }

    func removeImage(name: String) {
        mapView.style?.removeImage(forName: name)
    }

    // MARK: camera

    func setZoomLimits(min: Double, max: Double) {
        mapView.minimumZoomLevel = min
        mapView.maximumZoomLevel = max
    }

    func moveCamera(lat: Double, lon: Double, zoom: Double, bearing: Double, pitch: Double,
                    padLeft: Double, padTop: Double, padRight: Double, padBottom: Double) {
        // Padding comes in pixels (Compose); MapLibre iOS works in points.
        let s = Double(UIScreen.main.scale)
        let inset = UIEdgeInsets(top: padTop / s, left: padLeft / s, bottom: padBottom / s, right: padRight / s)
        if mapView.contentInset != inset { mapView.contentInset = inset }
        // setCenter(zoomLevel:direction:) leaves the pitch alone, so set the pitch first.
        if abs(mapView.camera.pitch - pitch) > 0.01 {
            let c = mapView.camera
            c.pitch = CGFloat(pitch)
            mapView.setCamera(c, animated: false)
        }
        mapView.setCenter(CLLocationCoordinate2D(latitude: lat, longitude: lon), zoomLevel: zoom,
                          direction: bearing, animated: false)
    }

    // Points come in pixels (Compose); MapLibre iOS works in points.
    func screenToMap(x: Double, y: Double, out: KavMapPoint) {
        let s = Double(UIScreen.main.scale)
        let c = mapView.convert(CGPoint(x: x / s, y: y / s), toCoordinateFrom: mapView)
        out.lat = c.latitude
        out.lon = c.longitude
    }

    func visibleBounds(out: KavMapBounds) {
        let b = mapView.visibleCoordinateBounds
        out.south = b.sw.latitude
        out.west = b.sw.longitude
        out.north = b.ne.latitude
        out.east = b.ne.longitude
    }

    // MARK: frames

    func setFrameListener(listener: KavFrameRendered?) {
        frameListener = listener
    }

    func mapViewDidFinishRenderingFrame(_ mapView: MLNMapView, fullyRendered: Bool) {
        frameListener?.rendered(fully: fullyRendered)
    }

    func setMaximumFps(fps: Int32) {
        mapView.preferredFramesPerSecond = MLNMapViewPreferredFramesPerSecond(rawValue: Int(fps))
    }

    // MARK: lifecycle

    func start() {}
    func stop() {}
    func destroy() {
        frameListener = nil
        mapView.delegate = nil
        mapView.removeFromSuperview()
    }
}
