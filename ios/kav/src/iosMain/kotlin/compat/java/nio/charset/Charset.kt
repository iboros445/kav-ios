// java.nio.charset for Kotlin/Native, which has no Charset in its stdlib. Only the three charsets
// upstream uses are decodable; JvmStd.kt.template wires them into String(bytes, cs) / toByteArray(cs).
package java.nio.charset

class Charset private constructor(val name: String) {
    override fun toString(): String = name

    companion object {
        val UTF_8 = Charset("UTF-8")
        val ISO_8859_1 = Charset("ISO-8859-1")
        val US_ASCII = Charset("US-ASCII")
    }
}

object StandardCharsets {
    val UTF_8 = Charset.UTF_8
    val ISO_8859_1 = Charset.ISO_8859_1
    val US_ASCII = Charset.US_ASCII
}
