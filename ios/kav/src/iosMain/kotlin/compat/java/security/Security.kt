// java.security for upstream's checks: SHA digests of downloads (MessageDigest), random bytes (SecureRandom), and the
// P-256 ECDSA signature on the timetable manifest (KeyFactory "EC" + X509EncodedKeySpec + Signature "SHA256withECDSA").
// Digests come from CommonCrypto; the signature check from Security.framework (SecKeyVerifySignature, X9.62 DER).
package java.security

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_MD5
import platform.CoreCrypto.CC_SHA1
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA512
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFAllocatorDefault
import platform.Security.SecKeyCreateWithData
import platform.Security.SecKeyVerifySignature
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPublic
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import platform.Security.kSecKeyAlgorithmECDSASignatureMessageX962SHA256
import platform.Security.kSecRandomDefault

open class GeneralSecurityException(message: String? = null) : Exception(message)
class NoSuchAlgorithmException(message: String? = null) : GeneralSecurityException(message)
class SignatureException(message: String? = null) : GeneralSecurityException(message)
class InvalidKeyException(message: String? = null) : GeneralSecurityException(message)

class MessageDigest private constructor(private val algorithm: String) {
    private val buf = java.io.ByteArrayOutputStream()

    fun update(input: ByteArray) { buf.write(input, 0, input.size) }
    fun update(input: ByteArray, offset: Int, len: Int) { buf.write(input, offset, len) }
    fun update(b: Byte) { buf.write(b.toInt()) }
    fun reset() { buf.reset() }
    fun digest(input: ByteArray): ByteArray { update(input); return digest() }

    fun digest(): ByteArray {
        val data = buf.toByteArray().also { reset() }
        val (size, fn) = when (algorithm) {
            "SHA-256" -> 32 to ::CC_SHA256
            "SHA-1" -> 20 to ::CC_SHA1
            "SHA-512" -> 64 to ::CC_SHA512
            "MD5" -> 16 to ::CC_MD5
            else -> throw NoSuchAlgorithmException(algorithm)
        }
        val out = UByteArray(size)
        val src = if (data.isEmpty()) ByteArray(1) else data
        src.usePinned { s -> out.usePinned { o -> fn(s.addressOf(0), data.size.convert(), o.addressOf(0)) } }
        return out.toByteArray()
    }

    companion object {
        fun getInstance(algorithm: String): MessageDigest {
            val a = algorithm.uppercase().let { if (it == "SHA256") "SHA-256" else if (it == "SHA1") "SHA-1" else it }
            if (a !in setOf("SHA-256", "SHA-1", "SHA-512", "MD5")) throw NoSuchAlgorithmException(algorithm)
            return MessageDigest(a)
        }
    }
}

open class SecureRandom {
    fun nextBytes(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val status = bytes.usePinned { SecRandomCopyBytes(kSecRandomDefault, bytes.size.convert(), it.addressOf(0)) }
        check(status == 0) { "SecRandomCopyBytes failed: $status" }
    }
    fun nextInt(): Int = ByteArray(4).also(::nextBytes).fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }
    fun nextInt(bound: Int): Int {
        require(bound > 0)
        val limit = Int.MAX_VALUE - Int.MAX_VALUE % bound
        while (true) { val r = nextInt() and Int.MAX_VALUE; if (r < limit) return r % bound }
    }
    fun nextLong(): Long = ByteArray(8).also(::nextBytes).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
    fun nextDouble(): Double = (nextLong() ushr 11) * (1.0 / (1L shl 53))
    fun nextBoolean(): Boolean = nextInt() and 1 == 1
}

interface PublicKey : Key

class ECPublicKey internal constructor(internal val point: ByteArray, override val encoded: ByteArray) : PublicKey {
    override val algorithm: String get() = "EC"
}

class KeyFactory private constructor(private val algorithm: String) {
    fun generatePublic(spec: java.security.spec.KeySpec): PublicKey {
        val der = (spec as? java.security.spec.X509EncodedKeySpec)?.encoded ?: throw InvalidKeyException("Unsupported key spec")
        return ECPublicKey(Spki.p256Point(der) ?: throw InvalidKeyException("Not a P-256 public key"), der)
    }
    companion object {
        fun getInstance(algorithm: String): KeyFactory {
            if (algorithm != "EC") throw NoSuchAlgorithmException(algorithm)
            return KeyFactory(algorithm)
        }
    }
}

class Signature private constructor(private val algorithm: String) {
    private var key: ECPublicKey? = null
    private val buf = java.io.ByteArrayOutputStream()

    fun initVerify(publicKey: PublicKey) {
        key = publicKey as? ECPublicKey ?: throw InvalidKeyException("Unsupported key")
        buf.reset()
    }
    fun update(data: ByteArray) { buf.write(data, 0, data.size) }
    fun update(data: ByteArray, off: Int, len: Int) { buf.write(data, off, len) }

    /** [sig] is the ASN.1 DER (r, s) form Java produces, which is also Security.framework's X9.62 form. */
    fun verify(sig: ByteArray): Boolean {
        val k = key ?: throw SignatureException("Not initialised for verification")
        val attrs = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, null, null)
        CFDictionaryAddValue(attrs, kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
        CFDictionaryAddValue(attrs, kSecAttrKeyClass, kSecAttrKeyClassPublic)
        val keyData = cfData(k.point)
        val secKey = SecKeyCreateWithData(keyData, attrs, null)
        CFRelease(keyData); CFRelease(attrs)
        if (secKey == null) return false
        val message = cfData(buf.toByteArray())
        val signature = cfData(sig)
        val ok = SecKeyVerifySignature(secKey, kSecKeyAlgorithmECDSASignatureMessageX962SHA256, message, signature, null)
        CFRelease(message); CFRelease(signature); CFRelease(secKey)
        return ok
    }

    private fun cfData(b: ByteArray) =
        if (b.isEmpty()) CFDataCreate(kCFAllocatorDefault, null, 0)
        else b.usePinned { CFDataCreate(kCFAllocatorDefault, it.addressOf(0).reinterpret<UByteVar>() as CPointer<UByteVar>, b.size.convert()) }

    companion object {
        fun getInstance(algorithm: String): Signature {
            if (algorithm != "SHA256withECDSA") throw NoSuchAlgorithmException(algorithm)
            return Signature(algorithm)
        }
    }
}

/** Pulls the uncompressed point (0x04 || X || Y, 65 bytes) out of a DER SubjectPublicKeyInfo for a P-256 key. */
internal object Spki {
    private val P256_OID = byteArrayOf(0x06, 0x08, 0x2A, 0x86.toByte(), 0x48, 0xCE.toByte(), 0x3D, 0x03, 0x01, 0x07)

    fun p256Point(der: ByteArray): ByteArray? {
        if (indexOf(der, P256_OID) < 0) return null
        // The BIT STRING: 0x03, length 0x42, 0x00 unused bits, then the 65-byte point.
        for (i in 0 until der.size - 67) {
            if (der[i] == 0x03.toByte() && der[i + 1] == 0x42.toByte() && der[i + 2] == 0.toByte() && der[i + 3] == 0x04.toByte())
                return der.copyOfRange(i + 3, i + 3 + 65)
        }
        return null
    }

    private fun indexOf(a: ByteArray, b: ByteArray): Int {
        outer@ for (i in 0..a.size - b.size) { for (j in b.indices) if (a[i + j] != b[j]) continue@outer; return i }
        return -1
    }
}
