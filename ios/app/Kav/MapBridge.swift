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

    // Diagnostics for kav.log: a phone without a Mac shows nothing else of why a map stays blank.
    private static var mapLibreLogged = 0
    private static let hookMapLibreLog: Void = {
        // MapLibre's own warnings and errors (a sprite, glyph or tile it couldn't load), at most 150 lines a launch.
        MLNLoggingConfiguration.shared.loggingLevel = .warning
        MLNLoggingConfiguration.shared.handler = { level, _, _, message in
            guard mapLibreLogged < 150 else { return }
            mapLibreLogged += 1
            log("MapLibre \(level == .error ? "error" : "warning"): \(message)")
        }
    }()
    private static var made = 0
    private let number: Int
    private var framesSeen = 0
    private var fullSeen = false

    override init() {
        _ = Self.hookMapLibreLog
        Self.made += 1
        number = Self.made
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
        // Where this style takes its tiles, icons and fonts from.
        if let data = json.data(using: .utf8), let o = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] {
            let sources = (o["sources"] as? [String: Any] ?? [:]).map { id, s -> String in
                let s = s as? [String: Any] ?? [:]
                return "\(id)=\(s["url"] ?? (s["tiles"] as? [Any])?.first ?? s["type"] ?? "?")"
            }
            Self.log("map \(number): style \(o["name"] ?? "?") (\((o["layers"] as? [Any])?.count ?? 0) layers), "
                + "sources \(sources), sprite \(o["sprite"] ?? "none"), glyphs \(o["glyphs"] ?? "none"), "
                + "view \(Int(mapView.bounds.width))x\(Int(mapView.bounds.height))")
        }
        mapView.styleURL = url
    }

    func mapView(_ mapView: MLNMapView, didFinishLoading style: MLNStyle) {
        Self.log("map \(number): style loaded, view \(Int(mapView.bounds.width))x\(Int(mapView.bounds.height)) "
            + "in a window: \(mapView.window != nil)")
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
        if let failed = KavCatch({ style.addSource(MLNShapeSource(identifier: id, shape: shape(geoJson), options: nil)) }) {
            Self.log("source \(id) not added: \(failed)")
        }
    }

    func setSourceData(id: String, geoJson: String) {
        (mapView.style?.source(withIdentifier: id) as? MLNShapeSource)?.shape = shape(geoJson)
    }

    func removeSource(id: String) {
        guard let style = mapView.style, let source = style.source(withIdentifier: id) else { return }
        // MapLibre raises for a source a layer still uses.
        if let failed = KavCatch({ style.removeSource(source) }) { Self.log("source \(id) not removed: \(failed)") }
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
            // A colour constant (PropertyFactory's ["to-color", "rgba(...)"]) as a UIColor. MapLibre would turn it into
            // CAST('rgba(...)', 'UIColor'), which Foundation's predicate security stops by ending the app (SIGILL, not
            // an exception KavCatch could catch). A colour read from a feature, ["to-color", ["get", ...]], is fine.
            if op == "to-color", a.count == 2, let s = a[1] as? String, let c = color(s) {
                return NSExpression(forConstantValue: c)
            }
            return NSExpression(mglJSONObject: a)
        }
        return NSExpression(forConstantValue: value)
    }

    /// "rgba(r,g,b,a)", "rgb(r,g,b)" or "#rgb"/"#rrggbb"/"#rrggbbaa", as the style spec writes colours.
    static func color(_ s: String) -> UIColor? {
        let t = s.trimmingCharacters(in: .whitespaces).lowercased()
        if t.hasPrefix("rgb"), let open = t.firstIndex(of: "("), let close = t.lastIndex(of: ")") {
            let n = t[t.index(after: open)..<close].split(separator: ",")
                .compactMap { Double($0.trimmingCharacters(in: .whitespaces)) }
            guard n.count == 3 || n.count == 4 else { return nil }
            return UIColor(red: n[0] / 255, green: n[1] / 255, blue: n[2] / 255, alpha: n.count == 4 ? n[3] : 1)
        }
        if t.hasPrefix("#") {
            var hex = String(t.dropFirst())
            if hex.count == 3 { hex = hex.map { "\($0)\($0)" }.joined() }
            guard hex.count == 6 || hex.count == 8, let v = UInt64(hex, radix: 16) else { return nil }
            let has = hex.count == 8
            let r = Double((v >> (has ? 24 : 16)) & 0xff), g = Double((v >> (has ? 16 : 8)) & 0xff)
            let b = Double((v >> (has ? 8 : 0)) & 0xff), al = has ? Double(v & 0xff) / 255 : 1
            return UIColor(red: r / 255, green: g / 255, blue: b / 255, alpha: al)
        }
        return nil
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
        if let failed = KavCatch({ style.removeLayer(layer) }) { Self.log("layer \(id) not removed: \(failed)") }
    }

    private func insert(_ layerJson: String, below: String?) {
        guard let style = mapView.style,
              let data = layerJson.data(using: .utf8),
              let obj = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              let id = obj["id"] as? String, let type = obj["type"] as? String,
              let sourceId = obj["source"] as? String else {
            Self.log("cannot read layer \(layerJson.prefix(120))")
            return
        }
        // TileMap puts its layers in again for each style it's handed (a fresh Style on the Kotlin side); one this map
        // already has is kept as it is.
        if style.layer(withIdentifier: id) != nil { return }
        guard let source = style.source(withIdentifier: sourceId) else {
            Self.log("layer \(id) not added: no source \(sourceId)")
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
        // A bare true or false is a filter in the style spec (Expression.literal(true) for the rider's layer), but
        // MapLibre iOS reads only arrays, and raises for NSPredicate(value:) too: true is the same as no filter, and
        // false shows nothing, so the layer is hidden instead.
        if let filter = obj["filter"] as? Bool {
            if !filter { layer.isVisible = false }
        } else if let filter = obj["filter"],
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
        if let failed = KavCatch({ mapView.style?.setImage(image, forName: name) }) { Self.log("image \(name) not added: \(failed)") }
    }

    func removeImage(name: String) {
        if let failed = KavCatch({ mapView.style?.removeImage(forName: name) }) { Self.log("image \(name) not removed: \(failed)") }
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
        framesSeen += 1
        if framesSeen == 1 || (fullyRendered && !fullSeen) || framesSeen == 300 {
            if fullyRendered { fullSeen = true }
            let f = mapView.convert(mapView.bounds, to: nil)
            Self.log("map \(number): frame \(framesSeen) fully=\(fullyRendered), on screen at \(Int(f.minX)),\(Int(f.minY)) "
                + "\(Int(f.width))x\(Int(f.height)), alpha \(mapView.alpha), hidden \(mapView.isHidden), "
                + "in a window: \(mapView.window != nil), zoom \(String(format: "%.1f", mapView.zoomLevel)) "
                + "at \(String(format: "%.4f,%.4f", mapView.centerCoordinate.latitude, mapView.centerCoordinate.longitude))")
        }
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
