package com.aiwa.store
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiwa.bridge.Recognitions

/**
 * « Mes mots »: what the person says — Réveil, Validation (« c'est bien ça »: what the person says to confirm the summary), Stop, Éteins — and « Mots d'Aiwa »: Accomplissement (« c'est prêt »), Rapport (« t'es dispo ? »).
 * Each can be changed by SAYING a word to the phone (what the small listening model will wake on) and/or by WRITING one (what the parser and the agent read in text). Every change is passed on
 * to the small model, to the parser and to the super agent (Vocabulary.changed).
 */
class RecognitionsActivity : ComponentActivity() {
    // Bumped when the window comes back (the voice print may have been made in between): the rows are drawn again
    private var back by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent { RecognitionsWindow(back, onClose = { finish() }) }
    }

    override fun onResume() {
        super.onResume()
        back++
    }
}

// What each recognition can be changed by: said to the small model (the wake-level words) and/or written (the words read in text)
private val BY_VOICE = setOf("agent", "liaison", "instruction", "stop", "eteins", "resend", "where")
private val BY_WRITING = setOf("instruction", "session", "repo", "validation", "stop", "eteins", "resend", "where", "report", "envoi", "efface", "accomplissement", "dispo", "question", "veille", "ecoute")

@Composable
private fun RecognitionsWindow(back: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    var stamp by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<Recognitions.Slot?>(null) }
    var typed by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }

    AiwaTheme {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp), tonalElevation = 6.dp,
                modifier = Modifier.padding(16.dp).fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            ) {
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Reconnaissances", style = MaterialTheme.typography.titleMedium)
                    Text("Dire un mot au téléphone change ce que l'écoute de réveil attend ; l'écrire change ce qu'Aiwa lit dans ce qui est dit, ou ce qu'il dit lui-même.", style = MaterialTheme.typography.bodySmall)
                    stamp; back
                    listOf(Recognitions.MINE to "Mes mots", Recognitions.AIWA to "Mots d'Aiwa").forEach { (group, heading) ->
                    Text(heading, style = MaterialTheme.typography.titleSmall)
                    Recognitions.SLOTS.filter { it.group == group }.forEach { slot ->
                        val spoken = WakeWordSettings.name(context, slot.id)
                        val written = WakeWordSettings.written(context, slot.id)
                        val current = (listOfNotNull(spoken.takeIf { it.isNotBlank() }) + written).ifEmpty { slot.defaults }.joinToString(" · ") { "« $it »" }
                        Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                // One line per word: its text on the left, the three actions on the right (write, say, back to the original)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(slot.label, style = MaterialTheme.typography.titleSmall)
                                        Text(current, style = MaterialTheme.typography.bodyMedium)
                                        Text(slot.help, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (slot.id in BY_WRITING) TextButton(onClick = { typed = ""; problem = null; editing = slot }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("✎") }
                                    if (slot.id in BY_VOICE) TextButton(onClick = { context.startActivity(Intent(context, WakeWordEnrollActivity::class.java).putExtra("slot", slot.id)) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("🎤") }
                                    TextButton(onClick = {
                                        WakeWordSettings.clear(context, slot.id); WakeWordSettings.clearWritten(context, slot.id)
                                        Vocabulary.changed(context); stamp++
                                    }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("↺") }
                                }
                                // Only the wake word can be limited to the owner's voice
                                if (slot.id == "agent") {
                                    val hasPrint = VoiceLock.print(context) != null
                                    val on = VoiceLock.enabled(context) && hasPrint
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = on, onCheckedChange = { wanted ->
                                            if (wanted && !hasPrint) context.startActivity(Intent(context, VoicePrintActivity::class.java))
                                            else { VoiceLock.setEnabled(context, wanted); WakeWordService.reload(context); stamp++ }
                                        })
                                        Text("Seulement ma voix", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                        TextButton(onClick = { context.startActivity(Intent(context, VoicePrintActivity::class.java)) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(if (hasPrint) "Refaire" else "Enregistrer") }
                                    }
                                    Text("Un mot de réveil dure environ une seconde : c'est un filtre contre les autres voix, pas une sécurité. « Stop » et « éteins » marchent toujours.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    }
                    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
                }
            }
        }
        editing?.let { slot ->
            AlertDialog(
                onDismissRequest = { editing = null },
                title = { Text("Écrire : " + slot.label) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(slot.help, style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(value = typed, onValueChange = { typed = it; problem = null }, label = { Text("Ton mot") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val error = Recognitions.checkWritten(slot.id, typed)
                        if (error != null) problem = error
                        else {
                            WakeWordSettings.saveWritten(context, slot.id, (WakeWordSettings.written(context, slot.id) + typed).distinct())
                            Vocabulary.changed(context); stamp++; editing = null
                        }
                    }) { Text("Enregistrer") }
                },
                dismissButton = { TextButton(onClick = { editing = null }) { Text("Annuler") } },
            )
        }
    }
}
