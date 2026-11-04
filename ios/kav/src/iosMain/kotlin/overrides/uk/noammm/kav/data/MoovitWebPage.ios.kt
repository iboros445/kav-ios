// iOS override of android/app/src/main/java/uk/noammm/kav/data/MoovitWebPage.kt @ ede72bd. Keep public API identical.
//
// Android runs Moovit's logged-out website in a hidden WebView and asks it for plans, timetables and alerts. The
// iPhone build doesn't have that yet (it needs a WKWebView bridge), so every ask fails the way an unreachable site
// would, and the iPhone build defaults to Bus Nearby for planning and timetables (platform/Defaults.kt).
package uk.noammm.kav.data

import android.content.Context
import java.io.IOException
import org.json.JSONObject

internal object MoovitWebPage {
    private var context: Context? = null
    fun init(ctx: Context) { context = ctx.applicationContext }

    // Mobile Safari's user agent, which a WKWebView would send.
    fun agent(): String? = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148"

    internal fun allowed(host: String?, path: String): Boolean = when {
        host == "moovitapp.com" -> !path.startsWith("/api/") || path.startsWith("/api/route/") ||
            path == "/api/user" || path == "/api/user/all" || path.startsWith("/api/metro/") || path == "/api/feature-flags" ||
            path == "/api/lines/agency" || path == "/api/lines/group-trips" ||
            path == "/api/share/itinerary/get" || path == "/api/share/itinerary" ||
            path == "/api/alert/metro" || path == "/api/alert/details"
        host == "appassets.mvtdev.com" -> true
        host?.endsWith(".awswaf.com") == true -> true
        else -> false
    }

    private fun unavailable(): Nothing = throw IOException("Moovit's website isn't available on iPhone yet")

    fun plan(query: JSONObject): JSONObject = unavailable()
    fun lines(query: JSONObject): JSONObject = unavailable()
    fun alerts(query: JSONObject): JSONObject = unavailable()
}
