package android.content.pm

import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.NSFileCreationDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.timeIntervalSince1970

class PackageManager {
    class NameNotFoundException(message: String? = null) : Exception(message)

    companion object {
        const val PERMISSION_GRANTED = 0
        const val PERMISSION_DENIED = -1

        fun bundleVersionName(): String = (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String) ?: "0"
        fun bundleVersionCode(): Long = (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)?.toLongOrNull() ?: 0L
    }

    fun canRequestPackageInstalls(): Boolean = false

    /** This app only: its version from Info.plist; installed when its data folder was made, updated when the app bundle was. */
    fun getPackageInfo(packageName: String, flags: Int): PackageInfo = PackageInfo(
        packageName = packageName,
        versionName = bundleVersionName(),
        longVersionCode = bundleVersionCode(),
        firstInstallTime = dateOf(android.content.KavApplicationContext.filesDir.path, NSFileCreationDate),
        lastUpdateTime = dateOf(NSBundle.mainBundle.bundlePath, NSFileModificationDate),
    )

    private fun dateOf(path: String, key: Any?): Long =
        (((NSFileManager.defaultManager.attributesOfItemAtPath(path, null)?.get(key) as? NSDate)?.timeIntervalSince1970 ?: 0.0) * 1000).toLong()
}

class PackageInfo(
    val packageName: String,
    val versionName: String?,
    val longVersionCode: Long,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
) {
    val versionCode: Int get() = longVersionCode.toInt()
}
