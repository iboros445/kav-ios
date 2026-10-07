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
        companion object {
            const val FLAG_SECURE = 0x2000
            const val FLAG_KEEP_SCREEN_ON = 0x80
        }
    }
}

class Window {
    var navigationBarColor: Int = 0
    var statusBarColor: Int = 0
    private var flags = 0
    fun addFlags(f: Int) { flags = flags or f }
    fun clearFlags(f: Int) { flags = flags and f.inv() }
    fun hasFlag(f: Int): Boolean = flags and f != 0
}
