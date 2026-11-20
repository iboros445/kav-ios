package android.security.keystore

object KeyProperties {
    const val PURPOSE_ENCRYPT = 1
    const val PURPOSE_DECRYPT = 2
    const val BLOCK_MODE_GCM = "GCM"
    const val ENCRYPTION_PADDING_NONE = "NoPadding"
    const val KEY_ALGORITHM_AES = "AES"
}

class KeyGenParameterSpec private constructor(val keystoreAlias: String) {
    class Builder(private val alias: String, @Suppress("UNUSED_PARAMETER") purposes: Int) {
        fun setBlockModes(vararg modes: String): Builder = this
        fun setEncryptionPaddings(vararg paddings: String): Builder = this
        fun build(): KeyGenParameterSpec = KeyGenParameterSpec(alias)
    }
}
