package android.view

open class View {
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
