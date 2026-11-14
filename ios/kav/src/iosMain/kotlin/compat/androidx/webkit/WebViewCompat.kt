//@cmp
package androidx.webkit

import android.webkit.WebView

object WebViewFeature {
    const val DOCUMENT_START_SCRIPT = "DOCUMENT_START_SCRIPT"
    fun isFeatureSupported(feature: String): Boolean = feature == DOCUMENT_START_SCRIPT
}

object WebViewCompat {
    /** A script that runs before the page's own, as a WKUserScript at document start (all origins; WKWebView has no allow-list). */
    fun addDocumentStartJavaScript(view: WebView, script: String, allowedOriginRules: Set<String>): Any {
        view.addDocumentStartScript(script)
        return Unit
    }
}
