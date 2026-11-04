// java.util.Base64 on kotlin.io.encoding: the basic and URL-safe alphabets, with or without padding.
package java.util

import kotlin.io.encoding.Base64 as Kb
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object Base64 {
    class Encoder internal constructor(private val url: Boolean, private val pad: Boolean) {
        private val codec get() = (if (url) Kb.UrlSafe else Kb.Default).withPadding(if (pad) Kb.PaddingOption.PRESENT else Kb.PaddingOption.ABSENT)
        fun withoutPadding(): Encoder = Encoder(url, false)
        fun encodeToString(src: ByteArray): String = codec.encode(src)
        fun encode(src: ByteArray): ByteArray = codec.encodeToByteArray(src)
    }

    class Decoder internal constructor(private val url: Boolean, private val mime: Boolean = false) {
        private val codec get() = (if (url) Kb.UrlSafe else Kb.Default).withPadding(Kb.PaddingOption.PRESENT_OPTIONAL)
        fun decode(src: String): ByteArray = codec.decode(if (mime) src.filterNot { it.isWhitespace() } else src)
        fun decode(src: ByteArray): ByteArray = decode(src.decodeToString())
    }

    fun getEncoder(): Encoder = Encoder(url = false, pad = true)
    fun getUrlEncoder(): Encoder = Encoder(url = true, pad = true)
    fun getMimeEncoder(): Encoder = Encoder(url = false, pad = true)
    fun getDecoder(): Decoder = Decoder(url = false)
    fun getUrlDecoder(): Decoder = Decoder(url = true)
    fun getMimeDecoder(): Decoder = Decoder(url = false, mime = true)
}
