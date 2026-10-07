package android.view

open class View {
    /** The UIKit view behind this one, for hosting inside Compose (see AndroidView). */
    open val nativeView: platform.UIKit.UIView? get() = null
}
