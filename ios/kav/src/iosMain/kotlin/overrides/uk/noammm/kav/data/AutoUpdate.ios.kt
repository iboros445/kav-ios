// iOS override of android/app/src/main/java/uk/noammm/kav/data/AutoUpdate.kt @ ede72bd. Keep public API identical.
//
// Android downloads the next APK and installs it with PackageInstaller. An iPhone app can't install itself: SideStore
// updates Kav from its source. So nothing is ever prepared or ready here; the "updated to" notice still works.
package uk.noammm.kav.data

import android.content.Context
import java.io.File
import org.json.JSONObject

internal object AutoUpdate {
    class Notice(val from: String, val to: String, val name: String, val notes: String, val at: Long) {
        fun release() = Updates.Release(to, name, notes, null, -1, Updates.PAGE)
    }

    private const val WEEK_MS = 7 * 24 * 3600_000L

    private fun file(ctx: Context) = File(ctx.filesDir, "updated.json")

    private fun state(ctx: Context): Notice? = runCatching {
        val f = file(ctx)
        val o = JSONObject(f.readText())
        Notice(o.optString("from"), o.optString("to"), o.optString("name"), o.optString("notes"), f.lastModified())
    }.getOrNull()

    fun prepare(ctx: Context, release: Updates.Release) {}

    fun ready(ctx: Context): Notice? = null

    fun installNow(ctx: Context): Boolean = false

    fun notice(ctx: Context): Notice? {
        val n = state(ctx) ?: return null
        if (!shows(n.to, Updates.installedVersion(ctx))) {
            if (System.currentTimeMillis() - n.at > WEEK_MS) file(ctx).delete()
            return null
        }
        return n
    }

    fun noticeShown(ctx: Context) {
        file(ctx).delete()
        File(ctx.cacheDir, "updates").listFiles()?.forEach { it.delete() }
    }

    internal fun shows(to: String, installed: String) = to.isNotEmpty() && !Updates.isNewer(to, installed)

    internal fun sameSigners(have: Set<String>, got: Set<String>) = have.isNotEmpty() && have == got
}
