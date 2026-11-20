import Foundation
import ActivityKit
import KavKit
import UIKit

@available(iOS 16.1, *)
final class LiveActivityBridge: NSObject, KavTripActivityHandler {
    static let shared = LiveActivityBridge()
    private var currentActivity: Activity<KavTripAttributes>?

    override init() {
        super.init()
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleAppWillTerminate),
            name: UIApplication.willTerminateNotification,
            object: nil
        )
    }

    @objc private func handleAppWillTerminate() {
        NSLog("Kav: app will terminate, ending all Live Activities immediately")
        endSync()
    }

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

        // Stale after 3 minutes if no new location/step update arrives
        let staleDate = Date(timeIntervalSinceNow: 180)

        if let activity = currentActivity, activity.attributes.tripId == tripId {
            Task {
                if #available(iOS 16.2, *) {
                    let content = ActivityContent(state: state, staleDate: staleDate)
                    await activity.update(content)
                } else {
                    await activity.update(using: state)
                }
            }
        } else {
            // Dismiss older activities before launching the new trip activity
            for existing in Activity<KavTripAttributes>.activities {
                Task {
                    if #available(iOS 16.2, *) {
                        await existing.end(nil, dismissalPolicy: .immediate)
                    } else {
                        await existing.end(dismissalPolicy: .immediate)
                    }
                }
            }

            do {
                let attributes = KavTripAttributes(tripId: tripId)
                let activity: Activity<KavTripAttributes>
                if #available(iOS 16.2, *) {
                    let content = ActivityContent(state: state, staleDate: staleDate)
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
        currentActivity = nil
        let activities = Activity<KavTripAttributes>.activities
        for activity in activities {
            Task {
                if #available(iOS 16.2, *) {
                    await activity.end(nil, dismissalPolicy: .immediate)
                } else {
                    await activity.end(dismissalPolicy: .immediate)
                }
            }
        }
    }

    func endSync() {
        currentActivity = nil
        let activities = Activity<KavTripAttributes>.activities
        guard !activities.isEmpty else { return }
        let sema = DispatchSemaphore(value: 0)
        Task {
            for activity in activities {
                if #available(iOS 16.2, *) {
                    await activity.end(nil, dismissalPolicy: .immediate)
                } else {
                    await activity.end(dismissalPolicy: .immediate)
                }
            }
            sema.signal()
        }
        _ = sema.wait(timeout: .now() + 1.0)
    }
}
