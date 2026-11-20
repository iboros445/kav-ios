// Android generates R from res/; Kotlin code only ever names these ids, so they are stand-ins. Raw resources are
// bundled by CI under assets/raw/ (copied from android/app/src/main/res/raw), and `files` names each id's file.
package uk.noammm.kav

object R {
    object drawable {
        const val ic_launcher_background = 1
        const val ic_launcher_foreground = 2
        const val ic_trip_notice = 3
    }

    object string { const val app_name = 10 }
    object mipmap { const val ic_launcher = 20 }

    object raw {
        const val moovit_share_schema = 101
        const val moovit_web_alerts = 102
        const val moovit_web_lines = 103
        const val moovit_web_request = 104
        const val notice = 105
        const val whats_new = 106

        internal val files: Map<Int, String> = mapOf(
            moovit_share_schema to "moovit_share_schema.json",
            moovit_web_alerts to "moovit_web_alerts.js",
            moovit_web_lines to "moovit_web_lines.js",
            moovit_web_request to "moovit_web_request.js",
            notice to "notice.json",
            whats_new to "whats_new.json",
        )
    }
}

// Android's generated BuildConfig. Kav's server key is only in Noam's release APKs, so this build has none: the
// iPhone build never defaults to Kav's server.
object BuildConfig {
    const val DEBUG = false
    const val APPLICATION_ID = "io.github.iboros445.kav"
    const val KAV_LIVE_KEY = ""
    val VERSION_NAME: String get() = android.content.pm.PackageManager.bundleVersionName()
    val VERSION_CODE: Int get() = android.content.pm.PackageManager.bundleVersionCode().toInt()
}
