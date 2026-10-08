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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
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
            // TEMPORARY black-screen bisect: one launch walks through three stages, 4 s each, and says which is on
            // screen. 1 = plain Compose only. 2 = plus IosChrome (window recolouring). 3 = upstream's UI.
            var stage by remember { mutableStateOf(1) }
            var size by remember { mutableStateOf(IntSize.Zero) }
            var frames by remember { mutableStateOf(0) }
            // recompositions: counts real recompositions of this scope (it reads `stage` and `frames`). tick: written
            // every frame and read only in the draw phase below, so the bar moves without any recomposition.
            val recompositions = remember { intArrayOf(0) }
            SideEffect { recompositions[0]++ }
            val tick = remember { mutableStateOf(0L) }
            LaunchedEffect(Unit) {
                for (st in 1..6) {
                    stage = st
                    Log.i("Kav", "bisect: stage $st (${STAGES[st]}) frames=$frames recompositions=${recompositions[0]}")
                    delay(3000)
                }
                Log.i("Kav", "bisect: done frames=$frames recompositions=${recompositions[0]}")
            }
            LaunchedEffect(Unit) {
                while (true) {
                    withFrameNanos { tick.value = it }
                    frames++; if (frames == 1) Log.i("Kav", "compose: first frame")
                }
            }
            // Build 15's recording: plain Compose rendered and updated until IosChrome ran, then the screen went black.
            // Each part of IosChrome is now applied on its own; the root view background (the suspect) comes last,
            // after upstream's UI has had its turn.
            SideEffect { IosChrome.apply(light = K.light, background = K.bg, parts = stage - 1) }
            Box(Modifier.fillMaxSize().onSizeChanged { size = it; Log.i("Kav", "compose: size ${it.width}x${it.height}") }) {
                if (stage < 5) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF0B3D91)))
                } else {
                    activity.content?.invoke()
                    BackEdge()
                    ToastHost()
                }
                // Moves left to right once a second if frames reach the screen, even with recomposition stuck.
                Box(Modifier.align(Alignment.BottomStart).padding(bottom = 140.dp).fillMaxWidth().height(24.dp).drawBehind {
                    val f = ((tick.value / 1_000_000L) % 1000L) / 1000f
                    val w = this.size.width; val h = this.size.height
                    drawRect(Color(0xFF30D158), topLeft = Offset(w * f, 0f), size = Size(w / 8, h))
                })
                Text(
                    "STAGE $stage  ${STAGES[stage]}\n" +
                        "ui ${size.width}x${size.height} frames=$frames",
                    fontSize = 22.sp, color = Color(0xFFFFD60A),
                    modifier = Modifier.align(Alignment.Center).background(Color(0xCC000000)).padding(12.dp),
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

private val STAGES = listOf(
    "", "plain compose", "+ dark/light style", "+ window colour", "+ status bar", "upstream UI", "+ root view colour",
)

internal object IosChrome {
    /** parts (TEMPORARY bisect): 1 style, 2 + window background, 3 + status bar, 5 + root view background. */
    fun apply(light: Boolean, background: Color, parts: Int = 5) {
        if (parts < 1) return
        val colour = UIColor(red = background.red.toDouble(), green = background.green.toDouble(), blue = background.blue.toDouble(), alpha = 1.0)
        // Overriding on the window reaches keyboards, sheets and pickers.
        val style = if (light) UIUserInterfaceStyle.UIUserInterfaceStyleLight else UIUserInterfaceStyle.UIUserInterfaceStyleDark
        for (scene in UIApplication.sharedApplication.connectedScenes) {
            (scene as? UIWindowScene)?.windows?.forEach { w ->
                (w as? UIWindow)?.let {
                    it.overrideUserInterfaceStyle = style
                    if (parts >= 2) it.backgroundColor = colour
                    if (parts >= 5) it.rootViewController?.view?.backgroundColor = colour
                }
            }
        }
        if (parts >= 3) UIApplication.sharedApplication.setStatusBarStyle(if (light) UIStatusBarStyleDarkContent else UIStatusBarStyleLightContent, false)
    }
}
