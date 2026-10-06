package com.aiwa.store
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.aiwa.bridge.isWakeWord
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Listens to the microphone for one word and nothing else: "instruction". The speech model runs on the phone with a grammar that has that
 * one word (anything else is "[unk]"), so it is cheap, and no sound leaves the phone. When the word is heard the microphone is
 * released at once and onWake is called (on this detector's thread: post it where it must run), because the dictation that follows needs
 * the microphone.
 *
 * The word "Aiwa" cannot be listened for: it is not in the small French model's vocabulary.
 */
class WakeWordDetector(
    private val model: Model,
    private val onWake: () -> Unit,
    private val onFailure: (String) -> Unit,
) {
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread({ run() }, "aiwa-wake-word").also { it.start() }
    }

    /** Stops listening and waits (a moment at most) for the microphone to be given back. */
    fun stop() {
        running = false
        try { thread?.join(800) } catch (err: InterruptedException) { /* leaving */ }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun run() {
        val sampleRate = 16_000
        val minimum = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minimum <= 0) { running = false; onFailure("le micro ne donne pas de taille de tampon (code $minimum)"); return }
        val record = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 8192))
        } catch (err: Exception) {
            running = false
            onFailure("micro refusé : ${err.message}")
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            running = false
            onFailure("le micro n'a pas pu s'ouvrir (un autre programme l'utilise ?)")
            return
        }
        var woken = false
        try {
            Recognizer(model, sampleRate.toFloat(), "[\"instruction\", \"[unk]\"]").use { recognizer ->
                record.startRecording()
                val buffer = ShortArray(2048)
                while (running) {
                    val n = record.read(buffer, 0, buffer.size)
                    if (n < 0) { onFailure("lecture du micro interrompue (code $n)"); running = false; break }
                    if (n == 0) continue
                    val heard = if (recognizer.acceptWaveForm(buffer, n)) JSONObject(recognizer.result).optString("text") else JSONObject(recognizer.partialResult).optString("partial")
                    if (heard.isNotEmpty() && isWakeWord(heard)) { woken = true; running = false }
                }
            }
        } catch (err: Throwable) {
            running = false
            onFailure(err.message ?: err.javaClass.simpleName)
        } finally {
            try { record.stop() } catch (err: Exception) { /* not started */ }
            record.release()
        }
        if (woken) onWake()
    }
}
