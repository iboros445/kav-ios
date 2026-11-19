//@cmp
// iOS override of android/app/src/main/java/uk/noammm/kav/TripService.kt @ 449cd8a. Keep public API identical.
//
// Android keeps a foreground service and an ongoing notification for a trip under way.
// On iOS, this updates the Live Activity / Dynamic Island bridge (LiveActivityBridge) and maintains trip state.
package uk.noammm.kav

import android.content.Context
import uk.noammm.kav.data.JourneyFile
import uk.noammm.kav.platform.TripActivityBridge
import uk.noammm.kav.ui.Fix
import uk.noammm.kav.ui.Mode

class TripService private constructor() {
    companion object {
        fun show(ctx: Context) {
            val (journey, step) = TripBridge.journey?.invoke() ?: JourneyFile.load(ctx) ?: run {
                TripActivityBridge.handler?.end()
                return
            }
            val fix = TripBridge.fixNow?.invoke()
            val now = System.currentTimeMillis() / 1000
            val notice = tripNotice(journey, step, fix, now, Prefs.accent(ctx))
            if (notice == null) {
                TripActivityBridge.handler?.end()
                return
            }

            val tripId = "${journey.trip.guid}:${journey.trip.dep}"
            val chip = notice.chip.ifEmpty {
                when (notice.glyph.mode) {
                    Mode.BUS -> "Bus"
                    Mode.TRAIN -> "Train"
                    Mode.SUBWAY -> "Metro"
                    Mode.TRAM -> "Tram"
                    else -> "Walk"
                }
            }
            val tintHex = "#" + (notice.tint and 0xFFFFFF).toString(16).padStart(6, '0')
            val mode = notice.glyph.mode?.name?.lowercase() ?: "walk"
            val progress = if (notice.max > 0) notice.progress.toDouble() / notice.max.toDouble() else 0.0

            TripActivityBridge.handler?.update(
                tripId = tripId,
                title = notice.title,
                subtitle = notice.text,
                chip = chip,
                tintHex = tintHex,
                modeName = mode,
                arrivalTime = notice.arrive,
                progress = progress,
                dueText = notice.dueText,
            )
        }

        fun repost() {
            show(android.content.KavApplicationContext)
        }

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

        fun stop(ctx: Context) {
            TripActivityBridge.handler?.end()
        }

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
