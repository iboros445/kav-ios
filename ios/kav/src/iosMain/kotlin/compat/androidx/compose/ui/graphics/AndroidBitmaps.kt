//@cmp
package androidx.compose.ui.graphics

import android.graphics.Bitmap

fun ImageBitmap.asAndroidBitmap(): Bitmap = Bitmap(this)
fun Bitmap.asImageBitmap(): ImageBitmap = image
