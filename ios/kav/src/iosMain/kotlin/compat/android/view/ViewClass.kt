package android.view

open class View {
    open val context: android.content.Context get() = android.content.KavApplicationContext
    // Compose hosts every view on iOS: none sits in an Android view tree.
    open val parent: ViewParent? get() = null
    open var alpha: Float = 1f
    var translationY: Float = 0f
    var scaleX: Float = 1f
    var scaleY: Float = 1f
    fun animate(): ViewPropertyAnimator = ViewPropertyAnimator(this)

    fun interface OnLayoutChangeListener {
        fun onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int)
    }
    // Compose lays every view out on iOS and moves the map itself when it's resized, so none is told.
    @Suppress("UNUSED_PARAMETER") fun addOnLayoutChangeListener(l: OnLayoutChangeListener) {}
    @Suppress("UNUSED_PARAMETER") fun removeOnLayoutChangeListener(l: OnLayoutChangeListener) {}

    companion object {
        val ALPHA = Property<View>("alpha")
        val TRANSLATION_Y = Property<View>("translationY")
        val SCALE_X = Property<View>("scaleX")
        val SCALE_Y = Property<View>("scaleY")
    }

    /** The UIKit view behind this one, for hosting inside Compose (see AndroidView). */
    open val nativeView: platform.UIKit.UIView? get() = null

    /** Whether touches go to the UIKit view (a web page) rather than to Compose (the map, which Compose drives). */
    open val interactive: Boolean get() = false

    fun post(action: java.lang.Runnable): Boolean {
        platform.darwin.dispatch_async(platform.darwin.dispatch_get_main_queue()) { action.run() }
        return true
    }

    fun postDelayed(action: java.lang.Runnable, delayMs: Long): Boolean {
        platform.darwin.dispatch_after(
            platform.darwin.dispatch_time(platform.darwin.DISPATCH_TIME_NOW, delayMs * 1_000_000),
            platform.darwin.dispatch_get_main_queue(),
        ) { action.run() }
        return true
    }
}
