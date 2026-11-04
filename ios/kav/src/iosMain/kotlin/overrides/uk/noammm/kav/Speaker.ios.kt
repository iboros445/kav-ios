// iOS override of android/app/src/main/java/uk/noammm/kav/Speaker.kt @ ede72bd. Keep public API identical.
//
// Trip alerts said out loud with AVSpeechSynthesizer, in Kav's language, ducking other audio like a navigation app.
// iOS doesn't tell an app the ringer switch's position, so mayTalk is always true (the voice obeys the silent switch
// through the audio session's category, which mixes with others and ducks them).
package uk.noammm.kav

import android.content.Context
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
import uk.noammm.kav.ui.Lang
import uk.noammm.kav.ui.T

internal object Speaker {
    private var synth: AVSpeechSynthesizer? = null

    fun say(ctx: Context, text: String) {
        val voice = AVSpeechSynthesisVoice.voiceWithLanguage(if (T.lang == Lang.HE) "he-IL" else "en-GB") ?: return
        val s = synth ?: AVSpeechSynthesizer().also { synth = it }
        runCatching {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, AVAudioSessionModeVoicePrompt,
                AVAudioSessionCategoryOptionDuckOthers or AVAudioSessionCategoryOptionInterruptSpokenAudioAndMixWithOthers, null)
            session.setActive(true, null)
        }
        s.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        s.speakUtterance(AVSpeechUtterance(string = text.replace('/', ',')).also { it.voice = voice })
    }

    fun mayTalk(ctx: Context): Boolean = true

    fun shutdown() {
        synth?.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        synth = null
    }
}
