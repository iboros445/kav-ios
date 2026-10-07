package android.content

import android.net.Uri

class ActivityNotFoundException(message: String? = null) : RuntimeException(message)

/** Data only. `Context.startActivity` hands it to the iOS side (platform/IntentRouter). */
class Intent() {
    var action: String? = null; private set
    var data: Uri? = null; private set
    var type: String? = null; private set
    var `package`: String? = null; private set
    var flags: Int = 0; private set
    val extras = HashMap<String, Any?>()
    val dataString: String? get() = data?.toString()
    var chooserTitle: String? = null; private set
    var target: Intent? = null; private set

    constructor(action: String) : this() { this.action = action }
    constructor(action: String, uri: Uri?) : this() { this.action = action; this.data = uri }
    constructor(@Suppress("UNUSED_PARAMETER") ctx: Context, @Suppress("UNUSED_PARAMETER") cls: kotlin.reflect.KClass<*>) : this()

    fun setAction(a: String): Intent { action = a; return this }
    fun setData(uri: Uri?): Intent { data = uri; return this }
    fun setType(t: String?): Intent { type = t; return this }
    fun setDataAndType(uri: Uri?, t: String?): Intent { data = uri; type = t; return this }
    fun setPackage(p: String?): Intent { `package` = p; return this }
    fun addFlags(f: Int): Intent { flags = flags or f; return this }
    fun putExtra(k: String, v: String?): Intent { extras[k] = v; return this }
    fun putExtra(k: String, v: Boolean): Intent { extras[k] = v; return this }
    fun putExtra(k: String, v: Int): Intent { extras[k] = v; return this }
    fun getStringExtra(k: String): String? = extras[k] as? String

    companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val ACTION_SEND = "android.intent.action.SEND"
        const val ACTION_MAIN = "android.intent.action.MAIN"
        const val EXTRA_TEXT = "android.intent.extra.TEXT"
        const val EXTRA_SUBJECT = "android.intent.extra.SUBJECT"
        const val FLAG_ACTIVITY_NEW_TASK = 0x10000000
        const val FLAG_GRANT_READ_URI_PERMISSION = 0x1
        const val FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY = 0x00100000

        fun createChooser(target: Intent, title: CharSequence?): Intent =
            Intent().also { it.target = target; it.chooserTitle = title?.toString(); it.action = "chooser" }
    }
}
