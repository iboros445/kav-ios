//@cmp
// androidx.core's Drawable.toBitmap: a blank bitmap of the size asked (no Drawable is ever drawn on iOS).
package androidx.core.graphics.drawable

import android.graphics.Bitmap
import android.graphics.drawable.Drawable

@Suppress("UNUSED_PARAMETER")
fun Drawable.toBitmap(width: Int = intrinsicWidth, height: Int = intrinsicHeight, config: Bitmap.Config? = null): Bitmap =
    Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
