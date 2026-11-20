// android.graphics.drawable.Drawable: only as the type an app's icon comes in (PackageManager.resolveActivity finds
// none on iOS, so none is ever made).
package android.graphics.drawable

abstract class Drawable {
    open val intrinsicWidth: Int get() = -1
    open val intrinsicHeight: Int get() = -1
}
