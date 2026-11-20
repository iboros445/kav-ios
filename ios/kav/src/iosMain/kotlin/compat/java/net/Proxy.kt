// java.net.Proxy and the unresolved address it points at, for URL.openConnection(proxy) (RiderProxy.open). The
// connection hands them to NSURLSession as its proxy dictionary (HttpURLConnection.kt).
package java.net

abstract class SocketAddress

class InetSocketAddress(private val host: String, private val portNumber: Int) : SocketAddress() {
    val hostString: String get() = host
    val hostName: String get() = host
    val port: Int get() = portNumber
    val isUnresolved: Boolean get() = true
    override fun toString(): String = "$host:$portNumber"

    companion object {
        fun createUnresolved(host: String, port: Int): InetSocketAddress = InetSocketAddress(host, port)
    }
}

class Proxy(private val kind: Type, private val at: SocketAddress?) {
    enum class Type { DIRECT, HTTP, SOCKS }

    fun type(): Type = kind
    fun address(): SocketAddress? = at
    override fun toString(): String = if (kind == Type.DIRECT) "DIRECT" else "$kind @ $at"

    companion object {
        val NO_PROXY = Proxy(Type.DIRECT, null)
    }
}
