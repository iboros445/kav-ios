// HttpURLConnection over NSURLSession. The request is sent lazily on the first call that needs the response
// (responseCode, headers, inputStream), like the JVM. The body is streamed through a queue, so large
// downloads (the 176 MB map) are not held in memory and `read` reports progress as data arrives.
package java.net

import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kav.compat.native.toByteArray
import kav.compat.native.toNSData
import platform.Foundation.NSCondition
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionResponseAllow
import platform.Foundation.NSURLSessionResponseDisposition
import platform.Foundation.NSURLSessionTask
import platform.Foundation.HTTPBody
import platform.Foundation.HTTPMethod
import platform.Foundation.setValue
import platform.Foundation.NSData
import platform.darwin.NSObject

abstract class URLConnection(val url: URL) {
    var connectTimeout: Int = 0
    var readTimeout: Int = 0
    var doOutput: Boolean = false
    var doInput: Boolean = true
    var useCaches: Boolean = true
    var allowUserInteraction: Boolean = false
    abstract fun connect()
    abstract fun setRequestProperty(key: String, value: String)
    abstract fun addRequestProperty(key: String, value: String)
    abstract fun getHeaderField(name: String?): String?
    abstract val inputStream: InputStream
    abstract val outputStream: OutputStream
    val contentEncoding: String? get() = getHeaderField("Content-Encoding")
    val contentType: String? get() = getHeaderField("Content-Type")
    val contentLength: Int get() = getHeaderField("Content-Length")?.toIntOrNull() ?: -1
    val contentLengthLong: Long get() = getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
}

open class HttpURLConnection(url: URL) : URLConnection(url) {
    var requestMethod: String = "GET"
    var instanceFollowRedirects: Boolean = true

    private val requestHeaders = LinkedHashMap<String, String>()
    private val body = ByteArrayOutputStream()
    private var exchange: Exchange? = null

    override fun setRequestProperty(key: String, value: String) {
        requestHeaders.keys.firstOrNull { it.equals(key, ignoreCase = true) }?.let { requestHeaders.remove(it) }
        requestHeaders[key] = value
    }

    override fun addRequestProperty(key: String, value: String) {
        val have = requestHeaders.keys.firstOrNull { it.equals(key, ignoreCase = true) }
        if (have == null) requestHeaders[key] = value else requestHeaders[have] = requestHeaders[have] + "," + value
    }

    fun getRequestProperty(key: String): String? = requestHeaders.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value

    override val outputStream: OutputStream
        get() {
            check(exchange == null) { "Cannot write output after reading input." }
            doOutput = true
            return body
        }

    private fun started(): Exchange {
        exchange?.let { return it }
        val method = if (doOutput && requestMethod == "GET") "POST" else requestMethod
        val ex = Exchange(
            url.toExternalForm(), method, requestHeaders.toMap(),
            if (doOutput || body.size() > 0) body.toByteArray() else null,
            readTimeout, instanceFollowRedirects,
        )
        exchange = ex
        ex.start()
        return ex
    }

    override fun connect() { started() }

    val responseCode: Int get() = started().awaitResponse().status
    val responseMessage: String? get() = null

    override fun getHeaderField(name: String?): String? = name?.let { started().awaitResponse().headers[it.lowercase()] }

    override val inputStream: InputStream
        get() {
            val r = started().awaitResponse()
            if (r.status >= 400) throw if (r.status == 404 || r.status == 410) FileNotFoundException(url.toExternalForm()) else IOException("Server returned HTTP response code: ${r.status} for URL: ${url.toExternalForm()}")
            return started().stream
        }

    /** The body of a 4xx/5xx response, or null when the request succeeded. */
    val errorStream: InputStream? get() = if (started().awaitResponse().status >= 400) started().stream else null

    fun disconnect() { exchange?.cancel() }

    private class Response(val status: Int, val headers: Map<String, String>)

    private class Exchange(
        val url: String, val method: String, val requestHeaders: Map<String, String>, val body: ByteArray?,
        val readTimeoutMs: Int, val followRedirects: Boolean,
    ) {
        private val lock = NSCondition()
        private var response: Response? = null
        private var failure: String? = null
        private var finished = false
        private val chunks = ArrayDeque<ByteArray>()
        private var task: NSURLSessionDataTask? = null
        private var session: NSURLSession? = null
        // the delegate must be kept alive for as long as the session
        private var delegate: Delegate? = null

        fun start() {
            val nsUrl = NSURL.URLWithString(url) ?: throw IOException("Bad URL: $url")
            val req = NSMutableURLRequest.requestWithURL(nsUrl)
            req.HTTPMethod = method
            req.setCachePolicy(NSURLRequestReloadIgnoringLocalCacheData)
            req.setTimeoutInterval(if (readTimeoutMs > 0) readTimeoutMs / 1000.0 else 60.0)
            for ((k, v) in requestHeaders) req.setValue(v, forHTTPHeaderField = k)
            if (body != null) req.HTTPBody = body.toNSData()
            val config = NSURLSessionConfiguration.ephemeralSessionConfiguration
            config.HTTPShouldSetCookies = false
            val queue = NSOperationQueue().apply { maxConcurrentOperationCount = 1 }
            val d = Delegate(this)
            delegate = d
            val s = NSURLSession.sessionWithConfiguration(config, d, queue)
            session = s
            task = s.dataTaskWithRequest(req).also { it.resume() }
            s.finishTasksAndInvalidate()
        }

        fun cancel() {
            task?.cancel()
            lock.lock(); finished = true; if (failure == null && response == null) failure = "Canceled"; lock.broadcast(); lock.unlock()
        }

        fun awaitResponse(): Response {
            lock.lock()
            try {
                while (response == null && failure == null) lock.wait()
                response?.let { return it }
                throw IOException(failure)
            } finally { lock.unlock() }
        }

        val stream: InputStream = object : InputStream() {
            private var current: ByteArray? = null
            private var at = 0

            override fun read(): Int {
                val one = ByteArray(1)
                return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (len == 0) return 0
                var cur = current
                if (cur == null || at >= cur.size) {
                    lock.lock()
                    try {
                        while (chunks.isEmpty() && !finished) lock.wait()
                        if (chunks.isEmpty()) {
                            if (failure != null) throw IOException(failure)
                            return -1
                        }
                        cur = chunks.removeFirst()
                    } finally { lock.unlock() }
                    current = cur; at = 0
                }
                val n = minOf(len, cur!!.size - at)
                cur.copyInto(b, off, at, at + n)
                at += n
                return n
            }

            override fun available(): Int = current?.let { it.size - at } ?: 0
            override fun close() { cancel() }
        }

        fun onResponse(r: NSHTTPURLResponse) {
            val map = HashMap<String, String>()
            r.allHeaderFields.forEach { (k, v) -> if (k != null && v != null) map[k.toString().lowercase()] = v.toString() }
            lock.lock(); response = Response(r.statusCode.toInt(), map); lock.broadcast(); lock.unlock()
        }

        fun onData(bytes: ByteArray) { lock.lock(); chunks.addLast(bytes); lock.broadcast(); lock.unlock() }

        fun onComplete(error: NSError?) {
            lock.lock()
            if (error != null && failure == null && !finished) failure = error.localizedDescription
            finished = true
            lock.broadcast(); lock.unlock()
        }
    }

    private class Delegate(private val ex: Exchange) : NSObject(), NSURLSessionDataDelegateProtocol {
        override fun URLSession(
            session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveResponse: NSURLResponse,
            completionHandler: (NSURLSessionResponseDisposition) -> Unit,
        ) {
            (didReceiveResponse as? NSHTTPURLResponse)?.let { ex.onResponse(it) }
            completionHandler(NSURLSessionResponseAllow)
        }

        override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
            ex.onData(didReceiveData.toByteArray())
        }

        override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
            ex.onComplete(didCompleteWithError)
        }

        override fun URLSession(
            session: NSURLSession, task: NSURLSessionTask, willPerformHTTPRedirection: NSHTTPURLResponse,
            newRequest: NSURLRequest, completionHandler: (NSURLRequest?) -> Unit,
        ) {
            completionHandler(if (ex.followRedirects) newRequest else null)
        }
    }
}
