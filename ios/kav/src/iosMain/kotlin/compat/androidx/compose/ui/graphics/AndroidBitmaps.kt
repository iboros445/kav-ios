//@cmp
package androidx.compose.ui.graphics

import android.graphics.Bitmap

fun ImageBitmap.asAndroidBitmap(): Bitmap = Bitmap(asSkiaBitmap())
fun Bitmap.asImageBitmap(): ImageBitmap = skia.asComposeImageBitmap()
