//@cmp
// AndroidView hosting a compat `android.view.View` that is backed by a UIView: the map, whose touches pass through
// to Compose (which owns its gestures), and web pages, which take their own touches (View.interactive).
package androidx.compose.ui.viewinterop

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import platform.UIKit.UIView

@Composable
fun <T : View> AndroidView(
    factory: (android.content.Context) -> T,
    modifier: Modifier = Modifier,
    onReset: ((T) -> Unit)? = null,
    onRelease: (T) -> Unit = {},
    update: (T) -> Unit = {},
) {
    val ctx = LocalContext.current
    val view = remember { factory(ctx) }
    DisposableEffect(view) { onDispose { onRelease(view) } }
    UIKitView(
        factory = { view.nativeView ?: UIView() },
        modifier = modifier,
        update = { update(view) },
        properties = UIKitInteropProperties(isInteractive = view.interactive, isNativeAccessibilityEnabled = view.interactive),
    )
}
