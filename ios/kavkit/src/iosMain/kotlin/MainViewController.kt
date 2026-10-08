//@cmp
package uk.noammm.kav.ios

import androidx.activity.compose.BackStack
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import android.util.Log
import android.widget.ToastQueue
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIColor
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.setStatusBarStyle
import uk.noammm.kav.MainActivity
import uk.noammm.kav.ui.K
import uk.noammm.kav.ui.T

// The entry point Swift calls (KavApp.swift). It runs upstream's MainActivity as-is: onCreate sets up preferences,
// map and payments, and its setContent lambda is what the Compose host renders.
//
// Startup breadcrumbs go to Documents/kav.log (Files app -> Kav): a phone without a Mac has no other way to see
// how far a launch got.
fun MainViewController(): UIViewController {
    Log.i("Kav", "MainViewController: start")
    setUnhandledExceptionHook { e -> Log.e("Kav", "unhandled exception", e) }
    val activity = MainActivity()
    activity.onCreate(null)
    Log.i("Kav", "MainViewController: onCreate done, content=${activity.content != null}")
    followAppState(activity)
    lateinit var controller: UIViewController
    controller = ComposeUIViewController {
        CompositionLocalProvider(LocalContext provides activity, LocalViewModelStoreOwner provides activity) {
            // The app has its own look (OLED, dark, light) and ignores the system's. Kav's own window (its
            // background, keyboard, share sheet, pickers) and the status bar are told to follow it. The window is
            // only known once the view is on screen, so the first frame applies it again.
            val light = K.light
            val bg = K.bg
            SideEffect { IosChrome.apply(controller.view.window, light, bg) }
            LaunchedEffect(Unit) {
                withFrameNanos { }
                IosChrome.apply(controller.view.window, light, bg)
                Log.i("Kav", "compose: first frame, window=${controller.view.window != null}")
            }
            Box(Modifier.fillMaxSize()) {
                activity.content?.invoke()
                BackEdge()
                ToastHost()
            }
        }
    }
    return controller
}

/** The activity's lifecycle follows the app: upstream collects live data only while it is STARTED or RESUMED. */
private fun followAppState(activity: MainActivity) {
    val center = NSNotificationCenter.defaultCenter
    val queue = NSOperationQueue.mainQueue
    center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, queue) { activity.moveTo(Lifecycle.State.RESUMED) }
    center.addObserverForName(UIApplicationWillResignActiveNotification, null, queue) { activity.moveTo(Lifecycle.State.STARTED) }
    center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, queue) { activity.moveTo(Lifecycle.State.CREATED) }
    // The first didBecomeActive may already have fired by the time the view controller is made.
    activity.moveTo(Lifecycle.State.RESUMED)
}

/** iOS has no back button: a swipe in from the leading edge answers every BackHandler, newest first. */
@Composable
private fun BackEdge() {
    val rtl = T.rtl
    val trigger = with(LocalDensity.current) { 56.dp.toPx() }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.align(if (rtl) Alignment.CenterEnd else Alignment.CenterStart).fillMaxHeight().width(16.dp)
                .pointerInput(rtl) {
                    var travelled = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { travelled = 0f },
                        onDragEnd = { if (travelled > trigger) BackStack.dispatch() },
                        onHorizontalDrag = { _, d -> travelled += if (rtl) -d else d },
                    )
                },
        )
    }
}

/** android.widget.Toast, drawn as a pill above the tab bar. */
@Composable
private fun ToastHost() {
    var text by remember { mutableStateOf<String?>(null) }
    var serial by remember { mutableStateOf(0) }
    DisposableEffect(Unit) {
        ToastQueue.listener = { t, _ -> text = t; serial++ }
        onDispose { ToastQueue.listener = null }
    }
    LaunchedEffect(serial) {
        if (text != null) { delay(2800); text = null }
    }
    text?.let { t ->
        Box(Modifier.fillMaxSize().padding(bottom = 110.dp, start = 24.dp, end = 24.dp), contentAlignment = Alignment.BottomCenter) {
            Text(
                t, fontSize = 13.sp, color = K.text,
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(K.surface4).padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

internal object IosChrome {
    /**
     * Only Kav's own window. The scene also holds transparent system windows above it (text effects and others);
     * giving those a background covers the whole app with an opaque colour, which is what made Kav a black screen.
     */
    fun apply(window: UIWindow?, light: Boolean, background: Color) {
        val w = window ?: return
        w.overrideUserInterfaceStyle = if (light) UIUserInterfaceStyle.UIUserInterfaceStyleLight else UIUserInterfaceStyle.UIUserInterfaceStyleDark
        val colour = UIColor(red = background.red.toDouble(), green = background.green.toDouble(), blue = background.blue.toDouble(), alpha = 1.0)
        w.backgroundColor = colour
        w.rootViewController?.view?.backgroundColor = colour
        UIApplication.sharedApplication.setStatusBarStyle(if (light) UIStatusBarStyleDarkContent else UIStatusBarStyleLightContent, false)
    }
}
