// java.io.File over NSFileManager / posix. Same surface Kotlin code sees on the JVM (including the
// kotlin.io helpers such as readText and deleteRecursively, as members).
package java.io

import java.nio.charset.Charset
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.posix.mkdir
import platform.posix.remove
import platform.posix.rename

class File(path: String) : Comparable<File> {
    val path: String = normalize(path)

    constructor(parent: File?, child: String) : this(if (parent == null) child else join(parent.path, child))
    constructor(parent: String?, child: String) : this(if (parent == null) child else join(parent, child))

    private val fm get() = NSFileManager.defaultManager

    val name: String get() = path.substringAfterLast('/')
    val parent: String? get() = when {
        '/' !in path -> null
        path.substringBeforeLast('/').isEmpty() -> if (path == "/") null else "/"
        else -> path.substringBeforeLast('/')
    }
    val parentFile: File? get() = parent?.let { File(it) }
    val isAbsolute: Boolean get() = path.startsWith("/")
    val absolutePath: String get() = if (isAbsolute) path else join(fm.currentDirectoryPath, path)
    val absoluteFile: File get() = File(absolutePath)
    // "." and ".." worked out by name. Links are left alone, so a folder and a file inside it always share a prefix
    // (MapPack's TarGz checks an entry stays inside the folder it unpacks into this way).
    val canonicalPath: String get() {
        val parts = ArrayList<String>()
        for (p in absolutePath.split('/')) when (p) {
            "", "." -> {}
            ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
            else -> parts.add(p)
        }
        return "/" + parts.joinToString("/")
    }
    val canonicalFile: File get() = File(canonicalPath)
    val extension: String get() = name.substringAfterLast('.', "")
    val nameWithoutExtension: String get() = name.substringBeforeLast('.')

    fun exists(): Boolean = fm.fileExistsAtPath(path)

    val isDirectory: Boolean get() = memScoped {
        val dir = alloc<BooleanVar>()
        fm.fileExistsAtPath(path, dir.ptr) && dir.value
    }

    val isFile: Boolean get() = exists() && !isDirectory
    fun canRead(): Boolean = fm.isReadableFileAtPath(path)
    fun canWrite(): Boolean = fm.isWritableFileAtPath(path)

    fun length(): Long = (fm.attributesOfItemAtPath(path, null)?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L

    fun lastModified(): Long =
        ((fm.attributesOfItemAtPath(path, null)?.get(NSFileModificationDate) as? NSDate)?.timeIntervalSince1970?.times(1000))?.toLong() ?: 0L

    /** Files and empty directories, like the JVM. */
    fun delete(): Boolean = remove(path) == 0
    fun deleteRecursively(): Boolean = !exists() || fm.removeItemAtPath(path, null)
    fun mkdir(): Boolean = mkdir(path, 0x1ED.convert()) == 0
    fun mkdirs(): Boolean = !exists() && fm.createDirectoryAtPath(path, true, null, null)
    fun renameTo(dest: File): Boolean = rename(path, dest.path) == 0
    fun createNewFile(): Boolean = if (exists()) false else fm.createFileAtPath(path, null, null)

    fun list(): Array<String>? =
        fm.contentsOfDirectoryAtPath(path, null)?.map { it as String }?.sorted()?.toTypedArray()

    fun listFiles(): Array<File>? = list()?.map { File(this, it) }?.toTypedArray()

    // kotlin.io's walk: this file, then everything under it, a folder before what's in it.
    fun walkTopDown(): Sequence<File> = sequence {
        yield(this@File)
        if (isDirectory) listFiles()?.forEach { yieldAll(it.walkTopDown()) }
    }

    fun readBytes(): ByteArray = FileInputStream(this).use { it.readBytes() }
    fun readText(charset: Charset = Charset.UTF_8): String = readBytes().decodeToString()
    fun writeBytes(bytes: ByteArray) { FileOutputStream(this).use { it.write(bytes) } }
    fun appendBytes(bytes: ByteArray) { FileOutputStream(this, true).use { it.write(bytes) } }
    fun writeText(text: String, charset: Charset = Charset.UTF_8) = writeBytes(text.encodeToByteArray())
    fun appendText(text: String, charset: Charset = Charset.UTF_8) = appendBytes(text.encodeToByteArray())
    fun forEachLine(charset: Charset = Charset.UTF_8, action: (String) -> Unit) { bufferedReader(charset).use { r -> r.lineSequence().forEach(action) } }
    fun bufferedWriter(charset: Charset = Charset.UTF_8): BufferedWriter = BufferedWriter(OutputStreamWriter(FileOutputStream(this), charset))
    fun setLastModified(ms: Long): Boolean = fm.setAttributes(mapOf(NSFileModificationDate to NSDate.dateWithTimeIntervalSince1970(ms / 1000.0)), path, null)
    fun inputStream(): FileInputStream = FileInputStream(this)
    fun outputStream(): FileOutputStream = FileOutputStream(this)
    fun bufferedReader(charset: Charset = Charset.UTF_8): BufferedReader = inputStream().bufferedReader(charset)

    fun copyTo(target: File, overwrite: Boolean = false): File {
        if (target.exists()) { if (!overwrite) throw IOException("The destination file already exists."); target.delete() }
        FileInputStream(this).use { i -> FileOutputStream(target).use { o -> i.copyTo(o) } }
        return target
    }

    fun toURI(): java.net.URI = java.net.URI("file://$absolutePath")

    override fun compareTo(other: File): Int = path.compareTo(other.path)
    override fun equals(other: Any?): Boolean = other is File && other.path == path
    override fun hashCode(): Int = path.hashCode()
    override fun toString(): String = path

    companion object {
        private var tempCounter = 0

        fun createTempFile(prefix: String, suffix: String?, directory: File?): File {
            val dir = directory ?: android.content.KavApplicationContext.cacheDir
            dir.mkdirs()
            while (true) {
                val f = File(dir, prefix + kotlin.random.Random.nextLong(1L shl 40).toString(36) + (tempCounter++) + (suffix ?: ".tmp"))
                if (!f.exists() && f.createNewFile()) return f
            }
        }

        fun createTempFile(prefix: String, suffix: String?): File = createTempFile(prefix, suffix, null)

        const val separator = "/"
        const val separatorChar = '/'

        private fun normalize(p: String): String {
            if (p.isEmpty()) return p
            val sb = StringBuilder()
            for (c in p) if (!(c == '/' && sb.isNotEmpty() && sb.last() == '/')) sb.append(c)
            while (sb.length > 1 && sb.last() == '/') sb.setLength(sb.length - 1)
            return sb.toString()
        }

        private fun join(parent: String, child: String): String =
            if (parent.isEmpty()) "/" + child.trimStart('/') else parent.trimEnd('/') + "/" + child.trimStart('/')
    }
}
