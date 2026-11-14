package java.io

/** Big-endian primitive writes, as java.io.DataOutputStream. */
open class DataOutputStream(private val out: OutputStream) : OutputStream() {
    private var written: Int = 0
    fun size(): Int = written
    override fun write(b: Int) { out.write(b); written++ }
    override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); written += len }
    fun writeByte(v: Int) = write(v and 0xFF)
    fun writeBoolean(v: Boolean) = write(if (v) 1 else 0)
    fun writeShort(v: Int) { write((v ushr 8) and 0xFF); write(v and 0xFF) }
    fun writeChar(v: Int) = writeShort(v)
    fun writeInt(v: Int) { for (s in intArrayOf(24, 16, 8, 0)) write((v ushr s) and 0xFF) }
    fun writeLong(v: Long) { for (s in 56 downTo 0 step 8) write(((v ushr s) and 0xFF).toInt()) }
    fun writeFloat(v: Float) = writeInt(v.toRawBits())
    fun writeDouble(v: Double) = writeLong(v.toRawBits())
    fun writeBytes(s: String) { for (c in s) write(c.code and 0xFF) }
    fun writeUTF(s: String) { val b = s.encodeToByteArray(); writeShort(b.size); write(b, 0, b.size) }
    override fun flush() = out.flush()
    override fun close() = out.close()
}
