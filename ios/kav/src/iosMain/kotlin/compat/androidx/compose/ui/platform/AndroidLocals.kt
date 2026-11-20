//@cmp
// LocalContext / LocalView: Android-only CompositionLocals that upstream reads everywhere. The root provides the
// activity as the context; the fallback is the application context.
package androidx.compose.ui.platform

import android.content.Context
import android.content.KavApplicationContext
import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf

val LocalContext = staticCompositionLocalOf<Context> { KavApplicationContext }
val LocalView = staticCompositionLocalOf { View() }
