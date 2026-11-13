// android.webkit.HttpAuthHandler, which WebViewClient.onReceivedHttpAuthRequest hands over (WebView.kt) and
// RiderProxy.answer fills in. WKWebView never asks on iOS (pages don't go through the rider's proxy).
package android.webkit

open class HttpAuthHandler internal constructor() {
    open fun proceed(username: String?, password: String?) {}
    open fun cancel() {}
    open fun useHttpAuthUsernamePassword(): Boolean = false
}
