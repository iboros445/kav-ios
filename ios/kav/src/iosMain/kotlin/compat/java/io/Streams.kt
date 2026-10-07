// java.io streams for Kotlin/Native. Pure Kotlin; file-backed streams are in FileStreams.kt.
// Helpers Kotlin adds to the JVM types (readBytes, buffered, bufferedReader, use, ...) are members
// here, so upstream code calling them resolves without extra imports.
package java.io

import java.nio.charset.Charset

open class IOException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
open class FileNotFoundException(message: String? = null) : IOException(message)
class EOFException(message: String? = null) : IOException(message)
class UncheckedIOException(cause: IOException) : RuntimeException(cause.message, cause)

interface Closeable : AutoCloseable { override fun close() }
interface Flushable { fun flush() }

abstract class InputStream : Closeable {
    abstract fun read(): Int

    open fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        val first = read()
        if (first < 0) return -1
        b[off] = first.toByte()
        var n = 1
        while (n < len) {
            if (available() <= 0) break
            val c = read()
            if (c < 0) break
            b[off + n] = c.toByte(); n++
        }
        return n
    }

    fun read(b: ByteArray): Int = read(b, 0, b.size)
    open fun available(): Int = 0
    override fun close() {}

    open fun skip(n: Long): Long {
        var left = n
        val tmp = ByteArray(8192)
        while (left > 0) {
            val r = read(tmp, 0, minOf(left, tmp.size.toLong()).toInt())
            if (r < 0) break
            left -= r
        }
        return n - left
    }

    // ---- kotlin.io extensions, as members ----
    fun readBytes(): ByteArray {
        val out = ByteArrayOutputStream(maxOf(8192, available()))
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = read(buf, 0, buf.size)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    fun readAllBytes(): ByteArray = readBytes()
    fun buffered(bufferSize: Int = 8192): InputStream = this
    fun reader(charset: Charset = Charset.UTF_8): InputStreamReader = InputStreamReader(this, charset)
    fun bufferedReader(charset: Charset = Charset.UTF_8): BufferedReader = BufferedReader(InputStreamReader(this, charset))

    fun copyTo(out: OutputStream, bufferSize: Int = 8192): Long {
        val buf = ByteArray(bufferSize)
        var total = 0L
        while (true) {
            val n = read(buf, 0, buf.size)
            if (n < 0) return total
            out.write(buf, 0, n); total += n
        }
    }
}

class ByteArrayInputStream(private val buf: ByteArray, off: Int = 0, len: Int = buf.size - off) : InputStream() {
    private var pos = off
    private val end = minOf(off + len, buf.size)

    override fun read(): Int = if (pos < end) buf[pos++].toInt() and 0xFF else -1

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (pos >= end) return -1
        val n = minOf(len, end - pos)
        buf.copyInto(b, off, pos, pos + n)
        pos += n
        return n
    }

    override fun available(): Int = end - pos
}

abstract class OutputStream : Closeable, Flushable {
    abstract fun write(b: Int)
    open fun write(b: ByteArray, off: Int, len: Int) { for (i in off until off + len) write(b[i].toInt() and 0xFF) }
    fun write(b: ByteArray) = write(b, 0, b.size)
    override fun flush() {}
    override fun close() {}
    fun buffered(bufferSize: Int = 8192): OutputStream = this
}

class ByteArrayOutputStream(size: Int = 32) : OutputStream() {
    private var buf = ByteArray(maxOf(size, 16))
    private var count = 0

    private fun ensure(extra: Int) {
        if (count + extra > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, count + extra))
    }

    override fun write(b: Int) { ensure(1); buf[count++] = b.toByte() }

    override fun write(b: ByteArray, off: Int, len: Int) {
        ensure(len)
        b.copyInto(buf, count, off, off + len)
        count += len
    }

    fun size(): Int = count
    fun reset() { count = 0 }
    fun toByteArray(): ByteArray = buf.copyOf(count)
    override fun toString(): String = buf.decodeToString(0, count)
    fun toString(charset: Charset): String = toString()
}

abstract class Reader : Closeable {
    abstract fun read(cbuf: CharArray, off: Int, len: Int): Int
    fun read(cbuf: CharArray): Int = read(cbuf, 0, cbuf.size)
    open fun read(): Int {
        val one = CharArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].code
    }

    override fun close() {}

    fun readText(): String {
        val sb = StringBuilder()
        val buf = CharArray(4096)
        while (true) {
            val n = read(buf, 0, buf.size)
            if (n < 0) return sb.toString()
            sb.appendRange(buf, 0, n)
        }
    }
}

// Decodes UTF-8 (the only encoding Kav reads text in) incrementally from a byte stream.
class InputStreamReader(private val input: InputStream, @Suppress("unused") val charset: Charset = Charset.UTF_8) : Reader() {
    private var pending: CharArray = CharArray(0)
    private var pi = 0
    private val bytes = ByteArray(8192)
    private var carry = 0 // undecoded tail bytes at the start of `bytes`

    private fun fill(): Boolean {
        val n = input.read(bytes, carry, bytes.size - carry)
        if (n < 0) {
            if (carry > 0) { pending = bytes.decodeToString(0, carry).toCharArray(); carry = 0; pi = 0; return true }
            return false
        }
        val total = carry + n
        // keep an incomplete trailing UTF-8 sequence for the next round
        var cut = total
        var back = 0
        while (back < 3 && cut - 1 - back >= 0 && (bytes[cut - 1 - back].toInt() and 0xC0) == 0x80) back++
        if (cut - 1 - back >= 0) {
            val lead = bytes[cut - 1 - back].toInt() and 0xFF
            val need = when { lead >= 0xF0 -> 4; lead >= 0xE0 -> 3; lead >= 0xC0 -> 2; else -> 1 }
            if (need > back + 1) cut = cut - 1 - back
        }
        pending = bytes.decodeToString(0, cut).toCharArray()
        pi = 0
        carry = total - cut
        if (carry > 0) bytes.copyInto(bytes, 0, cut, total)
        return true
    }

    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        while (pi >= pending.size) if (!fill()) return -1
        val n = minOf(len, pending.size - pi)
        pending.copyInto(cbuf, off, pi, pi + n)
        pi += n
        return n
    }

    override fun close() = input.close()
}

class BufferedReader(private val inner: Reader) : Reader() {
    private var peeked = -2

    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        var n = 0
        if (peeked >= 0) { cbuf[off] = peeked.toChar(); peeked = -2; n = 1 }
        if (n < len) {
            val r = inner.read(cbuf, off + n, len - n)
            if (r < 0) return if (n > 0) n else -1
            n += r
        }
        return n
    }

    fun readLine(): String? {
        val sb = StringBuilder()
        var any = false
        while (true) {
            val c = read()
            if (c < 0) return if (any) sb.toString() else null
            any = true
            if (c == '\n'.code) return sb.toString()
            if (c == '\r'.code) {
                val next = read()
                if (next >= 0 && next != '\n'.code) peeked = next
                return sb.toString()
            }
            sb.append(c.toChar())
        }
    }

    override fun read(): Int {
        if (peeked >= 0) { val p = peeked; peeked = -2; return p }
        return super.read()
    }

    fun lineSequence(): Sequence<String> = generateSequence { readLine() }
    override fun close() = inner.close()
}

abstract class Writer : Closeable, Flushable {
    abstract fun write(cbuf: CharArray, off: Int, len: Int)
    fun write(str: String) { val a = str.toCharArray(); write(a, 0, a.size) }
    override fun flush() {}
    override fun close() {}
}

class OutputStreamWriter(private val out: OutputStream, @Suppress("unused") val charset: Charset = Charset.UTF_8) : Writer() {
    override fun write(cbuf: CharArray, off: Int, len: Int) { out.write(cbuf.concatToString(off, off + len).encodeToByteArray()) }
    override fun flush() = out.flush()
    override fun close() = out.close()
}

class BufferedWriter(private val inner: Writer) : Writer() {
    override fun write(cbuf: CharArray, off: Int, len: Int) = inner.write(cbuf, off, len)
    override fun flush() = inner.flush()
    override fun close() = inner.close()
}
