// What Android code reaches for on a window: rotation, secure-flag, system-bar colours. iOS has one window,
// always portrait, so these are inert holders that keep calls compiling and answer sensibly.
package android.view

class Surface {
    companion object {
        const val ROTATION_0 = 0
        const val ROTATION_90 = 1
        const val ROTATION_180 = 2
        const val ROTATION_270 = 3
    }
}

class Display { val rotation: Int get() = Surface.ROTATION_0 }

class WindowManager {
    val defaultDisplay: Display get() = Display()

    class LayoutParams {
        /** 0..1, or -1 for "the user's own setting". Applied to the screen when set on the window. */
        var screenBrightness: Float = BRIGHTNESS_OVERRIDE_NONE

        companion object {
            const val BRIGHTNESS_OVERRIDE_NONE = -1f
            const val FLAG_SECURE = 0x2000
            const val FLAG_KEEP_SCREEN_ON = 0x80
        }
    }
}

class Window {
    var navigationBarColor: Int = 0
    var statusBarColor: Int = 0
    private var flags = 0
    private var saved: Double? = null
    private var params = WindowManager.LayoutParams()

    /** Setting screenBrightness raises the real screen brightness; -1 puts the user's level back. */
    var attributes: WindowManager.LayoutParams
        get() = WindowManager.LayoutParams().also { it.screenBrightness = params.screenBrightness }
        set(v) {
            params = WindowManager.LayoutParams().also { it.screenBrightness = v.screenBrightness }
            val screen = platform.UIKit.UIScreen.mainScreen
            if (v.screenBrightness >= 0f) {
                if (saved == null) saved = screen.brightness
                screen.brightness = v.screenBrightness.toDouble()
            } else saved?.let { screen.brightness = it; saved = null }
        }
    fun addFlags(f: Int) { flags = flags or f }
    fun clearFlags(f: Int) { flags = flags and f.inv() }
    fun hasFlag(f: Int): Boolean = flags and f != 0
}
