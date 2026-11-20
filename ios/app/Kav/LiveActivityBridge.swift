import Foundation
import ActivityKit
import KavKit

@available(iOS 16.1, *)
final class LiveActivityBridge: NSObject, KavTripActivityHandler {
    static let shared = LiveActivityBridge()
    private var currentActivity: Activity<KavTripAttributes>?

    func update(
        tripId: String,
        title: String,
        subtitle: String,
        chip: String,
        tintHex: String,
        modeName: String,
        arrivalTime: String,
        progress: Double,
        dueText: String
    ) {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else {
            NSLog("Kav: Live Activities are disabled in system settings")
            return
        }

        let state = KavTripAttributes.ContentState(
            title: title,
            subtitle: subtitle,
            chip: chip,
            tintColorHex: tintHex,
            modeName: modeName,
            arrivalTime: arrivalTime,
            progress: max(0.0, min(1.0, progress)),
            dueText: dueText
        )

        NSLog("Kav: LiveActivity update tripId=\(tripId), chip=\(chip), time=\(arrivalTime)")

        if let activity = currentActivity, activity.attributes.tripId == tripId {
            Task {
                if #available(iOS 16.2, *) {
                    let content = ActivityContent(state: state, staleDate: nil)
                    await activity.update(content)
                } else {
                    await activity.update(using: state)
                }
            }
        } else {
            // Dismiss older activities before launching the new trip activity
            for existing in Activity<KavTripAttributes>.activities {
                Task { await existing.end(dismissalPolicy: .immediate) }
            }

            do {
                let attributes = KavTripAttributes(tripId: tripId)
                let activity: Activity<KavTripAttributes>
                if #available(iOS 16.2, *) {
                    let content = ActivityContent(state: state, staleDate: nil)
                    activity = try Activity.request(attributes: attributes, content: content, pushType: nil)
                } else {
                    activity = try Activity.request(attributes: attributes, contentState: state, pushType: nil)
                }
                currentActivity = activity
                NSLog("Kav: Live Activity started successfully id=\(activity.id)")
            } catch {
                NSLog("Kav: Live Activity request failed: \(error.localizedDescription)")
            }
        }
    }

    func end() {
        NSLog("Kav: ending Live Activities")
        let activities = Activity<KavTripAttributes>.activities
        for activity in activities {
            Task {
                await activity.end(dismissalPolicy: .immediate)
            }
        }
        currentActivity = nil
    }
}
