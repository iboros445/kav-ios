// javax.crypto.Mac for HmacSHA256 (KavLive signs its requests with one), on CommonCrypto's CCHmac.
package javax.crypto

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.kCCHmacAlgSHA256

class Mac private constructor(private val algorithm: String) {
    private var key: ByteArray? = null
    private val buf = java.io.ByteArrayOutputStream()

    fun init(k: java.security.Key) { key = k.encoded; buf.reset() }
    fun update(input: ByteArray) { buf.write(input, 0, input.size) }
    fun doFinal(input: ByteArray): ByteArray { update(input); return doFinal() }

    fun doFinal(): ByteArray {
        val k = key ?: throw IllegalStateException("Mac not initialized")
        val data = buf.toByteArray().also { buf.reset() }
        val out = ByteArray(32)
        val kk = if (k.isEmpty()) ByteArray(1) else k
        val dd = if (data.isEmpty()) ByteArray(1) else data
        kk.usePinned { kp -> dd.usePinned { dp -> out.usePinned { op ->
            CCHmac(kCCHmacAlgSHA256, kp.addressOf(0), k.size.convert(), dp.addressOf(0), data.size.convert(), op.addressOf(0))
        } } }
        return out
    }

    companion object {
        fun getInstance(algorithm: String): Mac {
            require(algorithm == "HmacSHA256") { "Unsupported MAC: $algorithm" }
            return Mac(algorithm)
        }
    }
}
