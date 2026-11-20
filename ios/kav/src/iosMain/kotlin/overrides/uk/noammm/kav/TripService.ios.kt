//@cmp
// iOS override of android/app/src/main/java/uk/noammm/kav/TripService.kt @ 449cd8a. Keep public API identical.
//
// Android keeps a foreground service and an ongoing notification for a trip under way.
// On iOS, this updates the Live Activity / Dynamic Island bridge (LiveActivityBridge) and maintains trip state.
package uk.noammm.kav

import android.content.Context
import android.util.Log
import uk.noammm.kav.data.JourneyFile
import uk.noammm.kav.data.Transit
import uk.noammm.kav.platform.TripActivityBridge
import uk.noammm.kav.ui.Fix
import uk.noammm.kav.ui.Mode
import uk.noammm.kav.ui.Step
import uk.noammm.kav.ui.T
import uk.noammm.kav.ui.boardingChoice
import uk.noammm.kav.ui.buildSteps
import uk.noammm.kav.ui.clockFormat
import uk.noammm.kav.ui.legMode
import uk.noammm.kav.ui.modeName
import java.util.Date

class TripService private constructor() {
    companion object {
        private fun currentJourney(ctx: Context): Pair<ActiveJourney, Int>? {
            TripBridge.journey?.invoke()?.let { return it }
            val (saved, step) = JourneyFile.load(ctx) ?: return null
            val now = System.currentTimeMillis() / 1000
            // If the saved journey has expired past 3 hours, clean it up
            if (now > saved.trip.arr + JourneyFile.KEEP_S) {
                JourneyFile.clear(ctx)
                return null
            }
            return saved to step
        }

        fun show(ctx: Context) {
            val (journey, step) = currentJourney(ctx) ?: run {
                Log.i("Kav", "TripService: show called with no active journey")
                TripActivityBridge.handler?.end()
                return
            }
            val fix = TripBridge.fixNow?.invoke()
            val now = System.currentTimeMillis() / 1000

            val notice = tripNotice(journey, step, fix, now, Prefs.accent(ctx))
            if (notice == null) {
                Log.i("Kav", "TripService: tripNotice is null, ending activity")
                TripActivityBridge.handler?.end()
                return
            }

            val tripId = "${journey.trip.guid}:${journey.trip.dep}"
            val steps = buildSteps(journey.trip, journey.fromLabel, journey.toLabel)
            val currentStep = steps.getOrNull(step.coerceIn(0, steps.lastIndex))

            // 1. Line / Route number (e.g. "480", "1") for the route chip badge
            val routeNumber = when (currentStep) {
                is Step.Wait -> {
                    val ride = boardingChoice(currentStep.ride, currentStep.wait, journey.chosen[currentStep.legIndex] ?: 0).first
                    ride.shortName.ifBlank { journey.resolved.line(ride.lineId)?.number.orEmpty() }
                }
                is Step.Ride -> {
                    val ride = boardingChoice(currentStep.ride, currentStep.wait, journey.chosen[currentStep.legIndex] ?: 0).first
                    ride.shortName.ifBlank { journey.resolved.line(ride.lineId)?.number.orEmpty() }
                }
                is Step.Walk -> {
                    currentStep.toRide?.let {
                        val ride = boardingChoice(it, currentStep.toWait, journey.chosen[currentStep.toLeg] ?: 0).first
                        ride.shortName.ifBlank { journey.resolved.line(ride.lineId)?.number.orEmpty() }
                    }.orEmpty()
                }
                is Step.Start -> {
                    currentStep.board?.let {
                        val ride = boardingChoice(it, currentStep.boardWait, journey.chosen[currentStep.boardLeg] ?: 0).first
                        ride.shortName.ifBlank { journey.resolved.line(ride.lineId)?.number.orEmpty() }
                    }.orEmpty()
                }
                else -> ""
            }

            val chip = routeNumber.ifEmpty {
                notice.chip.takeIf { it.isNotBlank() && !it.equals("now", ignoreCase = true) && !it.equals("עכשיו", ignoreCase = true) }
                    ?: when (notice.glyph.mode) {
                        Mode.BUS -> "Bus"
                        Mode.TRAIN -> "Train"
                        Mode.SUBWAY -> "Metro"
                        Mode.TRAM -> "Tram"
                        else -> "Walk"
                    }
            }

            val hm = clockFormat()
            var liveDueText = notice.dueText
            var updatedTitle = notice.title

            // 2. Accurate real-time departure countdown (resolves "now on a bus that should come in 9 minutes")
            if (currentStep is Step.Wait) {
                val (ride, wait) = boardingChoice(currentStep.ride, currentStep.wait, journey.chosen[currentStep.legIndex] ?: 0)
                val deps = journey.resolved.departures(ride, wait, now)
                val upcoming = deps.firstOrNull { it.timeUtc >= now - 60 && it.state != Transit.TimeState.CANCELED }
                    ?: deps.firstOrNull()
                if (upcoming != null) {
                    val diffMins = ((upcoming.timeUtc - now) / 60).toInt()
                    liveDueText = when {
                        diffMins < 0 -> hm.format(Date(upcoming.timeUtc * 1000))
                        diffMins == 0 -> T("now", "עכשיו")
                        diffMins < 60 -> T("in $diffMins min", "בעוד $diffMins דק׳")
                        else -> hm.format(Date(upcoming.timeUtc * 1000))
                    }
                    val lName = ride.shortName.ifBlank { journey.resolved.line(ride.lineId)?.number.orEmpty() }.let {
                        val mName = modeName(legMode(ride, journey.resolved))
                        if (it.isBlank()) mName else "$mName $it"
                    }
                    updatedTitle = "$lName · $liveDueText"
                }
            } else if (currentStep is Step.Walk && currentStep.toRide != null) {
                val (ride, wait) = boardingChoice(currentStep.toRide, currentStep.toWait, journey.chosen[currentStep.toLeg] ?: 0)
                val deps = journey.resolved.departures(ride, wait, now)
                val upcoming = deps.firstOrNull { it.timeUtc >= now - 60 && it.state != Transit.TimeState.CANCELED }
                if (upcoming != null) {
                    val diffMins = ((upcoming.timeUtc - now) / 60).toInt()
                    if (diffMins in 0..59) {
                        liveDueText = if (diffMins == 0) T("now", "עכשיו") else T("in $diffMins min", "בעוד $diffMins דק׳")
                    }
                }
            }

            val tintHex = "#" + (notice.tint and 0xFFFFFF).toString(16).padStart(6, '0')
            val mode = notice.glyph.mode?.name?.lowercase() ?: "walk"
            val progress = if (notice.max > 0) notice.progress.toDouble() / notice.max.toDouble() else 0.0
            val cleanArrival = notice.arrive.replace(Regex("^(Arrive |הגעה ב[- ]?)", RegexOption.IGNORE_CASE), "").trim()

            Log.i("Kav", "TripService: show trip=$tripId title='$updatedTitle' chip='$chip' time='$cleanArrival' due='$liveDueText' mode=$mode")

            TripActivityBridge.handler?.update(
                tripId = tripId,
                title = updatedTitle,
                subtitle = notice.text,
                chip = chip,
                tintHex = tintHex,
                modeName = mode,
                arrivalTime = cleanArrival,
                progress = progress,
                dueText = liveDueText,
            )
        }

        fun repost() {
            show(android.content.KavApplicationContext)
        }

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
            Log.i("Kav", "TripService: stop called")
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
