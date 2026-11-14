//@cmp
// The Android-only parts of Compose's Dialog that upstream uses. A dialog on iOS draws inside Kav's own window, so
// there's no system window to fit or to reach.
package androidx.compose.ui.window

// DialogProperties with Android's decorFitsSystemWindows. It's a required parameter here so that calls without it
// still go to Compose's own constructor.
@Suppress("UNUSED_PARAMETER")
fun DialogProperties(
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    usePlatformDefaultWidth: Boolean = true,
    decorFitsSystemWindows: Boolean,
): DialogProperties = DialogProperties(
    dismissOnBackPress = dismissOnBackPress,
    dismissOnClickOutside = dismissOnClickOutside,
    usePlatformDefaultWidth = usePlatformDefaultWidth,
)

interface DialogWindowProvider {
    val window: android.view.Window
}
