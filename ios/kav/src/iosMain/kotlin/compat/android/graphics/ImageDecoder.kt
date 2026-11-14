//@cmp
// android.graphics.ImageDecoder (API 28) on the Skia decoder: read the size, let the caller pick a target size, decode.
package android.graphics

import android.content.ContentResolver
import android.net.Uri

class ImageDecoder private constructor() {
    class Size(val width: Int, val height: Int)
    class ImageInfo internal constructor(val size: Size, val mimeType: String = "image/*")
    class Source internal constructor(internal val read: () -> ByteArray)

    fun interface OnHeaderDecodedListener { fun onHeaderDecoded(decoder: ImageDecoder, info: ImageInfo, source: Source) }

    internal var target: Size? = null
    var allocator: Int = ALLOCATOR_DEFAULT
    fun setTargetSize(width: Int, height: Int) { target = Size(width, height) }

    companion object {
        const val ALLOCATOR_DEFAULT = 0
        const val ALLOCATOR_SOFTWARE = 1
        const val ALLOCATOR_HARDWARE = 3

        fun createSource(resolver: ContentResolver, uri: Uri): Source =
            Source { resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw java.io.FileNotFoundException(uri.toString()) }

        fun createSource(file: java.io.File): Source = Source { file.readBytes() }

        fun decodeBitmap(source: Source, listener: OnHeaderDecodedListener? = null): Bitmap {
            val bytes = source.read()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0) throw java.io.IOException("Not an image")
            val d = ImageDecoder()
            listener?.onHeaderDecoded(d, ImageInfo(Size(bounds.outWidth, bounds.outHeight)), source)
            val full = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, null) ?: throw java.io.IOException("Not an image")
            val t = d.target ?: return full
            if (t.width == full.width && t.height == full.height) return full
            val m = Matrix().apply { postScale(t.width.toFloat() / full.width, t.height.toFloat() / full.height) }
            return Bitmap.createBitmap(full, 0, 0, full.width, full.height, m, true)
        }
    }
}
