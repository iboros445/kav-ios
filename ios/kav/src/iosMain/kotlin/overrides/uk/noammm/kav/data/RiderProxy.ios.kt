// iOS override of android/app/src/main/java/uk/noammm/kav/data/RiderProxy.kt @ 449cd8a. Keep public API identical.
//
// The rider's own proxy. Android installs a ProxySelector and an Authenticator for the whole process; iOS has neither,
// so every connection Kav opens through open() carries the proxy itself (NSURLSession's proxy dictionary, see
// compat/java/net/HttpURLConnection.kt) and its login. The pages Kav shows (WKWebView) don't follow it, as Settings
// says (pagesFollow). OkHttp isn't on iOS, so there's no okHttp authenticator: GoogleSession and GovMapTiles go
// through open() too.
package uk.noammm.kav.data

import android.content.Context
import android.webkit.HttpAuthHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.InetSocketAddress
import java.net.Proxy

object RiderProxy {
    enum class Kind(val key: String) { OFF("off"), HTTP("http"), SOCKS("socks") }

    data class Setting(val kind: Kind, val host: String, val port: Int, val user: String, val password: String) {
        val on get() = kind != Kind.OFF && host.isNotBlank() && port in 1..65535
    }

    private val OFF = Setting(Kind.OFF, "", 0, "", "")

    var setting by mutableStateOf(OFF)
        private set

    private var installed = false

    private fun store(ctx: Context) = ctx.applicationContext.getSharedPreferences("proxy", Context.MODE_PRIVATE)

    fun load(ctx: Context): Setting {
        val p = store(ctx)
        return Setting(
            Kind.entries.firstOrNull { it.key == p.getString("kind", null) } ?: Kind.OFF,
            p.getString("host", "").orEmpty(), p.getInt("port", 0), p.getString("user", "").orEmpty(), p.getString("password", "").orEmpty(),
        )
    }

    fun install(ctx: Context) {
        if (installed) return
        installed = true
        setting = load(ctx)
    }

    fun set(ctx: Context, s: Setting) {
        store(ctx).edit().putString("kind", s.kind.key).putString("host", s.host.trim()).putInt("port", s.port)
            .putString("user", s.user).putString("password", s.password).apply()
        setting = s.copy(host = s.host.trim())
        // Each request has its own session on iOS, so the next one already takes the new way.
        GoogleSession.dropConnections()
        GovMapTiles.dropConnections()
    }

    private fun local(host: String?): Boolean {
        val h = host?.lowercase()?.trim('[', ']') ?: return true
        if (h == "localhost" || h.endsWith(".local") || h == "::1") return true
        val n = h.split('.').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 4 } ?: return false
        return n[0] == 127 || n[0] == 10 || (n[0] == 192 && n[1] == 168) || (n[0] == 172 && n[1] in 16..31) || (n[0] == 169 && n[1] == 254)
    }

    // WKWebView never asks for a proxy's login here (pages don't use the proxy on iOS).
    fun answer(handler: HttpAuthHandler, host: String?): Boolean {
        val s = setting
        if (!s.on || s.user.isEmpty() || !host.equals(s.host, ignoreCase = true)) return false
        handler.proceed(s.user, s.password)
        return true
    }

    // WKWebsiteDataStore can take a proxy only from iOS 17, and not one with a login: Kav's pages go direct.
    val pagesFollow get() = false

    // A connection for url, through the rider's proxy when there is one, and only through it (no direct fallback).
    fun open(url: java.net.URL): java.net.URLConnection {
        val s = setting
        if (!s.on || local(url.host)) return url.openConnection()
        val type = if (s.kind == Kind.SOCKS) Proxy.Type.SOCKS else Proxy.Type.HTTP
        val c = url.openConnection(Proxy(type, InetSocketAddress.createUnresolved(s.host, s.port)))
        if (c is java.net.HttpURLConnection && s.user.isNotEmpty()) c.proxyLogin = s.user to s.password
        return c
    }

    // Whether the proxy carries a request now: Kav's website asked through it. Null when it does, else why not.
    fun check(): String? = try {
        val c = open(java.net.URL("https://kavapp.link/")) as java.net.HttpURLConnection
        c.connectTimeout = 8_000; c.readTimeout = 8_000
        c.setRequestProperty("User-Agent", "Kav")
        try {
            val code = c.responseCode
            if (code in 200..399) null else "HTTP $code"
        } finally {
            c.disconnect()
        }
    } catch (e: Exception) {
        e.message ?: e::class.simpleName
    }
}
