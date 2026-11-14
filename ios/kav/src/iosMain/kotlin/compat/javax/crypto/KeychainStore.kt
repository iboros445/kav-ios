// Generic-password items in the iOS Keychain, one per alias. Readable after the first unlock, never leaves the device.
package javax.crypto

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.cinterop.reinterpret
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

internal object KeychainStore {
    private const val SERVICE = "io.github.iboros445.kav"

    private fun str(s: String) = CFStringCreateWithCString(kCFAllocatorDefault, s, kCFStringEncodingUTF8)

    private fun base(alias: String) = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, null, null).also {
        CFDictionaryAddValue(it, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(it, kSecAttrService, str(SERVICE))
        CFDictionaryAddValue(it, kSecAttrAccount, str(alias))
    }

    // A sideloaded or containerised app may be refused Keychain access (no entitlement); the key then lives in a file
    // that iOS keeps out of backups and encrypts at rest while the device is locked.
    private fun fallback(alias: String) = java.io.File(android.content.KavApplicationContext.filesDir, ".kav-key-" + alias)

    fun get(alias: String): ByteArray? = fromKeychain(alias) ?: fallback(alias).takeIf { it.exists() }?.readBytes()

    fun put(alias: String, value: ByteArray) {
        if (!toKeychain(alias, value)) fallback(alias).writeBytes(value) else fallback(alias).delete()
    }

    private fun fromKeychain(alias: String): ByteArray? = memScoped {
        val q = base(alias)
        CFDictionaryAddValue(q, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(q, kSecMatchLimit, kSecMatchLimitOne)
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(q, result.ptr)
        if (status != errSecSuccess) return@memScoped null
        val data = result.value as CFDataRef
        val n = CFDataGetLength(data).toInt()
        val ptr = CFDataGetBytePtr(data)
        ByteArray(n) { ptr!![it].toByte() }.also { CFRelease(data) }
    }

    private fun toKeychain(alias: String, value: ByteArray): Boolean {
        SecItemDelete(base(alias))
        val q = base(alias)
        val data = value.usePinned { CFDataCreate(kCFAllocatorDefault, it.addressOf(0).reinterpret(), value.size.convert()) }
        CFDictionaryAddValue(q, kSecValueData, data)
        CFDictionaryAddValue(q, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        val status = SecItemAdd(q, null)
        CFRelease(data)
        return status == errSecSuccess
    }
}
