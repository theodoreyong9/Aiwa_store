package com.aiwa.store
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.aiwa.bridge.WakeKind
import com.aiwa.bridge.WakeWords
import com.aiwa.bridge.wakeKind
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Listens to the microphone for a few phrases and nothing else: "mon agent", "instruction", "stop Claude" and "arrête Claude". The speech model runs on the phone with a grammar
 * that has only those (anything else is "[unk]"), so it is cheap, and no sound leaves the phone. When the word is heard the microphone is
 * released at once and onWake is called (on this detector's thread: post it where it must run), because the dictation that follows needs
 * the microphone.
 *
 * A word of the person's own is listened for through the spellings the engine heard when it was said to the phone (WakeWords): "Aiwa" is not in the small French model's
 * vocabulary and cannot be written in the grammar, but what the engine writes when it hears it can.
 */
class WakeWordDetector(
    private val model: Model,
    /** The spellings of the word the person chose (WakeWordSettings), as the engine heard them; empty = only the words of origin. */
    private val custom: List<String> = emptyList(),
    /** The spellings of the word chosen for the super agent (slot "liaison"); empty = only « mon super agent ». */
    private val liaison: List<String> = emptyList(),
    /** The spellings chosen for instruction, stop, éteins and renvoi (empty lists = the words of origin only). */
    private val more: com.aiwa.bridge.WakeCustoms = com.aiwa.bridge.WakeCustoms(),
    private val onWake: (WakeKind) -> Unit,
    private val onFailure: (String) -> Unit,
    /** True while a sound or a video plays on the phone (another app). With [pauseOnMedia], the microphone is then given back and listening waits: a video is never paused by it, and its own sound is never taken for a word. */
    private val mediaPlaying: () -> Boolean = { false },
    private val pauseOnMedia: Boolean = false,
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

    /** Listens; while a media plays (and the person asked for it) the microphone is released and listening waits, a second at a time. */
    private fun run() {
        while (running) {
            if (pauseOnMedia && mediaPlaying()) {
                try { Thread.sleep(1200) } catch (err: InterruptedException) { return }
                continue
            }
            if (listenOnce()) return
        }
    }

    /** One listening, from opening the microphone to giving it back. True when it is over for good (a word heard, a failure, a stop); false when a media started playing. */
    @android.annotation.SuppressLint("MissingPermission")
    private fun listenOnce(): Boolean {
        val sampleRate = 16_000
        val minimum = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minimum <= 0) { running = false; onFailure("le micro ne donne pas de taille de tampon (code $minimum)"); return true }
        val record = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 8192))
        } catch (err: Exception) {
            running = false
            onFailure("micro refusé : ${err.message}")
            return true
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            running = false
            onFailure("le micro n'a pas pu s'ouvrir (un autre programme l'utilise ?)")
            return true
        }
        var woken: WakeKind? = null
        var interrupted = false
        var sinceCheck = 0
        try {
            Recognizer(model, sampleRate.toFloat(), WakeWords.grammar(custom + liaison + more.all())).use { recognizer ->
                record.startRecording()
                val buffer = ShortArray(2048)
                while (running) {
                    val n = record.read(buffer, 0, buffer.size)
                    if (n < 0) { onFailure("lecture du micro interrompue (code $n)"); running = false; break }
                    if (n == 0) continue
                    // about every second (8 reads of 128 ms): has a media started? then the microphone goes back and this listening ends
                    if (pauseOnMedia && ++sinceCheck >= 8) { sinceCheck = 0; if (mediaPlaying()) { interrupted = true; break } }
                    val heard = if (recognizer.acceptWaveForm(buffer, n)) JSONObject(recognizer.result).optString("text") else JSONObject(recognizer.partialResult).optString("partial")
                    val kind = if (heard.isEmpty()) null else wakeKind(heard, custom, liaison, more)
                    if (kind != null) { woken = kind; running = false }
                }
            }
        } catch (err: Throwable) {
            running = false
            onFailure(err.message ?: err.javaClass.simpleName)
        } finally {
            try { record.stop() } catch (err: Exception) { /* not started */ }
            record.release()
        }
        woken?.let { onWake(it) }
        return !interrupted
    }
}
