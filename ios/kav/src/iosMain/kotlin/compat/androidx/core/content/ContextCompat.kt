package androidx.core.content

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationHub

object ContextCompat {
    fun checkSelfPermission(ctx: Context, permission: String): Int {
        val granted = when (permission) {
            Manifest.permission.ACCESS_FINE_LOCATION -> LocationHub.fine
            Manifest.permission.ACCESS_COARSE_LOCATION -> LocationHub.coarse
            Manifest.permission.POST_NOTIFICATIONS -> Notifications.allowed
            Manifest.permission.CAMERA -> Camera.allowed
            else -> false
        }
        return if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
    }
}
