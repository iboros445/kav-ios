package android.widget

import android.content.Context

/** Toasts are queued here; the root composable (platform/ToastHost) shows them. */
object ToastQueue {
    var listener: ((String, Boolean) -> Unit)? = null
}

class Toast private constructor(private val text: String, private val long: Boolean) {
    fun show() { ToastQueue.listener?.invoke(text, long) ?: println("Toast: $text") }

    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1
        fun makeText(@Suppress("UNUSED_PARAMETER") ctx: Context, text: CharSequence, duration: Int): Toast = Toast(text.toString(), duration == LENGTH_LONG)
    }
}
