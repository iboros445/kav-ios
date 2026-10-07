// Logs go to stdout and to Documents/kav.log (visible in the Files app under Kav, since file sharing is on),
// so a crash on a phone without a Mac can still be read. The file is rotated at 512 KB.
package android.util

import java.io.File
import java.io.FileOutputStream
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

object Log {
    const val VERBOSE = 2
    const val DEBUG = 3
    const val INFO = 4
    const val WARN = 5
    const val ERROR = 6

    fun v(tag: String, msg: String): Int = write("V", tag, msg, null)
    fun d(tag: String, msg: String): Int = write("D", tag, msg, null)
    fun i(tag: String, msg: String): Int = write("I", tag, msg, null)
    fun w(tag: String, msg: String): Int = write("W", tag, msg, null)
    fun w(tag: String, tr: Throwable?): Int = write("W", tag, "", tr)
    fun w(tag: String, msg: String, tr: Throwable?): Int = write("W", tag, msg, tr)
    fun e(tag: String, msg: String): Int = write("E", tag, msg, null)
    fun e(tag: String, msg: String, tr: Throwable?): Int = write("E", tag, msg, tr)
    fun getStackTraceString(tr: Throwable?): String = tr?.stackTraceToString() ?: ""

    private fun write(level: String, tag: String, msg: String, tr: Throwable?): Int {
        val line = "$level/$tag: $msg" + (tr?.let { "\n" + it.stackTraceToString() } ?: "")
        println(line)
        runCatching {
            val f = file() ?: return@runCatching
            if (f.length() > 512 * 1024) { File(f.path + ".1").delete(); f.renameTo(File(f.path + ".1")) }
            FileOutputStream(f, true).use { it.write((line + "\n").encodeToByteArray()) }
        }
        return line.length
    }

    private fun file(): File? =
        (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String)?.let { File(it, "kav.log") }
}
