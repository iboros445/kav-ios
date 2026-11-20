// What Context.startActivity does on iOS: open a URL, show the share sheet, open this app's Settings page.
package android.content

import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UINavigationController
import platform.UIKit.UIViewController
import platform.UIKit.UIWindowScene

internal object IntentRouter {
    fun topViewController(): UIViewController? {
        val scene = UIApplication.sharedApplication.connectedScenes.firstOrNull { it is UIWindowScene } as? UIWindowScene
        var vc = scene?.keyWindow?.rootViewController ?: return null
        while (true) vc = vc.presentedViewController ?: break
        return vc
    }

    fun start(intent: Intent) {
        when {
            intent.action == "chooser" -> intent.target?.let { start(it) }
            intent.action == Intent.ACTION_SEND -> {
                val text = intent.extras[Intent.EXTRA_TEXT] as? String ?: return
                val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
                topViewController()?.presentViewController(sheet, animated = true, completion = null)
            }
            intent.action == android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS ->
                open(UIApplicationOpenSettingsURLString)
            intent.action == Intent.ACTION_VIEW -> open(intent.data?.toString() ?: return)
            else -> throw ActivityNotFoundException("No handler for ${intent.action}")
        }
    }

    private fun open(url: String) {
        val u = NSURL.URLWithString(url) ?: throw ActivityNotFoundException(url)
        UIApplication.sharedApplication.openURL(u, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}
