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
    // No app can be addressed by package on iOS: ShareSheet lists none of its own and offers the system sheet (More).
    @Suppress("UNUSED_PARAMETER")
    fun resolveActivity(intent: android.content.Intent, flags: Int): ResolveInfo? = null

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

class ResolveInfo internal constructor() {
    @Suppress("UNUSED_PARAMETER")
    fun loadLabel(pm: PackageManager): CharSequence = ""
    @Suppress("UNUSED_PARAMETER")
    fun loadIcon(pm: PackageManager): android.graphics.drawable.Drawable = object : android.graphics.drawable.Drawable() {}
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
