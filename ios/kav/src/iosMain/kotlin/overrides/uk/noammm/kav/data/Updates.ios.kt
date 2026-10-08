// iOS override of android/app/src/main/java/uk/noammm/kav/data/Updates.kt @ e945f0b. Keep public API identical.
//
// Android downloads an APK from the upstream releases and hands it to the system installer. On iOS an app cannot
// install itself: updates arrive through the SideStore source (see ios/INSTALL.md). Until Phase 5 wires that up,
// `latest()` reports the newest `ios-v*` release of this fork and `install` is not offered (`canInstall` is false).
package uk.noammm.kav.data

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import platform.Foundation.NSBundle
import uk.noammm.kav.ui.T

object Updates {
    // MapFile builds the map download URL from these, so they stay pointed at the upstream project.
    const val OWNER = "ImNoammm"
    const val REPO = "kav"
    const val PAGE = "https://github.com/$OWNER/$REPO/releases"

    // The iOS builds are released from the fork.
    const val IOS_OWNER = "iboros445"
    const val IOS_REPO = "kav-ios"
    const val IOS_PAGE = "https://github.com/$IOS_OWNER/$IOS_REPO/releases"

    class Release(
        val version: String,
        val name: String,
        val notes: String,
        val apkUrl: String?,
        val apkBytes: Long,
        val pageUrl: String,
    )

    fun installedVersion(ctx: Context): String =
        (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String) ?: "0"

    fun latest(): Release {
        val c = (URL("https://api.github.com/repos/$IOS_OWNER/$IOS_REPO/releases/latest").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Kav")
        }
        val code = c.responseCode
        // No tagged ios-v* release yet (development builds come through the SideStore source): nothing to offer.
        if (code == 404) return Release(version = "0", name = "", notes = "", apkUrl = null, apkBytes = -1L, pageUrl = IOS_PAGE)
        if (code != 200) throw RuntimeException("GitHub HTTP $code")
        val o = JSONObject(c.inputStream.use { String(it.readBytes(), Charsets.UTF_8) })
        val assets = o.optJSONArray("assets")
        var ipa: JSONObject? = null
        for (i in 0 until (assets?.length() ?: 0)) {
            val a = assets!!.optJSONObject(i) ?: continue
            if (a.optString("name").endsWith(".ipa", ignoreCase = true)) { ipa = a; break }
        }
        return Release(
            version = o.optString("tag_name").removePrefix("ios-v").removePrefix("v").removePrefix("V"),
            name = o.optString("name"),
            notes = o.optString("body"),
            apkUrl = ipa?.optString("browser_download_url")?.takeIf { it.isNotBlank() },
            apkBytes = ipa?.optLong("size") ?: -1L,
            pageUrl = o.optString("html_url").ifBlank { IOS_PAGE },
        )
    }

    fun isNewer(remote: String, installed: String): Boolean {
        val a = segments(remote); val b = segments(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun segments(v: String): List<Int> =
        Regex("\\d+").findAll(v).map { it.value.toIntOrNull() ?: 0 }.toList()

    private fun dir(ctx: Context) = File(ctx.cacheDir, "updates").apply { mkdirs() }

    fun download(ctx: Context, release: Release, onProgress: (Long, Long) -> Unit): File {
        val url = release.apkUrl ?: throw RuntimeException(T("This release has no IPA attached", "לגרסה הזו לא מצורף קובץ IPA"))
        val file = File(dir(ctx), "kav-${release.version}.ipa")
        if (file.exists() && release.apkBytes > 0 && file.length() == release.apkBytes) {
            onProgress(file.length(), file.length()); return file
        }
        dir(ctx).listFiles()?.forEach { if (it != file) it.delete() }
        val part = File(file.path + ".part")
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "Kav")
        if (c.responseCode != 200) throw RuntimeException(T("Download HTTP ${c.responseCode}", "הורדה נכשלה, שגיאת HTTP ${c.responseCode}"))
        val total = if (release.apkBytes > 0) release.apkBytes else c.contentLengthLong
        c.inputStream.use { input ->
            part.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    onProgress(done, total)
                }
            }
        }
        if (!part.renameTo(file)) throw RuntimeException(T("Could not keep the download", "לא ניתן היה לשמור את ההורדה"))
        return file
    }

    // iOS cannot install a downloaded IPA. SideStore does that (Phase 5 opens it from here).
    fun canInstall(ctx: Context): Boolean = false

    fun askInstallPermission(ctx: Context) {}

    fun install(ctx: Context, file: File) {}
}
