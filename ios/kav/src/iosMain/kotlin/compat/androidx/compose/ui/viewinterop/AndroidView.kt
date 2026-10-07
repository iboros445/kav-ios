//@cmp
// AndroidView hosting a compat `android.view.View` that is backed by a UIView (today only MapView). Touches pass
// through to Compose, which owns the map's gestures.
package androidx.compose.ui.viewinterop

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import platform.UIKit.UIView

@Composable
fun <T : View> AndroidView(factory: (android.content.Context) -> T, modifier: Modifier = Modifier, update: (T) -> Unit = {}) {
    val ctx = LocalContext.current
    val view = remember { factory(ctx) }
    UIKitView(
        factory = { view.nativeView ?: UIView() },
        modifier = modifier,
        update = { update(view) },
        properties = UIKitInteropProperties(isInteractive = false, isNativeAccessibilityEnabled = false),
    )
}
