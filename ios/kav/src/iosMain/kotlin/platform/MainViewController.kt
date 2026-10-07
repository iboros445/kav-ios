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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import android.widget.ToastQueue
import kotlinx.coroutines.delay
import platform.UIKit.UIViewController
import uk.noammm.kav.MainActivity
import uk.noammm.kav.ui.K
import uk.noammm.kav.ui.T

// The entry point Swift calls (KavApp.swift). It runs upstream's MainActivity as-is: onCreate sets up preferences,
// map and payments, and its setContent lambda is what the Compose host renders.
fun MainViewController(): UIViewController {
    val activity = MainActivity()
    activity.onCreate(null)
    return ComposeUIViewController {
        CompositionLocalProvider(LocalContext provides activity, LocalViewModelStoreOwner provides activity) {
            Box(Modifier.fillMaxSize()) {
                activity.content?.invoke()
                BackEdge()
                ToastHost()
            }
        }
    }
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
