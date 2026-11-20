// iOS override of android/app/src/main/java/uk/noammm/kav/data/GoogleSession.kt @ 449cd8a. Keep public API identical.
//
// The same logged-out browser session with Google Maps, without OkHttp: requests go through HttpURLConnection (so the
// rider's proxy applies, RiderProxy.open) with keepCookies, which keeps Google's cookies in the system's cookie store.
// That store lasts across launches the way the Android jar's SharedPreferences copy does. The headers and the request
// budget are upstream's, unchanged.
package uk.noammm.kav.data

import android.content.Context
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSDate
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSHTTPCookieStorage
import platform.Foundation.NSURL
import platform.Foundation.timeIntervalSinceDate

internal object GoogleSession {
    private const val WARM_URL = "https://www.google.com/maps?hl=iw&gl=il"
    private const val REFERER = "https://www.google.com/maps"
    private const val LANGUAGES = "he-IL,he;q=0.9,en-US;q=0.8,en;q=0.7"
    private const val ACCEPT_DOCUMENT = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"

    @kotlin.concurrent.Volatile private var warmed = false

    fun init(ctx: Context) {}

    // Every request has a session of its own here, so there are no open connections to close.
    fun dropConnections() {}

    private fun hasCookie(name: String): Boolean {
        val url = NSURL.URLWithString("https://www.google.com/") ?: return false
        val now = NSDate()
        return NSHTTPCookieStorage.sharedHTTPCookieStorage.cookiesForURL(url).orEmpty().any {
            val c = it as? NSHTTPCookie
            c != null && c.name == name && (c.expiresDate?.let { e -> e.timeIntervalSinceDate(now) > 0 } ?: true)
        }
    }

    private fun request(url: String, document: Boolean): String? {
        val c = RiderProxy.open(java.net.URL(url)) as java.net.HttpURLConnection
        c.keepCookies = true
        c.connectTimeout = 8_000; c.readTimeout = 12_000
        headers(c, document)
        try {
            if (c.responseCode != 200) return null
            return c.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            c.disconnect()
        }
    }

    // Opens the Maps page once a launch, a first visit's way (no referrer), when Google's session cookie isn't kept.
    private fun warm() {
        if (warmed) return
        if (!hasCookie("NID")) runCatching { request(WARM_URL, document = true) }
        warmed = true
    }

    // A Maps data request, as the page's own script makes it. Null when Google answered with anything but 200.
    fun get(url: String): String? {
        warm()
        return request(url, document = false)
    }

    // Chrome's headers for its version in the user agent: the client hints have to name the same version.
    private fun headers(c: java.net.HttpURLConnection, document: Boolean) {
        val ua = GoogleRecipe.current.userAgent
        c.setRequestProperty("User-Agent", ua)
        c.setRequestProperty("Accept", if (document) ACCEPT_DOCUMENT else "*/*")
        c.setRequestProperty("Accept-Language", LANGUAGES)
        chromeMajor(ua)?.let { c.setRequestProperty("Sec-CH-UA", secChUaFor(it)) }
        c.setRequestProperty("Sec-CH-UA-Mobile", "?0")
        c.setRequestProperty("Sec-CH-UA-Platform", "\"Windows\"")
        c.setRequestProperty("Sec-Fetch-Dest", if (document) "document" else "empty")
        c.setRequestProperty("Sec-Fetch-Mode", if (document) "navigate" else "cors")
        c.setRequestProperty("Sec-Fetch-Site", if (document) "none" else "same-origin")
        if (document) c.setRequestProperty("Upgrade-Insecure-Requests", "1")
        else {
            // The two hints google.com asks for (Accept-CH), sent on every request after the page.
            c.setRequestProperty("Referer", REFERER)
            c.setRequestProperty("Downlink", "10")
            c.setRequestProperty("RTT", "50")
        }
    }

    // One budget for every Google request (alerts, how busy): 2.5 s apart, and none for half an hour after a limited
    // view or a refusal, which more requests would only prolong.
    private const val SPACING_MS = 2_500L
    private const val PAUSE_MS = 30 * 60_000L
    private val turn = kotlinx.coroutines.sync.Mutex()
    @kotlin.concurrent.Volatile private var lastAsk = 0L
    @kotlin.concurrent.Volatile private var pausedUntil = 0L

    val paused get() = System.currentTimeMillis() < pausedUntil

    // Runs one request in its turn. Throws when Google is resting, and starts the rest when the request fails.
    suspend fun <T> spaced(request: () -> T): T = turn.withLock {
        if (paused) throw java.io.IOException("Google is resting after a limited view")
        val gap = lastAsk + SPACING_MS - System.currentTimeMillis()
        if (gap > 0) kotlinx.coroutines.delay(gap)
        lastAsk = System.currentTimeMillis()
        try {
            request()
        } catch (e: java.io.IOException) {
            pausedUntil = System.currentTimeMillis() + PAUSE_MS
            throw e
        }
    }

    internal fun chromeMajor(ua: String): Int? = Regex("""Chrome/(\d+)\.""").find(ua)?.groupValues?.get(1)?.toIntOrNull()

    // The Sec-CH-UA desktop Chrome sends for a major version: Chrome derives its made-up brand, that brand's version and
    // the order of the three from the major alone (components/embedder_support/user_agent_utils.cc, as Vela ports it).
    internal fun secChUaFor(major: Int): String {
        val chars = listOf(" ", "(", ":", "-", ".", "/", ")", ";", "=", "?", "_")
        val greaseVersions = listOf("8", "99", "24")
        val list = listOf(
            "Not${chars[major % chars.size]}A${chars[(major + 1) % chars.size]}Brand" to greaseVersions[major % greaseVersions.size],
            "Chromium" to "$major",
            "Google Chrome" to "$major",
        )
        val orders = listOf(listOf(0, 1, 2), listOf(0, 2, 1), listOf(1, 0, 2), listOf(1, 2, 0), listOf(2, 0, 1), listOf(2, 1, 0))
        val order = orders[major % orders.size]
        val out = arrayOfNulls<Pair<String, String>>(3)
        for (i in list.indices) out[order[i]] = list[i]
        return out.joinToString(", ") { "\"${it!!.first}\";v=\"${it.second}\"" }
    }
}
