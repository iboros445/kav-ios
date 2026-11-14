//@cmp
// android.webkit on WKWebView, for the pages Kav shows inside itself: Rav-Pass's card page, the ministry's report
// form and Street View. Covered: settings, the page callbacks (started, finished, failed, which URLs to keep out),
// loadUrl / loadDataWithBaseURL, evaluateJavascript, document-start scripts and cookies.
// Not covered: shouldInterceptRequest (WKWebView doesn't let an app answer https requests), file choosers, and
// addJavascriptInterface (Kotlin/Native has no reflection to call the object's methods by name).
package android.webkit

import android.content.Context
import android.net.Uri
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSError
import platform.Foundation.NSJSONSerialization
import platform.Foundation.NSJSONWritingFragmentsAllowed
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.HTTPMethod
import platform.Foundation.distantPast
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject

annotation class JavascriptInterface

// Java hands these null freely (no file picked, no script result), so the value is nullable here.
fun interface ValueCallback<T> { fun onReceiveValue(value: T?) }

interface WebResourceRequest {
    val url: Uri
    val isForMainFrame: Boolean
    val method: String
    val requestHeaders: Map<String, String>
    fun isRedirect(): Boolean = false
    fun hasGesture(): Boolean = false
}

internal class Request(override val url: Uri, override val isForMainFrame: Boolean, override val method: String) : WebResourceRequest {
    override val requestHeaders: Map<String, String> get() = emptyMap()
}

open class WebResourceError internal constructor(val errorCode: Int, val description: CharSequence)

class WebResourceResponse(
    var mimeType: String?, var encoding: String?, val statusCode: Int, val reasonPhrase: String?,
    val responseHeaders: Map<String, String>?, var data: java.io.InputStream?,
) {
    constructor(mimeType: String?, encoding: String?, data: java.io.InputStream?) : this(mimeType, encoding, 200, "OK", null, data)
}

open class WebViewClient {
    open fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
    open fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {}
    open fun onPageFinished(view: WebView, url: String?) {}
    open fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {}
    open fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? = null
    // Only a proxy's login reaches this on Android (RiderProxy.answer). Pages on iOS don't go through the rider's proxy
    // (RiderProxy.pagesFollow is false), so WKWebView never asks.
    open fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String?, realm: String?) { handler.cancel() }
}

open class WebChromeClient {
    abstract class FileChooserParams {
        abstract fun createIntent(): android.content.Intent
        companion object {
            fun parseResult(resultCode: Int, data: android.content.Intent?): Array<Uri>? = null
        }
    }
    open fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: FileChooserParams): Boolean = false
    open fun onProgressChanged(view: WebView, newProgress: Int) {}
}

class WebSettings internal constructor(private val web: WebView) {
    var javaScriptEnabled: Boolean = false
        set(v) { field = v; web.wk.configuration.defaultWebpagePreferences.allowsContentJavaScript = v }
    var domStorageEnabled: Boolean = false
    var allowFileAccess: Boolean = true
    var allowContentAccess: Boolean = true
    var mediaPlaybackRequiresUserGesture: Boolean = true
    var userAgentString: String?
        get() = web.wk.customUserAgent
        set(v) { web.wk.customUserAgent = v }
    fun setGeolocationEnabled(on: Boolean) {}
    fun setSupportMultipleWindows(on: Boolean) {}
    fun setSupportZoom(on: Boolean) {}

    companion object {
        const val LOAD_NO_CACHE = 2
        fun getDefaultUserAgent(ctx: Context): String =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148"
    }
}

open class WebView(@Suppress("UNUSED_PARAMETER") context: Context) : android.view.View() {
    internal val wk: WKWebView = WKWebView(frame = CGRectZero.readValue(), configuration = WKWebViewConfiguration().apply {
        websiteDataStore = WKWebsiteDataStore.defaultDataStore()
    })
    private val delegate = Delegate(this)

    init {
        wk.navigationDelegate = delegate
        wk.allowsBackForwardNavigationGestures = false
    }

    override val nativeView: UIView get() = wk
    override val interactive: Boolean get() = true

    val settings: WebSettings = WebSettings(this)
    var webViewClient: WebViewClient = WebViewClient()
    var webChromeClient: WebChromeClient? = null
    val url: String? get() = wk.URL?.absoluteString

    fun loadUrl(url: String) {
        val u = NSURL.URLWithString(url) ?: return
        wk.loadRequest(NSURLRequest.requestWithURL(u))
    }

    fun loadDataWithBaseURL(baseUrl: String?, data: String, mimeType: String?, encoding: String?, historyUrl: String?) {
        wk.loadHTMLString(data, baseUrl?.let { NSURL.URLWithString(it) })
    }

    fun loadData(data: String, mimeType: String?, encoding: String?) = loadDataWithBaseURL(null, data, mimeType, encoding, null)

    /** The callback gets the result as JSON text, as on Android ("null" for nothing). */
    fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
        wk.evaluateJavaScript(script) { result, _ -> resultCallback?.onReceiveValue(json(result)) }
    }

    fun addJavascriptInterface(obj: Any, name: String) {
        android.util.Log.w("KavWebView", "addJavascriptInterface($name) is not available on iOS")
    }

    internal fun addDocumentStartScript(source: String) {
        wk.configuration.userContentController.addUserScript(
            WKUserScript(source = source, injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart, forMainFrameOnly = false),
        )
    }

    fun stopLoading() = wk.stopLoading()
    fun reload() { wk.reload() }
    fun canGoBack(): Boolean = wk.canGoBack
    fun goBack() { wk.goBack() }
    fun destroy() { wk.stopLoading(); wk.navigationDelegate = null }
    fun onPause() {}
    fun onResume() {}

    fun setBackgroundColor(color: Int) {
        val c = UIColor.colorWithRed(((color shr 16) and 0xFF) / 255.0, ((color shr 8) and 0xFF) / 255.0, (color and 0xFF) / 255.0, ((color ushr 24) and 0xFF) / 255.0)
        wk.opaque = false
        wk.backgroundColor = c
        wk.scrollView.backgroundColor = c
    }

    private fun json(v: Any?): String = when (v) {
        null -> "null"
        is String -> quote(v)
        is NSNumber -> v.stringValue
        else -> NSJSONSerialization.dataWithJSONObject(v, NSJSONWritingFragmentsAllowed, null)
            ?.let { NSString.create(it, NSUTF8StringEncoding) as String? } ?: "null"
    }

    private fun quote(s: String): String = buildString {
        append('"')
        for (c in s) when (c) {
            '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u" + c.code.toString(16).padStart(4, '0')) else append(c)
        }
        append('"')
    }

    private class Delegate(private val web: WebView) : NSObject(), WKNavigationDelegateProtocol {
        private fun request(url: NSURL?, main: Boolean, method: String = "GET"): WebResourceRequest =
            Request(Uri.parse(url?.absoluteString ?: ""), main, method)

        override fun webView(webView: WKWebView, decidePolicyForNavigationAction: WKNavigationAction, decisionHandler: (WKNavigationActionPolicy) -> Unit) {
            val a = decidePolicyForNavigationAction
            val main = a.targetFrame?.mainFrame ?: true
            val keepOut = runCatching { web.webViewClient.shouldOverrideUrlLoading(web, request(a.request.URL, main, a.request.HTTPMethod ?: "GET")) }.getOrDefault(false)
            decisionHandler(if (keepOut) WKNavigationActionPolicy.WKNavigationActionPolicyCancel else WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
        }

        @ObjCSignatureOverride
        override fun webView(webView: WKWebView, didStartProvisionalNavigation: WKNavigation?) {
            web.webViewClient.onPageStarted(web, webView.URL?.absoluteString, null)
        }

        @ObjCSignatureOverride
        override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
            web.webViewClient.onPageFinished(web, webView.URL?.absoluteString)
        }

        @ObjCSignatureOverride
        override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) = failed(webView, withError)

        @ObjCSignatureOverride
        override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) = failed(webView, withError)

        private fun failed(webView: WKWebView, error: NSError) {
            if (error.code == -999L) return   // NSURLErrorCancelled: a navigation replaced by another, not a failure
            val url = (error.userInfo["NSErrorFailingURLKey"] as? NSURL) ?: webView.URL
            web.webViewClient.onReceivedError(web, request(url, true), WebResourceError(error.code.toInt(), error.localizedDescription))
        }
    }
}

class CookieManager private constructor() {
    fun setAcceptCookie(accept: Boolean) {}
    fun setAcceptThirdPartyCookies(view: WebView, accept: Boolean) {}
    fun flush() {}
    fun removeAllCookies(callback: ValueCallback<Boolean>?) {
        val store = WKWebsiteDataStore.defaultDataStore()
        store.removeDataOfTypes(WKWebsiteDataStore.allWebsiteDataTypes(), platform.Foundation.NSDate.distantPast) { callback?.onReceiveValue(true) }
    }
    companion object {
        private val one = CookieManager()
        fun getInstance(): CookieManager = one
    }
}
