// GZIPOutputStream on zlib (deflate with a gzip wrapper: windowBits 15 + 16). It collects the input and compresses
// it on close, which is all upstream needs (a shared trip, a few kilobytes).
package java.util.zip

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.zlib.Z_DEFAULT_COMPRESSION
import platform.zlib.Z_DEFAULT_STRATEGY
import platform.zlib.Z_DEFLATED
import platform.zlib.Z_FINISH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.deflate
import platform.zlib.deflateBound
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2_
import platform.zlib.z_stream
import platform.zlib.ZLIB_VERSION

class GZIPOutputStream(private val out: OutputStream, @Suppress("UNUSED_PARAMETER") size: Int = 512) : OutputStream() {
    private val buf = ByteArrayOutputStream()
    private var closed = false

    override fun write(b: Int) = buf.write(b)
    override fun write(b: ByteArray, off: Int, len: Int) = buf.write(b, off, len)

    fun finish() {
        if (closed) return
        closed = true
        out.write(compress(buf.toByteArray()))
    }

    override fun close() { finish(); out.close() }

    private fun compress(input: ByteArray): ByteArray = memScoped {
        val zs = alloc<z_stream>()
        if (deflateInit2_(zs.ptr, Z_DEFAULT_COMPRESSION, Z_DEFLATED, 15 + 16, 8, Z_DEFAULT_STRATEGY, ZLIB_VERSION, sizeOf<z_stream>().convert()) != Z_OK)
            throw IOException("deflateInit2 failed")
        try {
            val cap = deflateBound(zs.ptr, input.size.convert()).toInt() + 32
            val dst = ByteArray(cap)
            val src = if (input.isEmpty()) ByteArray(1) else input
            src.usePinned { s -> dst.usePinned { d ->
                zs.next_in = s.addressOf(0).reinterpret()
                zs.avail_in = input.size.convert()
                zs.next_out = d.addressOf(0).reinterpret()
                zs.avail_out = cap.convert()
                if (deflate(zs.ptr, Z_FINISH) != Z_STREAM_END) throw IOException("deflate failed")
            } }
            dst.copyOf(zs.total_out.toInt())
        } finally { deflateEnd(zs.ptr) }
    }
}
