package com.aiwa.store

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.SpeakerModel

/**
 * « Seulement ma voix »: the owner reads a few sentences (about twelve seconds); the speaker model of Vosk makes a print of each stretch of voice and the mean of them is kept on the phone
 * (VoiceLock). The speaker model (about 13 MB) is downloaded the first time. The always-on listening is put aside while this window records (one microphone).
 */
class VoicePrintActivity : ComponentActivity() {
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            val scope = rememberCoroutineScope()
            var state by remember { mutableStateOf("idle") }
            var note by remember { mutableStateOf<String?>(null) }
            var saved by remember { mutableStateOf(VoiceLock.print(applicationContext) != null) }
            DarkSheetTheme {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (state == "idle" || state == "done") finish() },
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(22.dp), color = SheetBg, border = androidx.compose.foundation.BorderStroke(1.dp, SheetLine),
                        modifier = Modifier.padding(16.dp).fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Ma voix", style = MaterialTheme.typography.titleMedium)
                            Text("Le réveil ne répondra qu'à ta voix. Lis à voix haute, normalement, dans le calme, pendant douze secondes. Rien n'est enregistré : seule une empreinte de ta voix est gardée sur ce téléphone.", style = MaterialTheme.typography.bodySmall)
                            Text("« Bonjour, c'est moi. Je dis mon mot de réveil, puis je donne une instruction, comme je le fais d'habitude. Je parle d'une voix claire, ni trop près ni trop loin du téléphone. »", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                when (state) {
                                    "downloading" -> "Je télécharge le modèle de voix (environ 13 Mo)…"
                                    "recording" -> "J'écoute… lis la phrase."
                                    "computing" -> "Je calcule l'empreinte…"
                                    "done" -> "Voix enregistrée ✓ La case « seulement ma voix » est active."
                                    else -> if (saved) "Une empreinte est déjà gardée : tu peux la refaire." else "Pas encore d'empreinte."
                                },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                            Button(onClick = {
                                if (androidx.core.content.ContextCompat.checkSelfPermission(this@VoicePrintActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { permission.launch(Manifest.permission.RECORD_AUDIO); return@Button }
                                note = null
                                scope.launch {
                                    if (!SpeakerModelStore.isReady(applicationContext)) {
                                        state = "downloading"
                                        val failure = withContext(Dispatchers.IO) { SpeakerModelStore.download(applicationContext) { } }
                                        if (failure != null) { state = "idle"; note = "Le modèle de voix n'a pas pu être installé : $failure"; return@launch }
                                    }
                                    if (!VoskModelStore.isReady(applicationContext)) {
                                        state = "downloading"
                                        val failure = withContext(Dispatchers.IO) { VoskModelStore.download(applicationContext) { } }
                                        if (failure != null) { state = "idle"; note = "Le modèle vocal n'a pas pu être installé : $failure"; return@launch }
                                    }
                                    WakeWordService.instance?.let { s -> withContext(Dispatchers.Main) { s.pause() } }
                                    state = "recording"
                                    val print = withContext(Dispatchers.IO) { recordPrint() }
                                    WakeWordService.instance?.let { s -> withContext(Dispatchers.Main) { s.resume() } }
                                    if (print == null) { state = "idle"; note = "Je n'ai pas assez entendu ta voix. Réessaie plus près, dans le calme."; return@launch }
                                    VoiceLock.savePrint(applicationContext, print)
                                    VoiceLock.setEnabled(applicationContext, true)
                                    WakeWordService.reload(applicationContext)
                                    saved = true; state = "done"
                                }
                            }, enabled = state == "idle" || state == "done", modifier = Modifier.fillMaxWidth()) { Text(if (saved) "Refaire mon empreinte" else "Enregistrer ma voix (12 s)") }
                            OutlinedButton(onClick = { finish() }, enabled = state == "idle" || state == "done", modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
                        }
                    }
                }
            }
        }
    }

    /** Twelve seconds of the owner's voice → the mean print, or null when too little voice was measured. Blocking. */
    private fun recordPrint(): DoubleArray? {
        val rate = 16_000
        val size = maxOf(AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), 8192)
        val record = try {
            @android.annotation.SuppressLint("MissingPermission")
            val r = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
            if (r.state != AudioRecord.STATE_INITIALIZED) { r.release(); null } else r
        } catch (err: Exception) { null } ?: return null
        val prints = mutableListOf<DoubleArray>()
        try {
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            Model(VoskModelStore.dir(applicationContext).absolutePath).use { model ->
                SpeakerModel(SpeakerModelStore.dir(applicationContext).absolutePath).use { spk ->
                    Recognizer(model, rate.toFloat()).use { recognizer ->
                        recognizer.setSpeakerModel(spk)
                        fun collect(json: String) {
                            val j = JSONObject(json)
                            val a = j.optJSONArray("spk") ?: return
                            if (a.length() >= 64 && j.optInt("spk_frames", 0) >= 60) prints.add(DoubleArray(a.length()) { a.optDouble(it) })
                        }
                        record.startRecording()
                        val buffer = ShortArray(2048)
                        val end = System.currentTimeMillis() + 12_000
                        while (System.currentTimeMillis() < end) {
                            val n = record.read(buffer, 0, buffer.size)
                            if (n <= 0) continue
                            if (recognizer.acceptWaveForm(buffer, n)) collect(recognizer.result)
                        }
                        collect(recognizer.finalResult)
                    }
                }
            }
        } catch (err: Throwable) {
            return null
        } finally {
            try { record.stop() } catch (err: Exception) { }
            record.release()
        }
        return if (prints.size >= 1) VoiceLock.average(prints) else null
    }
}
