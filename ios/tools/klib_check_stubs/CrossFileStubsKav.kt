// LOCAL-CHECK ONLY: declarations from MainActivity.kt and data/JourneyFile.kt (which need Compose, so
// klib_check.sh does not compile them) that the data layer calls. Signatures copied from upstream.
@file:Suppress("unused", "UNUSED_PARAMETER")
package uk.noammm.kav

import android.content.Context
import uk.noammm.kav.data.Net
import uk.noammm.kav.data.Source
import uk.noammm.kav.ui.Lang

object Loaded {
    val net: Net? get() = null
    fun store(net: Net) {}
    fun clear() {}
}

object Prefs {
    fun backupJson(ctx: Context): org.json.JSONObject = org.json.JSONObject()
    fun restoreBackup(ctx: Context, o: org.json.JSONObject) {}
    fun kavServerBundleKey(ctx: Context): String = ""
    fun setKavServerBundleKey(ctx: Context, key: String) {}
    fun kavServerKey(ctx: Context): String = ""
    fun ownServer(ctx: Context): String = ""
    fun timetableOnMobile(ctx: Context): Boolean = false
    fun timetableSource(ctx: Context): Source = Source.KAV
    fun timetableUpdates(ctx: Context): Boolean = true
    fun updatesOnMobile(ctx: Context): Boolean = false
    fun hopOnDevice(ctx: Context): String = ""
    fun lang(ctx: Context): Lang = Lang.entries.first()
}
