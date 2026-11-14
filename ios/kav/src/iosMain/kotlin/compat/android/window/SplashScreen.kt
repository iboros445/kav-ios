// android.window.SplashScreen (Activity.splashScreen). iOS shows the app's launch storyboard instead and never hands
// the screen over, so the listener is kept and never called.
package android.window

import android.view.View

class SplashScreenView internal constructor() : View() {
    val iconView: View? get() = null
    fun remove() {}
}

class SplashScreen internal constructor() {
    fun interface OnExitAnimationListener { fun onSplashScreenExit(view: SplashScreenView) }
    @Suppress("UNUSED_PARAMETER")
    fun setOnExitAnimationListener(listener: OnExitAnimationListener) {}
    fun clearOnExitAnimationListener() {}
}
