package com.aiwa.store
import android.content.Context
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.aiwa.bridge.endsWithCancelPhrase

private val STOP_PHRASE_REGEX = Regex("""\bc['\s]?est\s+bon,?\s+vas[\s-]?y\b[!.\s]*$""", RegexOption.IGNORE_CASE)

// The same words anywhere in what was heard: the answer to a question ("Dépôt X, tu confirmes ?") is just that phrase.
private val STOP_PHRASE_ANYWHERE = Regex("""\bc['\s]?est\s+bon,?\s+vas[\s-]?y\b""", RegexOption.IGNORE_CASE)

fun containsStopPhrase(text: String): Boolean = STOP_PHRASE_ANYWHERE.containsMatchIn(text)

private fun stripStopPhrase(text: String): String? {
    val match = STOP_PHRASE_REGEX.find(text) ?: return null
    return text.substring(0, match.range.first).trim()
}

/**
 * Reported live, twice, insistently: "le micro enregistre et envoie
 * lorsqu'il entend 'c'est bon vas-y'" — NOT as soon as the speaker
 * pauses to think, which is what the previous implementation actually
 * did: Android's one-shot RecognizerIntent (ACTION_RECOGNIZE_SPEECH via
 * a launched Activity) returns its result the moment ITS OWN internal
 * silence-detection decides you've stopped talking — there is no way
 * to tell that API "keep listening, I'm not done". This wraps the raw
 * SpeechRecognizer API instead: every time a listening pass ends
 * (silence, timeout, or a real result with no stop phrase in it), it
 * just restarts listening and keeps accumulating, until a pass's own
 * text actually contains the stop phrase.
 *
 * HONEST LIMIT: raw SpeechRecognizer has no default visible dialog the
 * way the one-shot RecognizerIntent Activity did (that dialog is
 * Google's own app's UI, not something this API provides) — a caller
 * MUST show its own "still listening" indicator, or the user has no
 * way to know whether they're still being heard between passes.
 */
class StopPhraseListener(
    context: Context,
    private val onFinalText: (String) -> Unit,
    private val onPartial: (String) -> Unit,
    private val onGiveUp: () -> Unit,
    // "Non, c'est pas bon, arrête" (or "annule") at the end of what was heard: everything is dropped
    private val onCancelPhrase: () -> Unit = {},
) : RecognitionListener {
    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    private val quiet = QuietSounds(context)
    private fun release() { recognizer.destroy(); quiet.off() }
    private val accumulated = StringBuilder()
    private var consecutiveSilentPasses = 0
    private var stopped = false

    init {
        recognizer.setRecognitionListener(this)
    }

    fun start() {
        if (stopped) return
        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        quiet.on()
        recognizer.startListening(intent)
    }

    fun cancel() {
        if (stopped) return
        stopped = true
        release()
    }

    private fun cancelledByVoice() {
        stopped = true
        release()
        onCancelPhrase()
    }

    private fun finishWith(text: String) {
        stopped = true
        release()
        if (text.isNotBlank()) onFinalText(text) else onGiveUp()
    }

    override fun onResults(results: Bundle) {
        if (stopped) return
        val heard = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        if (endsWithCancelPhrase(heard)) { cancelledByVoice(); return }
        val beforeStopPhrase = stripStopPhrase(heard)
        if (beforeStopPhrase != null) {
            if (accumulated.isNotEmpty() && beforeStopPhrase.isNotEmpty()) accumulated.append(" ")
            accumulated.append(beforeStopPhrase)
            finishWith(accumulated.toString().trim())
            return
        }
        if (heard.isNotBlank()) {
            consecutiveSilentPasses = 0
            if (accumulated.isNotEmpty()) accumulated.append(" ")
            accumulated.append(heard)
            onPartial(accumulated.toString())
        } else {
            consecutiveSilentPasses++
        }
        // Three fully silent passes in a row (no speech at all, not just
        // "no stop phrase yet") means the user walked away or gave up —
        // without this, a listener that's never told the stop phrase
        // would otherwise restart itself forever.
        if (consecutiveSilentPasses >= 3) {
            finishWith(accumulated.toString().trim())
        } else {
            start()
        }
    }

    override fun onPartialResults(partialResults: Bundle) {
        if (stopped) return
        val partial = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        val combined = (accumulated.toString() + " " + partial).trim()
        if (endsWithCancelPhrase(partial)) { cancelledByVoice(); return }
        onPartial(combined)
    }

    override fun onError(error: Int) {
        if (stopped) return
        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                consecutiveSilentPasses++
                if (consecutiveSilentPasses >= 3) finishWith(accumulated.toString().trim()) else start()
            }
            else -> finishWith(accumulated.toString().trim())
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}

/**
 * Listens for one answer to a question the phone just asked aloud: true if "c'est bon vas-y" was said, false for anything else or
 * for silence (a second pass is given once to a silence). The question is never taken as answered by default: nothing changes
 * unless the phrase was heard.
 */
class ConfirmListener(context: Context, private val onAnswer: (Boolean) -> Unit) : RecognitionListener {
    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    private val quiet = QuietSounds(context)
    private fun release() { recognizer.destroy(); quiet.off() }
    private var answered = false
    private var silences = 0

    init {
        recognizer.setRecognitionListener(this)
    }

    fun start() {
        if (answered) return
        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        quiet.on()
        recognizer.startListening(intent)
    }

    fun cancel() {
        if (answered) return
        answered = true
        release()
    }

    private fun answer(yes: Boolean) {
        if (answered) return
        answered = true
        release()
        onAnswer(yes)
    }

    override fun onResults(results: Bundle) {
        val heard = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        if (containsStopPhrase(heard)) answer(true) else if (heard.isBlank() && silences++ < 1) start() else answer(false)
    }

    override fun onPartialResults(partialResults: Bundle) {
        val heard = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        if (endsWithCancelPhrase(heard)) answer(false) else if (containsStopPhrase(heard)) answer(true)
    }

    override fun onError(error: Int) {
        if ((error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) && silences++ < 1) start() else answer(false)
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}

/**
 * The beeps Android's recognizer plays when it starts and stops listening (and again at each restart of a pass): muted while a listener is
 * alive, then given back. Only the streams that were not muted already are touched, and only if the system allows it (a refusal changes nothing).
 * Nothing is spoken while a listener runs, so the voice (Speaker) is never cut. Music playing on the phone is silent for those seconds.
 */
class QuietSounds(context: Context) {
    private val audio = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    private val muted = mutableListOf<Int>()
    private var held = false

    fun on() {
        if (held) return
        held = true
        for (stream in listOf(android.media.AudioManager.STREAM_NOTIFICATION, android.media.AudioManager.STREAM_SYSTEM, android.media.AudioManager.STREAM_MUSIC)) {
            try {
                if (!audio.isStreamMute(stream)) {
                    audio.adjustStreamVolume(stream, android.media.AudioManager.ADJUST_MUTE, 0)
                    muted.add(stream)
                }
            } catch (err: Exception) { /* not allowed here (Do Not Disturb): that stream keeps its sound */ }
        }
    }

    fun off() {
        if (!held) return
        held = false
        val streams = muted.toList()
        muted.clear()
        // A moment later: the closing beep comes right after the recognizer is destroyed
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            for (stream in streams) try { audio.adjustStreamVolume(stream, android.media.AudioManager.ADJUST_UNMUTE, 0) } catch (err: Exception) { }
        }, 600)
    }
}
