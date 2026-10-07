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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
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
import platform.UIKit.UIWindowScene
import platform.UIKit.setStatusBarStyle
import uk.noammm.kav.MainActivity
import uk.noammm.kav.ui.K
import uk.noammm.kav.ui.T

// The entry point Swift calls (KavApp.swift). It runs upstream's MainActivity as-is: onCreate sets up preferences,
// map and payments, and its setContent lambda is what the Compose host renders.
//
// Breadcrumbs go to Documents/kav.log (Files app -> Kav) because a phone without a Mac has no other way to see why a
// screen is empty. The little line at the top left reports whether Compose has a size and is producing frames.
fun MainViewController(): UIViewController {
    Log.i("Kav", "MainViewController: start")
    setUnhandledExceptionHook { e -> Log.e("Kav", "unhandled exception", e) }
    val activity = MainActivity()
    activity.onCreate(null)
    Log.i("Kav", "MainViewController: onCreate done, content=${activity.content != null}")
    followAppState(activity)
    return ComposeUIViewController {
        CompositionLocalProvider(LocalContext provides activity, LocalViewModelStoreOwner provides activity) {
            // The app has its own look (OLED, dark, light) and ignores the system's. Everything iOS draws itself
            // (window background, keyboard, share sheet, pickers, status bar) is told to follow it.
            SideEffect { IosChrome.apply(light = K.light, background = K.bg) }
            var size by remember { mutableStateOf(IntSize.Zero) }
            var frames by remember { mutableStateOf(0) }
            LaunchedEffect(Unit) {
                Log.i("Kav", "compose: first composition")
                while (frames < 120) { withFrameNanos { }; frames++; if (frames == 1) Log.i("Kav", "compose: first frame") }
            }
            Box(Modifier.fillMaxSize().onSizeChanged { size = it; Log.i("Kav", "compose: size ${it.width}x${it.height}") }) {
                activity.content?.invoke()
                BackEdge()
                ToastHost()
                Text(
                    "ui ${size.width}x${size.height} frames=$frames", fontSize = 9.sp, color = Color(0xFF8A8A8A),
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 52.dp),
                )
            }
        }
    }
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
    fun apply(light: Boolean, background: Color) {
        val colour = UIColor(red = background.red.toDouble(), green = background.green.toDouble(), blue = background.blue.toDouble(), alpha = 1.0)
        // Overriding on the window reaches keyboards, sheets and pickers.
        val style = if (light) UIUserInterfaceStyle.UIUserInterfaceStyleLight else UIUserInterfaceStyle.UIUserInterfaceStyleDark
        for (scene in UIApplication.sharedApplication.connectedScenes) {
            (scene as? UIWindowScene)?.windows?.forEach { w ->
                (w as? UIWindow)?.let {
                    it.overrideUserInterfaceStyle = style
                    it.backgroundColor = colour
                    it.rootViewController?.view?.backgroundColor = colour
                }
            }
        }
        UIApplication.sharedApplication.setStatusBarStyle(if (light) UIStatusBarStyleDarkContent else UIStatusBarStyleLightContent, false)
    }
}
