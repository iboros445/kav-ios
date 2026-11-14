package javax.net.ssl

open class SSLException(message: String? = null) : java.io.IOException(message)
class SSLHandshakeException(message: String? = null) : SSLException(message)
