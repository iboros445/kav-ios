//@cmp
// The activity MainActivity extends. On iOS there is one, created by MainViewController: it owns the lifecycle and
// ViewModel store that upstream expects, and keeps the setContent lambda for the Compose host to render.
package androidx.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** androidx.core.app.PictureInPictureModeChangedInfo */
class PictureInPictureModeChangedInfo(val isInPictureInPictureMode: Boolean)

open class ComponentActivity : Activity(), LifecycleOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore: ViewModelStore = ViewModelStore()

    var content: (@Composable () -> Unit)? = null
        internal set

    private var current: Intent = Intent()
    val intent: Intent get() = current
    fun setIntent(i: Intent) { current = i }

    open fun onCreate(savedInstanceState: Bundle?) { registry.currentState = Lifecycle.State.CREATED }
    open fun onNewIntent(intent: Intent) {}
    open fun onUserLeaveHint() {}
    // iOS has no restart of a stopped activity or memory-trim callbacks for Kav to act on.
    open fun onRestart() {}
    open fun onTrimMemory(level: Int) {}

    /** Called by the host as the app comes to the foreground and goes away. */
    fun moveTo(state: Lifecycle.State) { registry.currentState = state }

    @Suppress("UNUSED_PARAMETER")
    fun addOnPictureInPictureModeChangedListener(listener: Consumer<PictureInPictureModeChangedInfo>) {}
}

@Suppress("UNUSED_PARAMETER")
fun ComponentActivity.enableEdgeToEdge() {}
