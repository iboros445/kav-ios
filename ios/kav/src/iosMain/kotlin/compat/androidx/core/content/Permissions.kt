// Android runtime permissions mapped onto iOS authorisation: location -> CoreLocation, POST_NOTIFICATIONS ->
// UserNotifications, CAMERA -> AVFoundation. `request` answers on the main queue with one entry per permission asked.
package androidx.core.content

import android.Manifest
import android.location.LocationHub
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

internal object Notifications {
    var allowed: Boolean = false; private set

    fun refresh(done: () -> Unit = {}) {
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            val s = settings?.authorizationStatus
            allowed = s == UNAuthorizationStatusAuthorized || s == UNAuthorizationStatusProvisional
            dispatch_async(dispatch_get_main_queue()) { done() }
        }
    }

    fun request(done: (Boolean) -> Unit) {
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { granted, _ ->
            allowed = granted
            dispatch_async(dispatch_get_main_queue()) { done(granted) }
        }
    }
}

internal object Camera {
    val allowed: Boolean get() = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) == AVAuthorizationStatusAuthorized

    fun request(done: (Boolean) -> Unit) {
        AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
            dispatch_async(dispatch_get_main_queue()) { done(granted) }
        }
    }
}

object Permissions {
    fun request(permissions: Array<String>, done: (Map<String, Boolean>) -> Unit) {
        val result = LinkedHashMap<String, Boolean>()
        val location = permissions.filter { it == Manifest.permission.ACCESS_FINE_LOCATION || it == Manifest.permission.ACCESS_COARSE_LOCATION }
        val others = permissions.filterNot { it in location }
        var open = (if (location.isNotEmpty()) 1 else 0) + others.size
        if (open == 0) { done(result); return }
        fun finishOne() { if (--open == 0) done(result) }
        if (location.isNotEmpty()) LocationHub.requestAuthorization { fine, coarse ->
            for (p in location) result[p] = if (p == Manifest.permission.ACCESS_FINE_LOCATION) fine else coarse
            finishOne()
        }
        for (p in others) when (p) {
            Manifest.permission.POST_NOTIFICATIONS -> Notifications.request { result[p] = it; finishOne() }
            Manifest.permission.CAMERA -> Camera.request { result[p] = it; finishOne() }
            else -> { result[p] = false; finishOne() }
        }
    }
}
