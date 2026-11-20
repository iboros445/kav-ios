// iOS override of android/app/src/main/java/uk/noammm/kav/Speaker.kt @ 449cd8a. Keep public API identical.
//
// Trip alerts said out loud with AVSpeechSynthesizer, in Kav's language, ducking other audio like a navigation app.
// iOS doesn't tell an app the ringer switch's position, so mayTalk is always true (the voice obeys the silent switch
// through the audio session's category, which mixes with others and ducks them). In English, a name in Hebrew letters
// is read by the Hebrew voice between the English words, as on Android (spokenRuns).
package uk.noammm.kav

import android.content.Context
import java.util.Locale
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDuckOthers
import platform.AVFAudio.AVAudioSessionCategoryOptionInterruptSpokenAudioAndMixWithOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeVoicePrompt
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.setActive
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time
import uk.noammm.kav.ui.Lang
import uk.noammm.kav.ui.T

internal object Speaker {
    private var synth: AVSpeechSynthesizer? = null
    private var waiting = 0

    // onSilent: told when nothing can be said, the phone having no voice for Kav's language.
    fun say(ctx: Context, text: String, onSilent: (() -> Unit)? = null) {
        val main = if (T.lang == Lang.HE) HEBREW else Locale.UK
        if (voice(main) == null) { onSilent?.invoke(); return }
        val said = text.replace('/', ',').replace(Regex("[⁦-⁩]"), "")
        val parts = if (main == HEBREW) listOf(said to main) else spokenRuns(said)
        val s = synth ?: AVSpeechSynthesizer().also { synth = it }
        runCatching {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, AVAudioSessionModeVoicePrompt,
                AVAudioSessionCategoryOptionDuckOthers or AVAudioSessionCategoryOptionInterruptSpokenAudioAndMixWithOthers, null)
            session.setActive(true, null)
        }
        s.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        // Utterances queue in order; a run with no voice on the phone (Hebrew, in English) is left out.
        for ((part, locale) in parts) {
            val v = voice(locale) ?: continue
            s.speakUtterance(AVSpeechUtterance(string = part).also { it.voice = v })
        }
    }

    // Android waits for the notification's own sound to start and end before speaking. iOS doesn't let an app see
    // other playback, so the words wait the time a notification sound takes; a later alert's words replace waiting ones.
    fun sayAfterSound(ctx: Context, text: String) {
        val turn = ++waiting
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, SOUND_MS * 1_000_000), dispatch_get_main_queue()) {
            if (turn == waiting) say(ctx, text)
        }
    }

    // The synthesizer made ahead of the first alert, so it isn't what the first thing said waits for.
    fun warm(ctx: Context) { if (synth == null) synth = AVSpeechSynthesizer() }

    fun mayTalk(ctx: Context): Boolean = true

    fun shutdown() {
        waiting++
        synth?.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        synth = null
    }

    private fun voice(l: Locale): AVSpeechSynthesisVoice? = AVSpeechSynthesisVoice.voiceWithLanguage(l.toLanguageTag())

    private const val SOUND_MS = 1_500L
}

// The text in runs of Hebrew letters and the rest, each with the voice that reads it.
internal fun spokenRuns(text: String): List<Pair<String, Locale>> =
    Regex("[֐-׿][֐-׿0-9'\"׳״\\- ]*[֐-׿0-9'\"׳״]|[֐-׿]|[^֐-׿]+")
        .findAll(text).map { it.value }.filter { it.isNotBlank() }
        .map { it.trim() to if (it.any { c -> c in '֐'..'׿' }) HEBREW else Locale.UK }.toList()

private val HEBREW = Locale.forLanguageTag("he-IL")
