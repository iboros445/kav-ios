// The slice of javax.crypto / java.security.KeyStore / android.security.keystore that Pay.kt's `Seal` uses:
// an AES-256 key generated once and kept in the iOS Keychain, AES-GCM with a random 12-byte IV.
package javax.crypto

import java.security.Key
import kotlin.random.Random

interface SecretKey : Key

class SecretKeySpec(val bytes: ByteArray, override val algorithm: String) : SecretKey {
    override val encoded: ByteArray get() = bytes
}

class AEADBadTagException(message: String? = null) : Exception(message)

class Cipher private constructor(private val transformation: String) {
    private var mode = 0
    private var key: ByteArray? = null
    private var spec: javax.crypto.spec.GCMParameterSpec? = null
    var iv: ByteArray? = null; private set

    fun init(opmode: Int, k: Key) {
        mode = opmode
        key = k.encoded
        // Encrypting with no IV given: generate one (Android's keystore does the same).
        if (opmode == ENCRYPT_MODE) iv = Random.Default.nextBytes(12)
    }

    fun init(opmode: Int, k: Key, parameters: javax.crypto.spec.GCMParameterSpec) {
        mode = opmode; key = k.encoded; spec = parameters
        iv = parameters.iv
    }

    fun doFinal(input: ByteArray): ByteArray = doFinal(input, 0, input.size)

    fun doFinal(input: ByteArray, offset: Int, length: Int): ByteArray {
        val k = key ?: throw IllegalStateException("Cipher not initialized")
        val data = input.copyOfRange(offset, offset + length)
        val nonce = iv ?: throw IllegalStateException("No IV")
        return when (mode) {
            ENCRYPT_MODE -> AesGcm.seal(k, nonce, data)
            DECRYPT_MODE -> AesGcm.open(k, nonce, data, tagBits = spec?.tLen ?: 128) ?: throw AEADBadTagException("Tag mismatch")
            else -> throw IllegalStateException("Cipher not initialized")
        }
    }

    companion object {
        const val ENCRYPT_MODE = 1
        const val DECRYPT_MODE = 2
        fun getInstance(transformation: String): Cipher {
            require(transformation == "AES/GCM/NoPadding") { "Unsupported transformation: $transformation" }
            return Cipher(transformation)
        }
    }
}

class KeyGenerator private constructor() {
    private var alias: String? = null

    fun init(spec: android.security.keystore.KeyGenParameterSpec) { alias = spec.keystoreAlias }

    fun generateKey(): SecretKey {
        val raw = Random.Default.nextBytes(32)
        alias?.let { KeychainStore.put(it, raw) }
        return SecretKeySpec(raw, "AES")
    }

    companion object {
        fun getInstance(algorithm: String, provider: String): KeyGenerator = KeyGenerator()
    }
}
