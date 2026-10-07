//@cmp
// android.graphics bitmaps on Skia (which Compose Multiplatform ships). Only what upstream uses: decode with sampling,
// JPEG/PNG encode, a drawing canvas for the map's arrow marker, and the ImageBitmap bridges.
package android.graphics

import java.io.File
import java.io.OutputStream
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeJoin
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Bitmap as SkBitmap
import org.jetbrains.skia.Canvas as SkCanvas
import org.jetbrains.skia.Image as SkImage
import org.jetbrains.skia.Paint as SkPaint
import org.jetbrains.skia.Path as SkPath

class Bitmap internal constructor(internal val skia: SkBitmap) {
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
        fun createBitmap(width: Int, height: Int, config: Config): Bitmap = Bitmap(SkBitmap().also { it.allocN32Pixels(width, height) })
    }
}

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

    fun decodeFile(path: String, opts: Options?): Bitmap? {
        val bytes = runCatching { File(path).readBytes() }.getOrNull() ?: return null
        return decodeByteArray(bytes, 0, bytes.size, opts)
    }
}

class Path {
    internal val skia = SkPath()
    fun moveTo(x: Float, y: Float) { skia.moveTo(x, y) }
    fun lineTo(x: Float, y: Float) { skia.lineTo(x, y) }
    fun close() { skia.closePath() }
}

class Paint(flags: Int = 0) {
    enum class Style { FILL, STROKE, FILL_AND_STROKE }
    enum class Join { MITER, ROUND, BEVEL }

    internal val skia = SkPaint().also { it.isAntiAlias = flags and ANTI_ALIAS_FLAG != 0 }

    var style: Style = Style.FILL
        set(v) { field = v; skia.mode = if (v == Style.STROKE) PaintMode.STROKE else PaintMode.FILL }
    var strokeWidth: Float = 0f
        set(v) { field = v; skia.strokeWidth = v }
    var strokeJoin: Join = Join.MITER
        set(v) { field = v; skia.strokeJoin = when (v) { Join.ROUND -> PaintStrokeJoin.ROUND; Join.BEVEL -> PaintStrokeJoin.BEVEL; Join.MITER -> PaintStrokeJoin.MITER } }
    var color: Int = 0
        set(v) { field = v; skia.color = v }

    companion object { const val ANTI_ALIAS_FLAG = 1 }
}

class Canvas(bitmap: Bitmap) {
    private val skia = SkCanvas(bitmap.skia)
    fun drawPath(path: Path, paint: Paint) { skia.drawPath(path.skia, paint.skia) }
}
