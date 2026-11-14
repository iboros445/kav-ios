//@cmp
// iOS override of android/app/src/main/java/uk/noammm/kav/LineAlerts.kt @ 449cd8a. Keep public API identical.
//
// Android keeps a line alert's notification up to date every minute from an alarm that wakes the app. An iPhone app
// can't be woken on a schedule without push or a background-task entitlement, so line alerts don't run on iOS yet.
package uk.noammm.kav

import android.content.Context

class LineAlertReceiver

object LineAlerts {
    const val ACT_WAKE = "uk.noammm.kav.LINE_ALERTS"
    const val ACT_QUIET = "uk.noammm.kav.LINE_ALERT_QUIET"
    const val EXTRA_ID = "alert"
    const val EXTRA_UNTIL = "until"
    const val EXTRA_STOP = "uk.noammm.kav.STOP"

    fun reschedule(ctx: Context) {}

    fun quiet(ctx: Context, id: Long, until: Long) {
        if (System.currentTimeMillis() / 1000 < until) Prefs.quietAlert(ctx, id, until)
    }

    fun run(ctx: Context) {}
}
