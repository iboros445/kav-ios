//@cmp
// AndroidView hosting a compat `android.view.View` that is backed by a UIView: the map, whose touches pass through
// to Compose (which owns its gestures), and web pages, which take their own touches (View.interactive).
package androidx.compose.ui.viewinterop

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
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
        modifier = modifier.then(FillOffered),
        update = { update(view) },
        properties = UIKitInteropProperties(isInteractive = view.interactive, isNativeAccessibilityEnabled = view.interactive),
    )
}

// A view hosted here takes all the room it's offered, as an Android View does in AndroidView. Compose's UIKitView
// instead asks Auto Layout for the smallest size that fits along any axis whose size isn't fixed, and a view with no
// size of its own (MLNMapView, WKWebView) answers zero, so the whole view was laid out 0x0: TileMap fixes the map's
// height but leaves its width free, and every map stayed blank. An unbounded axis (inside a scroll) keeps Compose's own
// measuring.
private val FillOffered = Modifier.layout { measurable, c ->
    val w = if (c.hasBoundedWidth) c.maxWidth else null
    val h = if (c.hasBoundedHeight) c.maxHeight else null
    val placeable = measurable.measure(
        Constraints(
            minWidth = w ?: c.minWidth, maxWidth = w ?: c.maxWidth,
            minHeight = h ?: c.minHeight, maxHeight = h ?: c.maxHeight,
        )
    )
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}
