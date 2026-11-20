package android.provider

object Settings {
    // Android's "remove animations" switch: on iOS, Reduce Motion.
    object Global {
        const val ANIMATOR_DURATION_SCALE = "animator_duration_scale"
        const val TRANSITION_ANIMATION_SCALE = "transition_animation_scale"
        @Suppress("UNUSED_PARAMETER")
        fun getFloat(cr: android.content.ContentResolver?, name: String, def: Float): Float =
            if (platform.UIKit.UIAccessibilityIsReduceMotionEnabled()) 0f else def
    }
    const val ACTION_APPLICATION_DETAILS_SETTINGS = "android.settings.APPLICATION_DETAILS_SETTINGS"
    const val ACTION_MANAGE_UNKNOWN_APP_SOURCES = "android.settings.MANAGE_UNKNOWN_APP_SOURCES"
}
