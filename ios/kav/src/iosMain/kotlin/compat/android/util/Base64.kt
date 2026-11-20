package android.util

import kotlin.io.encoding.Base64 as Kb
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object Base64 {
    const val DEFAULT = 0
    const val NO_PADDING = 1
    const val NO_WRAP = 2
    const val URL_SAFE = 8

    fun encodeToString(input: ByteArray, flags: Int): String {
        val codec = if (flags and URL_SAFE != 0) Kb.UrlSafe else Kb.Default
        val s = (if (flags and NO_PADDING != 0) codec.withPadding(Kb.PaddingOption.ABSENT) else codec).encode(input)
        return if (flags and NO_WRAP != 0) s else s.chunked(76).joinToString("\n") + "\n"
    }

    fun decode(input: String, flags: Int): ByteArray {
        val codec = if (flags and URL_SAFE != 0) Kb.UrlSafe else Kb.Default
        return codec.withPadding(Kb.PaddingOption.PRESENT_OPTIONAL).decode(input.filterNot { it.isWhitespace() })
    }
}
