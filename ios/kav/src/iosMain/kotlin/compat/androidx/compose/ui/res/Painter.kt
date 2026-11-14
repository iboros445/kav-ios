//@cmp
// painterResource for the three drawables upstream names. The adaptive-icon pair (background, foreground) is drawn from
// the bundled app icon: the background is solid, the foreground is the icon inset so Update.kt's 1.5x zoom frames it.
package androidx.compose.ui.res

import android.content.KavApplicationContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.jetbrains.skia.Image
import uk.noammm.kav.R

@Composable
fun painterResource(id: Int): Painter = remember(id) {
    when (id) {
        R.drawable.ic_launcher_background -> ColorPainter(Color(0xFF0E1013))
        R.drawable.ic_launcher_foreground -> icon()?.let { InsetPainter(it) } ?: ColorPainter(Color.Transparent)
        else -> ColorPainter(Color.Transparent)
    }
}

private fun icon(): ImageBitmap? = runCatching {
    Image.makeFromEncoded(KavApplicationContext.assets.open("icon.png").use { it.readBytes() }).toComposeImageBitmap()
}.getOrNull()

private class InsetPainter(private val image: ImageBitmap) : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        val k = 1f / 1.5f
        val w = (size.width * k).toInt(); val h = (size.height * k).toInt()
        drawImage(image, dstOffset = IntOffset(((size.width - w) / 2).toInt(), ((size.height - h) / 2).toInt()), dstSize = IntSize(w, h))
    }
}
