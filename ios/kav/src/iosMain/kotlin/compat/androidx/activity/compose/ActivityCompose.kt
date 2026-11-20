//@cmp
package androidx.activity.compose

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@Suppress("UNUSED_PARAMETER")
fun ComponentActivity.setContent(parent: CompositionContext? = null, content: @Composable () -> Unit) {
    this.content = content
}

/** The stack of live BackHandlers; the newest enabled one gets the back gesture (an edge swipe, see platform/BackEdge). */
object BackStack {
    internal class Entry { var enabled = true; var action: () -> Unit = {} }

    private val entries = ArrayList<Entry>()

    internal fun add(e: Entry) { entries.add(e) }
    internal fun remove(e: Entry) { entries.remove(e) }

    /** True if something handled it. */
    fun dispatch(): Boolean {
        val e = entries.lastOrNull { it.enabled } ?: return false
        e.action()
        return true
    }

    fun canGoBack(): Boolean = entries.any { it.enabled }
}

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val latest = rememberUpdatedState(onBack)
    val entry = remember { BackStack.Entry() }
    entry.enabled = enabled
    entry.action = { latest.value() }
    DisposableEffect(entry) {
        BackStack.add(entry)
        onDispose { BackStack.remove(entry) }
    }
}

class ManagedActivityResultLauncher<I, O> internal constructor(
    private val contract: ActivityResultContract<I, O>,
    private val onResult: State<(O) -> Unit>,
) {
    fun launch(input: I) = contract.run(input) { onResult.value(it) }
}

@Composable
fun <I, O> rememberLauncherForActivityResult(
    contract: ActivityResultContract<I, O>,
    onResult: (O) -> Unit,
): ManagedActivityResultLauncher<I, O> {
    val latest = rememberUpdatedState(onResult)
    return remember(contract) { ManagedActivityResultLauncher(contract, latest) }
}
