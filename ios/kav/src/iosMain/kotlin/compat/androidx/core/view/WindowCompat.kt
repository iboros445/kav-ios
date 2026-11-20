package androidx.core.view

import android.view.View
import android.view.Window
import platform.UIKit.UIApplication
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.setStatusBarStyle

/** Light status-bar *appearance* means dark icons, i.e. a light theme. Needs UIViewControllerBasedStatusBarAppearance=false. */
class WindowInsetsControllerCompat internal constructor() {
    var isAppearanceLightStatusBars: Boolean = false
        set(value) {
            field = value
            UIApplication.sharedApplication.setStatusBarStyle(if (value) UIStatusBarStyleDarkContent else UIStatusBarStyleLightContent, false)
        }

    /** There is no navigation bar on an iPhone; the home indicator follows the content. */
    var isAppearanceLightNavigationBars: Boolean = false
}

object WindowCompat {
    private val controller = WindowInsetsControllerCompat()
    fun getInsetsController(window: Window, view: View): WindowInsetsControllerCompat = controller
    fun setDecorFitsSystemWindows(window: Window, decorFits: Boolean) {}
}
