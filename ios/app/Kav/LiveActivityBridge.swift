import Foundation
import ActivityKit
import KavKit
import UIKit

private func kavLog(_ msg: String) {
    NSLog("Kav: \(msg)")
    guard let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first else { return }
    let logFile = dir.appendingPathComponent("kav.log")
    let line = "I/LiveActivity: \(msg)\n"
    if let data = line.data(using: .utf8) {
        if FileManager.default.fileExists(atPath: logFile.path) {
            if let handle = try? FileHandle(forWritingTo: logFile) {
                handle.seekToEndOfFile()
                handle.write(data)
                try? handle.close()
            }
        } else {
            try? data.write(to: logFile)
        }
    }
}

@available(iOS 16.1, *)
final class LiveActivityBridge: NSObject, KavTripActivityHandler {
    static let shared = LiveActivityBridge()
    private var currentActivity: Activity<KavTripAttributes>?

    override init() {
        super.init()
        let auth = ActivityAuthorizationInfo().areActivitiesEnabled
        kavLog("LiveActivityBridge initialized, areActivitiesEnabled=\(auth), currentCount=\(Activity<KavTripAttributes>.activities.count)")
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleAppWillTerminate),
            name: UIApplication.willTerminateNotification,
            object: nil
        )
    }

    @objc private func handleAppWillTerminate() {
        kavLog("app will terminate, ending Live Activity")
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
        let auth = ActivityAuthorizationInfo().areActivitiesEnabled
        guard auth else {
            kavLog("Live Activities disabled in system settings (areActivitiesEnabled=false)")
            return
        }

        let safeProgress = (progress.isNaN || progress.isInfinite) ? 0.0 : max(0.0, min(1.0, progress))
        let state = KavTripAttributes.ContentState(
            title: title,
            subtitle: subtitle,
            chip: chip,
            tintColorHex: tintHex,
            modeName: modeName,
            arrivalTime: arrivalTime,
            progress: safeProgress,
            dueText: dueText
        )

        let totalActivities = Activity<KavTripAttributes>.activities
        kavLog("update: tripId=\(tripId), chip=\(chip), time=\(arrivalTime), title=\(title), activeCount=\(totalActivities.count)")

        // Adopt an already running activity from the system if available
        let active = currentActivity ?? totalActivities.first(where: { $0.attributes.tripId == tripId }) ?? totalActivities.first

        if let existing = active {
            currentActivity = existing
            Task {
                if #available(iOS 16.2, *) {
                    let content = ActivityContent(state: state, staleDate: nil)
                    await existing.update(content)
                } else {
                    await existing.update(using: state)
                }
                kavLog("updated existing Live Activity id=\(existing.id)")
            }
        } else {
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
                kavLog("Live Activity started successfully id=\(activity.id), systemCount=\(Activity<KavTripAttributes>.activities.count)")
            } catch {
                kavLog("ERROR: Live Activity request failed: \(error.localizedDescription)")
            }
        }
    }

    func end() {
        kavLog("end called, dismissing activities")
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
        _ = sema.wait(timeout: .now() + 0.5)
    }
}
