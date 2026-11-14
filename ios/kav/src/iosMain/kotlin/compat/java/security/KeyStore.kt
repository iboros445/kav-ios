package java.security

import javax.crypto.KeychainStore
import javax.crypto.SecretKey
import javax.crypto.SecretKeySpec

interface Key {
    val algorithm: String
    val encoded: ByteArray
}

class KeyStore private constructor() {
    fun load(param: Any?) {}

    fun getKey(alias: String, password: CharArray?): Key? =
        KeychainStore.get(alias)?.let { SecretKeySpec(it, "AES") as SecretKey }

    companion object {
        fun getInstance(type: String): KeyStore = KeyStore()
    }
}
