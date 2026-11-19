import Foundation
import ActivityKit

public struct KavTripAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        public var title: String
        public var subtitle: String
        public var chip: String
        public var tintColorHex: String
        public var modeName: String
        public var arrivalTime: String
        public var progress: Double
        public var dueText: String

        public init(
            title: String = "",
            subtitle: String = "",
            chip: String = "",
            tintColorHex: String = "#007AFF",
            modeName: String = "walk",
            arrivalTime: String = "",
            progress: Double = 0.0,
            dueText: String = ""
        ) {
            self.title = title
            self.subtitle = subtitle
            self.chip = chip
            self.tintColorHex = tintColorHex
            self.modeName = modeName
            self.arrivalTime = arrivalTime
            self.progress = progress
            self.dueText = dueText
        }
    }

    public var tripId: String

    public init(tripId: String) {
        self.tripId = tripId
    }
}
