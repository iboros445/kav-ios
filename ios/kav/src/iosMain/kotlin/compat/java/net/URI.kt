// java.net.URI / URL / URLDecoder / URLEncoder in pure Kotlin (RFC 3986 shape, Java's accessor names).
package java.net

import java.io.IOException
import java.nio.charset.Charset

class URISyntaxException(val input: String, val reason: String) : Exception("$reason: $input")
class MalformedURLException(message: String? = null) : IOException(message)

class URI(private val text: String) : Comparable<URI> {
    val scheme: String?
    val rawAuthority: String?
    val rawPath: String
    val rawQuery: String?
    val rawFragment: String?
    val userInfo: String?
    val host: String?
    val port: Int

    init {
        for ((i, c) in text.withIndex()) {
            if (c <= ' ' || c in "\"<>\\^`{|}") throw URISyntaxException(text, "Illegal character at index $i")
        }
        var rest = text
        rawFragment = rest.substringAfter('#', "").takeIf { '#' in rest }.also { rest = rest.substringBefore('#') }
        rawQuery = rest.substringAfter('?', "").takeIf { '?' in rest }.also { rest = rest.substringBefore('?') }
        val colon = rest.indexOf(':')
        scheme = if (colon > 0 && rest.substring(0, colon).let { s -> s[0].isLetter() && s.all { it.isLetterOrDigit() || it in "+-." } }) {
            rest.substring(0, colon).also { rest = rest.substring(colon + 1) }
        } else null
        if (rest.startsWith("//")) {
            val end = rest.indexOf('/', 2).let { if (it < 0) rest.length else it }
            rawAuthority = rest.substring(2, end)
            rawPath = rest.substring(end)
        } else {
            rawAuthority = null
            rawPath = rest
        }
        val auth = rawAuthority
        if (auth != null) {
            userInfo = if ('@' in auth) auth.substringBeforeLast('@') else null
            val hp = auth.substringAfterLast('@')
            val closeBracket = hp.indexOf(']')
            val portText = if (hp.startsWith("[")) hp.substring(closeBracket + 1).removePrefix(":") else hp.substringAfterLast(':', "").takeIf { ':' in hp } ?: ""
            host = (if (hp.startsWith("[")) hp.substring(0, closeBracket + 1) else hp.substringBeforeLast(':')).ifEmpty { null }
            port = portText.toIntOrNull() ?: -1
            if (portText.isNotEmpty() && portText.toIntOrNull() == null) throw URISyntaxException(text, "Illegal port")
        } else { userInfo = null; host = null; port = -1 }
    }

    val authority: String? get() = rawAuthority?.let { URLDecoder.decode(it, "UTF-8") }
    val path: String get() = URLDecoder.decode(rawPath.replace("+", "%2B"), "UTF-8")
    val query: String? get() = rawQuery?.let { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }
    val fragment: String? get() = rawFragment?.let { URLDecoder.decode(it, "UTF-8") }
    val isAbsolute: Boolean get() = scheme != null

    fun resolve(str: String): URI = resolve(URI(str))

    /** RFC 3986 section 5.2 reference resolution. */
    fun resolve(ref: URI): URI {
        if (ref.scheme != null) return ref
        val sb = StringBuilder()
        sb.append(scheme?.let { "$it:" } ?: "")
        if (ref.rawAuthority != null) {
            sb.append("//").append(ref.rawAuthority).append(removeDots(ref.rawPath))
            ref.rawQuery?.let { sb.append('?').append(it) }
        } else {
            sb.append(rawAuthority?.let { "//$it" } ?: "")
            val path: String
            val query: String?
            if (ref.rawPath.isEmpty()) {
                path = rawPath
                query = ref.rawQuery ?: rawQuery
            } else {
                path = if (ref.rawPath.startsWith("/")) removeDots(ref.rawPath) else removeDots(merge(ref.rawPath))
                query = ref.rawQuery
            }
            sb.append(path)
            query?.let { sb.append('?').append(it) }
        }
        ref.rawFragment?.let { sb.append('#').append(it) }
        return URI(sb.toString())
    }

    private fun merge(rel: String): String =
        if (rawAuthority != null && rawPath.isEmpty()) "/$rel" else rawPath.substring(0, rawPath.lastIndexOf('/') + 1) + rel

    private fun removeDots(p: String): String {
        if ('.' !in p) return p
        val out = ArrayList<String>()
        val parts = p.split('/')
        for ((i, seg) in parts.withIndex()) {
            when {
                seg == "." -> if (i == parts.lastIndex) out.add("")
                seg == ".." -> { if (out.size > 1) out.removeAt(out.lastIndex); if (i == parts.lastIndex) out.add("") }
                else -> out.add(seg)
            }
        }
        return out.joinToString("/")
    }

    fun toURL(): URL = URL(text)
    override fun compareTo(other: URI): Int = text.compareTo(other.text)
    override fun equals(other: Any?): Boolean = other is URI && other.text == text
    override fun hashCode(): Int = text.hashCode()
    override fun toString(): String = text
    fun toASCIIString(): String = text

    companion object { fun create(str: String): URI = URI(str) }
}

class URL(spec: String) {
    private val text: String = spec.trim()
    private val uri: URI = try { URI(text) } catch (e: URISyntaxException) { throw MalformedURLException(e.message) }

    init { if (uri.scheme == null) throw MalformedURLException("no protocol: $spec") }

    constructor(context: URL?, spec: String) : this(
        if (context == null) spec else try { context.uri.resolve(spec.trim()).toString() } catch (e: URISyntaxException) { throw MalformedURLException(e.message) },
    )

    val protocol: String get() = uri.scheme ?: ""
    val host: String get() = uri.host ?: ""
    val port: Int get() = uri.port
    val path: String get() = uri.rawPath
    val query: String? get() = uri.rawQuery
    val file: String get() = uri.rawPath + (uri.rawQuery?.let { "?$it" } ?: "")
    val ref: String? get() = uri.rawFragment
    val defaultPort: Int get() = when (protocol) { "http" -> 80; "https" -> 443; else -> -1 }

    fun toURI(): URI = uri
    fun toExternalForm(): String = text
    override fun toString(): String = text
    override fun equals(other: Any?): Boolean = other is URL && other.text == text
    override fun hashCode(): Int = text.hashCode()

    fun openConnection(): URLConnection = when (protocol.lowercase()) {
        "http", "https" -> HttpURLConnection(this)
        else -> throw IOException("Unsupported protocol: $protocol")
    }

    fun openConnection(proxy: Proxy): URLConnection =
        openConnection().also { if (it is HttpURLConnection && proxy.type() != Proxy.Type.DIRECT) it.proxy = proxy }

    fun openStream(): java.io.InputStream = openConnection().inputStream
}

object URLDecoder {
    fun decode(s: String, enc: String = "UTF-8"): String = decode(s)
    fun decode(s: String, charset: Charset): String = decode(s)

    private fun decode(s: String): String {
        if ('%' !in s && '+' !in s) return s
        val out = java.io.ByteArrayOutputStream(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '+' -> { out.write(' '.code); i++ }
                c == '%' -> {
                    if (i + 2 >= s.length) throw IllegalArgumentException("URLDecoder: Incomplete trailing escape (%) pattern")
                    out.write(s.substring(i + 1, i + 3).toIntOrNull(16)
                        ?: throw IllegalArgumentException("URLDecoder: Illegal hex characters in escape (%) pattern"))
                    i += 3
                }
                else -> {
                    val n = if (c.isHighSurrogate() && i + 1 < s.length) 2 else 1
                    out.write(s.substring(i, i + n).encodeToByteArray())
                    i += n
                }
            }
        }
        return out.toByteArray().decodeToString()
    }
}

object URLEncoder {
    fun encode(s: String, enc: String = "UTF-8"): String = encode(s)
    fun encode(s: String, charset: Charset): String = encode(s)

    private fun encode(s: String): String {
        val sb = StringBuilder()
        for (b in s.encodeToByteArray()) {
            val c = b.toInt() and 0xFF
            val ch = c.toChar()
            when {
                ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch in ".-*_" -> sb.append(ch)
                ch == ' ' -> sb.append('+')
                else -> sb.append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
            }
        }
        return sb.toString()
    }
}
