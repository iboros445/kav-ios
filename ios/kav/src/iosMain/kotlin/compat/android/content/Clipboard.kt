// android.content.ClipboardManager on UIPasteboard: Kav only copies plain text (a trip's link).
package android.content

class ClipData private constructor(val label: CharSequence?, internal val text: CharSequence?) {
    companion object {
        fun newPlainText(label: CharSequence?, text: CharSequence?): ClipData = ClipData(label, text)
    }
}

class ClipboardManager internal constructor() {
    fun setPrimaryClip(clip: ClipData) { platform.UIKit.UIPasteboard.generalPasteboard.string = clip.text?.toString() }
    val hasPrimaryClip: Boolean get() = platform.UIKit.UIPasteboard.generalPasteboard.hasStrings
}
