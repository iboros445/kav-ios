// androidx.core.view's View extensions upstream calls.
package androidx.core.view

import android.view.View

// The next frame: on iOS, the next turn of the main queue.
inline fun View.doOnPreDraw(crossinline action: (View) -> Unit) {
    val v = this
    post { action(v) }
}
