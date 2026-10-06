package com.aiwa.store
import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The phone's own voice (Android's text-to-speech engine, the one set in the phone's settings): it asks the question that confirms
 * a repository, and says "C'est prêt". One engine per sentence, shut down right after: these are a few words a day.
 */
object Speaker {
    /** Says the text and returns when it has been said: true if it was, false when the phone has no French voice, no engine, or it took too long. */
    suspend fun speakAndWait(context: Context, text: String): Boolean = withContext(Dispatchers.Main) {
        withTimeoutOrNull(20_000L) {
            suspendCancellableCoroutine<Boolean> { cont ->
                var engine: TextToSpeech? = null
                fun end(ok: Boolean) {
                    try { engine?.shutdown() } catch (err: Exception) { /* already gone */ }
                    if (cont.isActive) cont.resume(ok)
                }
                engine = TextToSpeech(context.applicationContext) { status ->
                    val tts = engine
                    if (status != TextToSpeech.SUCCESS || tts == null) {
                        end(false)
                    } else {
                        val language = tts.setLanguage(Locale.FRENCH)
                        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                            end(false)
                        } else {
                            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                override fun onStart(utteranceId: String?) {}
                                override fun onDone(utteranceId: String?) { end(true) }
                                @Suppress("OVERRIDE_DEPRECATION")
                                override fun onError(utteranceId: String?) { end(false) }
                            })
                            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString()) != TextToSpeech.SUCCESS) end(false)
                        }
                    }
                }
                cont.invokeOnCancellation { try { engine?.shutdown() } catch (err: Exception) { /* already gone */ } }
            }
        } ?: false
    }
}
