//@cmp
// android.graphics bitmaps on Skia (which Compose Multiplatform ships). Only what upstream uses: decode with sampling,
// JPEG/PNG encode, a drawing canvas for the map's arrow marker, and the ImageBitmap bridges.
package android.graphics

import java.io.File
import java.io.OutputStream
import org.jetbrains.skia.EncodedImageFormat
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Bitmap as SkBitmap
import org.jetbrains.skia.Canvas as SkCanvas
import org.jetbrains.skia.Image as SkImage

class Bitmap internal constructor(internal val image: androidx.compose.ui.graphics.ImageBitmap) {
    internal constructor(sk: SkBitmap) : this(sk.asComposeImageBitmap())
    internal val skia: SkBitmap get() = image.asSkiaBitmap()

    enum class Config { ARGB_8888, RGB_565, ALPHA_8 }
    enum class CompressFormat { JPEG, PNG, WEBP }

    val width: Int get() = skia.width
    val height: Int get() = skia.height
    val byteCount: Int get() = skia.rowBytes * skia.height
    fun recycle() {}
    val isRecycled: Boolean get() = false

    fun compress(format: CompressFormat, quality: Int, out: OutputStream): Boolean {
        val f = when (format) { CompressFormat.JPEG -> EncodedImageFormat.JPEG; CompressFormat.PNG -> EncodedImageFormat.PNG; CompressFormat.WEBP -> EncodedImageFormat.WEBP }
        val data = SkImage.makeFromBitmap(skia).encodeToData(f, quality) ?: return false
        out.write(data.bytes)
        return true
    }

    companion object {
        fun createBitmap(width: Int, height: Int, config: Config): Bitmap = Bitmap(androidx.compose.ui.graphics.ImageBitmap(width, height))

        /** A region of [src] transformed by [m]: rotations by multiples of 90 degrees and scaling, which is what upstream asks. */
        fun createBitmap(src: Bitmap, x: Int, y: Int, width: Int, height: Int, m: Matrix?, filter: Boolean): Bitmap {
            val turns = Math.floorMod(kotlin.math.round((m?.degrees ?: 0f) / 90f).toInt(), 4)
            val sw = width * (m?.sx ?: 1f); val sh = height * (m?.sy ?: 1f)
            val (w, h) = if (turns % 2 == 0) sw to sh else sh to sw
            val out = SkBitmap().also { it.allocN32Pixels(kotlin.math.abs(w).toInt().coerceAtLeast(1), kotlin.math.abs(h).toInt().coerceAtLeast(1)) }
            val canvas = SkCanvas(out)
            canvas.translate(out.width / 2f, out.height / 2f)
            canvas.rotate(turns * 90f)
            canvas.drawImageRect(SkImage.makeFromBitmap(src.skia), Rect.makeXYWH(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat()),
                Rect.makeXYWH(-sw / 2f, -sh / 2f, sw, sh))
            canvas.close()
            return Bitmap(out)
        }
    }
}

/** android.graphics.Matrix, the part Kav uses: a rotation and a scale for createBitmap. */
class Matrix {
    internal var degrees = 0f
    internal var sx = 1f
    internal var sy = 1f
    fun postRotate(d: Float): Boolean { degrees += d; return true }
    fun preRotate(d: Float): Boolean { degrees += d; return true }
    fun setRotate(d: Float) { degrees = d }
    fun postScale(x: Float, y: Float): Boolean { sx *= x; sy *= y; return true }
    fun reset() { degrees = 0f; sx = 1f; sy = 1f }
}

private object Math { fun floorMod(a: Int, b: Int): Int = ((a % b) + b) % b }

object BitmapFactory {
    class Options {
        var inJustDecodeBounds = false
        var inSampleSize = 1
        var outWidth = 0
        var outHeight = 0
    }

    fun decodeByteArray(data: ByteArray, offset: Int, length: Int, opts: Options?): Bitmap? {
        val image = runCatching { SkImage.makeFromEncoded(if (offset == 0 && length == data.size) data else data.copyOfRange(offset, offset + length)) }.getOrNull() ?: return null
        opts?.let { it.outWidth = image.width; it.outHeight = image.height }
        if (opts?.inJustDecodeBounds == true) return null
        val s = (opts?.inSampleSize ?: 1).coerceAtLeast(1)
        val w = (image.width / s).coerceAtLeast(1)
        val h = (image.height / s).coerceAtLeast(1)
        val bmp = SkBitmap().also { it.allocN32Pixels(w, h) }
        val canvas = SkCanvas(bmp)
        canvas.drawImageRect(image, Rect.makeWH(w.toFloat(), h.toFloat()))
        canvas.close()
        return Bitmap(bmp)
    }

    fun decodeStream(input: java.io.InputStream?, outPadding: Any?, opts: Options?): Bitmap? {
        val bytes = runCatching { input?.readBytes() }.getOrNull() ?: return null
        return decodeByteArray(bytes, 0, bytes.size, opts)
    }

    fun decodeStream(input: java.io.InputStream?): Bitmap? = decodeStream(input, null, null)

    fun decodeFile(path: String, opts: Options?): Bitmap? {
        val bytes = runCatching { File(path).readBytes() }.getOrNull() ?: return null
        return decodeByteArray(bytes, 0, bytes.size, opts)
    }
}

class Path {
    internal val compose = androidx.compose.ui.graphics.Path()
    fun moveTo(x: Float, y: Float) { compose.moveTo(x, y) }
    fun lineTo(x: Float, y: Float) { compose.lineTo(x, y) }
    fun close() { compose.close() }
}

class Paint(flags: Int = 0) {
    enum class Style { FILL, STROKE, FILL_AND_STROKE }
    enum class Join { MITER, ROUND, BEVEL }

    internal val compose = androidx.compose.ui.graphics.Paint().also { it.isAntiAlias = flags and ANTI_ALIAS_FLAG != 0 }

    var style: Style = Style.FILL
        set(v) { field = v; compose.style = if (v == Style.STROKE) androidx.compose.ui.graphics.PaintingStyle.Stroke else androidx.compose.ui.graphics.PaintingStyle.Fill }
    var strokeWidth: Float = 0f
        set(v) { field = v; compose.strokeWidth = v }
    var strokeJoin: Join = Join.MITER
        set(v) { field = v; compose.strokeJoin = when (v) { Join.ROUND -> androidx.compose.ui.graphics.StrokeJoin.Round; Join.BEVEL -> androidx.compose.ui.graphics.StrokeJoin.Bevel; Join.MITER -> androidx.compose.ui.graphics.StrokeJoin.Miter } }
    var color: Int = 0
        set(v) { field = v; compose.color = androidx.compose.ui.graphics.Color(v) }

    companion object { const val ANTI_ALIAS_FLAG = 1 }
}

class Canvas(bitmap: Bitmap) {
    private val compose = androidx.compose.ui.graphics.Canvas(bitmap.image)
    fun drawPath(path: Path, paint: Paint) { compose.drawPath(path.compose, paint.compose) }
}
