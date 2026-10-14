package com.aiwa.store
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The second listening, the one of the liaison agent: normal consumption (the phone's own recognizer, not the small word detector), it never ends by
 * itself. Each sentence heard is handed to [onHeard]; silence just starts another pass. Whoever uses it stops it before the phone speaks (the voice
 * must not be heard as a sentence) and starts a new one afterwards. Main thread only.
 */
class AgentListener(context: Context, private val onHeard: (String) -> Unit) : RecognitionListener {
    private val appContext = context.applicationContext
    private val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
    private val quiet = QuietSounds(appContext)
    private val main = Handler(Looper.getMainLooper())
    private var stopped = false

    init { recognizer.setRecognitionListener(this) }

    fun start() {
        if (stopped) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        quiet.on()
        try { recognizer.startListening(intent) } catch (err: Exception) { again() }
    }

    fun stop() {
        if (stopped) return
        stopped = true
        main.removeCallbacksAndMessages(null)
        try { recognizer.destroy() } catch (err: Exception) { /* already gone */ }
        quiet.off()
    }

    // A short pause before the next pass: a recognizer restarted at once answers "busy".
    private fun again() { if (!stopped) main.postDelayed({ start() }, 300) }

    override fun onResults(results: Bundle) {
        if (stopped) return
        val heard = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
        if (heard.isNotEmpty()) onHeard(heard) else again()
    }

    override fun onError(error: Int) { again() }

    override fun onPartialResults(partialResults: Bundle) {}
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}

/** The phone says the sentence and listens for « c'est bon vas-y »: true only if it was heard (anything else, or silence, is a no). Main thread. */
suspend fun askAloud(context: Context, sentence: String): Boolean {
    if (!Speaker.speakAndWait(context, "$sentence Dis : c'est bon, vas-y.")) return false
    delay(400)   // the end of the voice must not be heard as the answer
    return suspendCancellableCoroutine { cont ->
        val confirm = ConfirmListener(context) { answer -> if (cont.isActive) cont.resume(answer) }
        cont.invokeOnCancellation { confirm.cancel() }
        confirm.start()
    }
}
