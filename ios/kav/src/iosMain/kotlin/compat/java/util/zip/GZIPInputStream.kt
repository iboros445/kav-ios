package java.util.zip

import java.io.IOException
import java.io.InputStream

class ZipException(message: String? = null) : IOException(message)

/**
 * GZIP reader in pure Kotlin (RFC 1951 inflate + RFC 1952 header). Decodes the whole member up front,
 * which is what every upstream caller does with it anyway (`GZIPInputStream(bytes.inputStream()).readBytes()`).
 *
 * Tolerant on purpose: input that does not start with the gzip magic is passed through unchanged. On iOS,
 * CFNetwork may already have decoded a `Content-Encoding: gzip` body while leaving the header in place; the
 * upstream code would then gunzip plain bytes and fail.
 */
class GZIPInputStream(input: InputStream, @Suppress("UNUSED_PARAMETER") size: Int = 512) : InputStream() {
    private val data: ByteArray = decode(input.readBytes())
    private var pos = 0

    override fun read(): Int = if (pos < data.size) data[pos++].toInt() and 0xFF else -1

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (pos >= data.size) return -1
        val n = minOf(len, data.size - pos)
        data.copyInto(b, off, pos, pos + n)
        pos += n
        return n
    }

    override fun available(): Int = data.size - pos

    private companion object {
        fun decode(src: ByteArray): ByteArray {
            if (src.size < 2 || src[0] != 0x1F.toByte() || src[1] != 0x8B.toByte()) return src
            val out = Inflater.OutBuf(maxOf(src.size * 4, 1 shl 16))
            var p = 0
            while (p + 18 <= src.size && src[p] == 0x1F.toByte() && src[p + 1] == 0x8B.toByte()) {   // concatenated members
                if (src[p + 2].toInt() != 8) throw ZipException("Unsupported compression method")
                val flg = src[p + 3].toInt()
                p += 10
                if (flg and 4 != 0) { val xlen = (src[p].toInt() and 0xFF) or ((src[p + 1].toInt() and 0xFF) shl 8); p += 2 + xlen }
                if (flg and 8 != 0) { while (src[p] != 0.toByte()) p++; p++ }
                if (flg and 16 != 0) { while (src[p] != 0.toByte()) p++; p++ }
                if (flg and 2 != 0) p += 2
                p = Inflater(src, p, out).inflate()
                p += 8  // CRC32 + ISIZE
            }
            return out.toByteArray()
        }
    }
}

/** RFC 1951 inflate: stored, fixed-Huffman and dynamic-Huffman blocks. */
internal class Inflater(private val src: ByteArray, private var pos: Int, private val out: OutBuf) {
    class OutBuf(cap: Int) {
        var a = ByteArray(cap)
        var n = 0
        fun put(b: Int) { if (n == a.size) a = a.copyOf(a.size * 2); a[n++] = b.toByte() }
        fun ensure(extra: Int) { if (n + extra > a.size) a = a.copyOf(maxOf(a.size * 2, n + extra)) }
        fun toByteArray(): ByteArray = a.copyOf(n)
    }

    private var bitBuf = 0
    private var bitCnt = 0

    private fun bits(need: Int): Int {
        while (bitCnt < need) {
            if (pos >= src.size) throw ZipException("Unexpected end of ZLIB input stream")
            bitBuf = bitBuf or ((src[pos++].toInt() and 0xFF) shl bitCnt)
            bitCnt += 8
        }
        val v = bitBuf and ((1 shl need) - 1)
        bitBuf = bitBuf ushr need
        bitCnt -= need
        return v
    }

    private class Huff(lengths: IntArray, n: Int) {
        val count = IntArray(16)
        val symbol = IntArray(n)

        init {
            for (i in 0 until n) count[lengths[i]]++
            count[0] = 0
            val offs = IntArray(16)
            for (i in 1 until 15) offs[i + 1] = offs[i] + count[i]
            for (i in 0 until n) if (lengths[i] != 0) symbol[offs[lengths[i]]++] = i
        }
    }

    private fun decode(h: Huff): Int {
        var code = 0; var first = 0; var index = 0
        for (len in 1..15) {
            code = code or bits(1)
            val c = h.count[len]
            if (code - c < first) return h.symbol[index + (code - first)]
            index += c; first += c
            first = first shl 1; code = code shl 1
        }
        throw ZipException("Invalid Huffman code")
    }

    /** Inflates one deflate stream starting at `pos`; returns the byte position just after it. */
    fun inflate(): Int {
        do {
            val last = bits(1)
            when (bits(2)) {
                0 -> stored()
                1 -> codes(FIXED_LIT, FIXED_DIST)
                2 -> dynamic()
                else -> throw ZipException("Invalid block type")
            }
        } while (last == 0)
        return pos - bitCnt / 8   // return unused whole bytes still sitting in the bit buffer
    }

    private fun stored() {
        bitBuf = 0; bitCnt = 0
        if (pos + 4 > src.size) throw ZipException("Unexpected end of ZLIB input stream")
        val len = (src[pos].toInt() and 0xFF) or ((src[pos + 1].toInt() and 0xFF) shl 8)
        pos += 4
        if (pos + len > src.size) throw ZipException("Unexpected end of ZLIB input stream")
        out.ensure(len)
        src.copyInto(out.a, out.n, pos, pos + len)
        out.n += len; pos += len
    }

    private fun dynamic() {
        val nlen = bits(5) + 257; val ndist = bits(5) + 1; val ncode = bits(4) + 4
        val lengths = IntArray(320)
        for (i in 0 until ncode) lengths[ORDER[i]] = bits(3)
        val lencode = Huff(lengths, 19)
        var idx = 0
        val all = IntArray(nlen + ndist)
        while (idx < nlen + ndist) {
            val sym = decode(lencode)
            if (sym < 16) { all[idx++] = sym; continue }
            var prev = 0; val rep: Int
            when (sym) {
                16 -> { if (idx == 0) throw ZipException("Invalid repeat"); prev = all[idx - 1]; rep = 3 + bits(2) }
                17 -> rep = 3 + bits(3)
                else -> rep = 11 + bits(7)
            }
            if (idx + rep > nlen + ndist) throw ZipException("Invalid repeat length")
            repeat(rep) { all[idx++] = prev }
        }
        val ll = IntArray(288); all.copyInto(ll, 0, 0, nlen)
        val dl = IntArray(30); all.copyInto(dl, 0, nlen, nlen + ndist)
        codes(Huff(ll, nlen), Huff(dl, ndist))
    }

    private fun codes(lit: Huff, dist: Huff) {
        while (true) {
            var sym = decode(lit)
            if (sym < 256) { out.put(sym); continue }
            if (sym == 256) return
            sym -= 257
            if (sym >= 29) throw ZipException("Invalid length code")
            val len = LBASE[sym] + bits(LEXT[sym])
            val ds = decode(dist)
            if (ds >= 30) throw ZipException("Invalid distance code")
            val d = DBASE[ds] + bits(DEXT[ds])
            if (d > out.n) throw ZipException("Invalid distance")
            out.ensure(len)
            var from = out.n - d
            repeat(len) { out.a[out.n++] = out.a[from++] }
        }
    }

    private companion object {
        val ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)
        val LBASE = intArrayOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258)
        val LEXT = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0)
        val DBASE = intArrayOf(1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577)
        val DEXT = intArrayOf(0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13)
        val FIXED_LIT: Huff = Huff(IntArray(288) { if (it < 144) 8 else if (it < 256) 9 else if (it < 280) 7 else 8 }, 288)
        val FIXED_DIST: Huff = Huff(IntArray(30) { 5 }, 30)
    }
}
