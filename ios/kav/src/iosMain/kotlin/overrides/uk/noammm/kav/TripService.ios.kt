//@cmp
// iOS override of android/app/src/main/java/uk/noammm/kav/TripService.kt @ 449cd8a. Keep public API identical.
//
// Android keeps a foreground service and an ongoing notification for a trip under way. iOS has neither; Phase 4 of
// ios/MIGRATION_PLAN.md adds background location and local notifications behind these same entry points. Until then
// they do nothing, and the trip is followed while the app is open.
package uk.noammm.kav

import android.content.Context
import uk.noammm.kav.data.JourneyFile
import uk.noammm.kav.ui.Fix

class TripService private constructor() {
    companion object {
        fun show(ctx: Context) {}

        fun repost() {}

        // A ride paid from the notification or the app's square goes on its trip leg's card: in the app's trip when it
        // is open, else in the saved one.
        fun notePaid(ctx: Context, key: String, leg: Int?, trip: String?) {
            if (leg == null || trip == null) return
            TripBridge.journey?.let { current ->
                if (current()?.first?.paymentKey == trip) TripBridge.paid?.invoke(leg, key)
                return
            }
            val (journey, step) = JourneyFile.load(ctx) ?: return
            if (journey.paymentKey != trip) return
            JourneyFile.save(ctx, journey.copy(paid = journey.paid + (leg to key)), step)
        }

        fun stop(ctx: Context) {}

        fun alert(ctx: Context, title: String, text: String) {}
    }
}

object TripBridge {
    @Volatile var end: (() -> Unit)? = null
    @Volatile var journey: (() -> Pair<ActiveJourney, Int>?)? = null
    @Volatile var fix: ((Fix) -> Unit)? = null
    @Volatile var fixNow: (() -> Fix?)? = null
    @Volatile var paid: ((leg: Int, key: String) -> Unit)? = null
}
