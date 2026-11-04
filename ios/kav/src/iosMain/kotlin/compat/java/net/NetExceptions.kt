package java.net

import java.io.IOException

open class SocketException(message: String? = null) : IOException(message)
class SocketTimeoutException(message: String? = null) : java.io.InterruptedIOException(message)
class UnknownHostException(message: String? = null) : IOException(message)
class ConnectException(message: String? = null) : SocketException(message)
class ProtocolException(message: String? = null) : IOException(message)
