package java.io

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.posix.FILE
import platform.posix.errno
import platform.posix.fclose
import platform.posix.fflush
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fwrite
import platform.posix.strerror
import kotlinx.cinterop.toKString

private fun open(path: String, mode: String): CPointer<FILE> =
    fopen(path, mode) ?: throw FileNotFoundException("$path (${strerror(errno)?.toKString()})")

class FileInputStream(file: File) : InputStream() {
    constructor(path: String) : this(File(path))

    private var fp: CPointer<FILE>? = open(file.path, "rb")

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        val f = fp ?: throw IOException("Stream Closed")
        val n = b.usePinned { fread(it.addressOf(off), 1.convert(), len.convert(), f) }.toInt()
        return if (n == 0) -1 else n
    }

    override fun close() { fp?.let { fclose(it) }; fp = null }
}

class FileOutputStream(file: File, append: Boolean = false) : OutputStream() {
    constructor(path: String, append: Boolean = false) : this(File(path), append)

    private var fp: CPointer<FILE>? = open(file.path, if (append) "ab" else "wb")

    override fun write(b: Int) { write(byteArrayOf(b.toByte()), 0, 1) }

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (len == 0) return
        val f = fp ?: throw IOException("Stream Closed")
        val n = b.usePinned { fwrite(it.addressOf(off), 1.convert(), len.convert(), f) }.toInt()
        if (n != len) throw IOException("Write failed (${strerror(errno)?.toKString()})")
    }

    override fun flush() { fp?.let { fflush(it) } }
    override fun close() { fp?.let { fclose(it) }; fp = null }
}
