package android.net

import java.io.File
import java.net.URI
import java.net.URLDecoder

class Uri private constructor(private val text: String) {
    private val uri: URI? = runCatching { URI(text) }.getOrNull()

    val scheme: String? get() = uri?.scheme
    val host: String? get() = uri?.host
    val authority: String? get() = uri?.authority
    val path: String? get() = uri?.path
    val encodedPath: String? get() = uri?.rawPath
    val query: String? get() = uri?.query
    val encodedQuery: String? get() = uri?.rawQuery
    val fragment: String? get() = uri?.fragment
    val lastPathSegment: String? get() = path?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }
    val pathSegments: List<String> get() = path.orEmpty().split('/').filter { it.isNotEmpty() }
    val isHierarchical: Boolean get() = uri?.let { !it.rawPath.isEmpty() || it.rawAuthority != null } ?: false

    fun getQueryParameter(key: String): String? {
        val q = encodedQuery ?: return null
        for (pair in q.split('&')) {
            val k = URLDecoder.decode(pair.substringBefore('='), "UTF-8")
            if (k == key) return URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
        }
        return null
    }

    fun getQueryParameterNames(): Set<String> =
        encodedQuery?.split('&')?.map { URLDecoder.decode(it.substringBefore('='), "UTF-8") }?.toSet() ?: emptySet()

    override fun toString(): String = text
    override fun equals(other: Any?): Boolean = other is Uri && other.text == text
    override fun hashCode(): Int = text.hashCode()

    companion object {
        val EMPTY = Uri("")
        fun parse(s: String): Uri = Uri(s)
        fun fromFile(f: File): Uri = Uri("file://" + f.absolutePath)
        fun encode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        fun decode(s: String): String = URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
    }
}
