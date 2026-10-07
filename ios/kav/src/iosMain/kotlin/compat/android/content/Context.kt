// A Context for code that was written against Android. It is the app: files, assets, preferences. Phase 2 adds
// what MainActivity needs (startActivity, system services) on top of this.
package android.content

import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

abstract class Context {
    open val applicationContext: Context get() = this
    open val packageName: String get() = "io.github.iboros445.kav"
    val packageManager: android.content.pm.PackageManager by lazy { android.content.pm.PackageManager() }
    val display: android.view.Display get() = android.view.Display()
    val resources: Resources by lazy { Resources() }
    fun getString(id: Int): String = if (id == uk.noammm.kav.R.string.app_name) "Kav" else ""

    fun startActivity(intent: Intent) = IntentRouter.start(intent)

    /** Managers for the services Kav asks for. Anything else is null, which callers treat as "not available". */
    fun getSystemService(name: String): Any? = when (name) {
        LOCATION_SERVICE -> locationManager
        SENSOR_SERVICE -> sensorManager
        CONNECTIVITY_SERVICE -> connectivityManager
        WINDOW_SERVICE -> android.view.WindowManager()
        else -> null
    }

    private val locationManager by lazy { android.location.LocationManager() }
    private val sensorManager by lazy { android.hardware.SensorManager() }
    private val connectivityManager by lazy { android.net.ConnectivityManager() }

    val filesDir: File by lazy { dir(NSApplicationSupportDirectory).also { it.mkdirs() } }
    val cacheDir: File by lazy { dir(NSCachesDirectory).also { it.mkdirs() } }
    val assets: AssetManager by lazy { AssetManager() }
    val contentResolver: ContentResolver by lazy { ContentResolver() }

    private val prefs = HashMap<String, SharedPreferences>()

    fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        prefs.getOrPut(name) { DefaultsPreferences(name) }

    private fun dir(kind: ULong): File =
        File((NSSearchPathForDirectoriesInDomains(kind, NSUserDomainMask, true).firstOrNull() as? String) ?: "/tmp")

    companion object {
        const val MODE_PRIVATE = 0
        const val LOCATION_SERVICE = "location"
        const val SENSOR_SERVICE = "sensor"
        const val CONNECTIVITY_SERVICE = "connectivity"
        const val WINDOW_SERVICE = "window"
        const val NOTIFICATION_SERVICE = "notification"
    }
}

/** The one Context the app has. Android code receives it where it expects an Application or Activity. */
object KavApplicationContext : Context()

class AssetManager {
    /** Files are bundled under `assets/`. KAV_ASSETS_DIR overrides it (the Gradle simulator tests use that). */
    fun open(path: String): InputStream {
        val override = NSProcessInfo.processInfo.environment["KAV_ASSETS_DIR"] as? String
        val base = override ?: ((NSBundle.mainBundle.resourcePath ?: "") + "/assets")
        return FileInputStream(File(base, path))
    }
}

class ContentResolver {
    private fun fileOf(uri: Uri): File = File(uri.path ?: throw java.io.FileNotFoundException(uri.toString()))

    fun openInputStream(uri: Uri): InputStream? = FileInputStream(fileOf(uri))

    /** mode "w" truncates, "wa" appends, "wt" truncates (the same as "w" here). */
    fun openOutputStream(uri: Uri, mode: String = "w"): OutputStream? {
        val out = FileOutputStream(fileOf(uri), mode.contains('a'))
        // a file staged by CreateDocument is offered to the save picker once the writer is done
        return object : OutputStream() {
            override fun write(b: Int) = out.write(b)
            override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
            override fun flush() = out.flush()
            override fun close() { out.close(); DocumentPickers.finishSave(uri) }
        }
    }

    fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val f = runCatching { fileOf(uri) }.getOrNull() ?: return null
        return object : Cursor {
            private val columns = projection?.toList() ?: listOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
            override fun moveToFirst(): Boolean = true
            override fun getColumnIndex(name: String): Int = columns.indexOf(name)
            override fun getString(index: Int): String? = when (columns.getOrNull(index)) {
                OpenableColumns.DISPLAY_NAME -> f.name
                OpenableColumns.SIZE -> f.length().toString()
                else -> null
            }
            override fun getLong(index: Int): Long = getString(index)?.toLongOrNull() ?: 0L
            override fun close() {}
        }
    }
}
