package com.aiwa.store

import android.content.Context
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.aiwa.bridge.LocalClaudeBridge
import java.net.URL
import java.text.Normalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * After « c'est prêt »: when the production is a NEW document or a NEW video, the phone asks what to do with it — a document is read aloud or opened, a video goes to the TV or not.
 * It only asks while the wake-word service holds the microphone (the question needs the person's answer); nothing is done without the answer.
 */
object ProductionOffer {
    private const val PREFS = "aiwa_offer"

    suspend fun run(context: Context) {
        val service = WakeWordService.instance ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val bridge = LocalClaudeBridge()
        val doc = withContext(Dispatchers.IO) { bridge.documents().maxByOrNull { it.ts } }
        val video = try { bridge.videos().videos.firstOrNull() } catch (err: Exception) { null }
        val docKey = doc?.let { "${it.name}:${it.ts}" }
        val videoKey = video?.let { "${it.name}:${it.ts}" }
        val firstTime = !prefs.contains("doc") && !prefs.contains("video")
        val newDoc = doc != null && docKey != prefs.getString("doc", null)
        val newVideo = video != null && videoKey != prefs.getString("video", null)
        prefs.edit().apply { if (docKey != null) putString("doc", docKey); if (videoKey != null) putString("video", videoKey) }.apply()
        if (firstTime || (!newDoc && !newVideo)) return      // what was already there is not offered
        withContext(Dispatchers.Main) { service.pause() }
        try {
            if (newVideo && video != null) {
                val name = video.name.substringBeforeLast('.').replace('_', ' ').replace('-', ' ')
                if (askAloud(context, "Ta vidéo $name est prête. Tu veux que je la lance sur la TV ?")) {
                    try { CastService.start(context, video.url, "", video.name) } catch (err: Exception) { toastOnMain(context, "Envoi à la TV impossible : ${err.message ?: "?"}") }
                }
            } else if (doc != null) {
                val name = doc.name.substringBeforeLast('.').replace('_', ' ').replace('-', ' ')
                Speaker.speakAndWait(context, "Ton document $name est prêt. Tu veux que je le lise, ou que je l'ouvre ?")
                when (listenChoice(context)) {
                    "read" -> readAloud(context, doc.url)
                    "open" -> openProduction(context, "documents", true)
                    else -> {}
                }
            }
        } finally {
            withContext(Dispatchers.Main) { service.resume() }
        }
    }

    /** The beginning of the document, read in the phone's voice (the text of the file, with its marks taken out). */
    private suspend fun readAloud(context: Context, url: String) {
        val raw = withContext(Dispatchers.IO) { try { URL(url).readText() } catch (err: Exception) { null } }
        if (raw == null) { Speaker.speakAndWait(context, "Je n'ai pas pu le lire."); return }
        val text = raw.replace(Regex("(?s)<(script|style).*?</\\1>"), " ").replace(Regex("<[^>]+>"), " ").replace(Regex("[#*_`>|]+"), " ").replace(Regex("\\s+"), " ").trim()
        val part = if (text.length > 1800) text.take(1800).substringBeforeLast(' ') else text
        if (text.length > 1800) Speaker.speakAndWait(context, "Je t'en lis le début.")
        Speaker.speakNatural(context, part)
    }

    /** « lis » → "read", « ouvre » → "open", anything else (or silence) → null: nothing is done. */
    private suspend fun listenChoice(context: Context): String? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val listener = ChoiceListener(context, onChoice = { choice -> if (cont.isActive) cont.resumeWith(Result.success(choice)) })
            cont.invokeOnCancellation { listener.cancel() }
            listener.start()
        }
    }
}

/** « lis » → read, « ouvre » → open. */
private fun offerChoiceOf(heard: String): String? {
    val words = plainWords(heard)
    return when {
        words.any { it in READ_WORDS } -> "read"
        words.any { it in OPEN_WORDS } -> "open"
        else -> null
    }
}

private val READ_WORDS = setOf("lis", "lire", "lit", "lecture", "lisez")
private val OPEN_WORDS = setOf("ouvre", "ouvrir", "ouvrez", "affiche", "montre", "voir")

internal fun plainWords(text: String): List<String> =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").split(Regex("[^a-z]+")).filter { it.isNotEmpty() }

/** One short listening for « lis » or « ouvre » (one more try after a silence). */
class ChoiceListener(context: Context, private val classify: (String) -> String? = ::offerChoiceOf, private val onChoice: (String?) -> Unit) : RecognitionListener {
    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    private val quiet = QuietSounds(context)
    private var done = false
    private var silences = 0

    init { recognizer.setRecognitionListener(this) }

    fun start() {
        if (done) return
        quiet.on()
        recognizer.startListening(android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        })
    }

    fun cancel() {
        if (done) return
        done = true
        recognizer.destroy(); quiet.off()
    }

    private fun finish(choice: String?) {
        if (done) return
        done = true
        recognizer.destroy(); quiet.off()
        onChoice(choice)
    }

    private fun choiceOf(heard: String): String? = classify(heard)

    override fun onResults(results: Bundle) {
        val heard = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        val choice = choiceOf(heard)
        if (choice == null && heard.isBlank() && silences++ < 1) start() else finish(choice)
    }

    override fun onPartialResults(partialResults: Bundle) {
        choiceOf(partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())?.let { finish(it) }
    }

    override fun onError(error: Int) {
        if ((error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) && silences++ < 1) start() else finish(null)
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}
