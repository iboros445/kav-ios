package javax.crypto.spec

class GCMParameterSpec(val tLen: Int, src: ByteArray, offset: Int, len: Int) {
    constructor(tLen: Int, src: ByteArray) : this(tLen, src, 0, src.size)
    val iv: ByteArray = src.copyOfRange(offset, offset + len)
}
