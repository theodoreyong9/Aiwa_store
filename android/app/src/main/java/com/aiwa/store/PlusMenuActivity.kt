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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import com.aiwa.bridge.LocalClaudeBridge
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The channels of « + »: what a contact is called there, and what to give. The id travels in the request to the session. */
private data class Channel(val id: String, val label: String, val handle: String)

private val CHANNELS = listOf(
    Channel("whatsapp", "WhatsApp", "Numéro (avec l'indicatif)"),
    Channel("telegram", "Telegram", "@identifiant ou numéro"),
    Channel("sms", "SMS", "Numéro"),
    Channel("mail", "Mail", "Adresse mail"),
    Channel("x", "Twitter / X", "@identifiant"),
    Channel("linkedin", "LinkedIn", "Lien du profil ou nom"),
    Channel("instagram", "Instagram", "@identifiant"),
)

/** The request for ONE contact: a campaign of a single target, nothing sent by the session (I validate and send it myself). Pure text, so that it can be tested. */
fun contactRequest(channelLabel: String, name: String, handle: String, message: String): String =
    "Prépare UN message pour UN seul contact, sur $channelLabel : ${name.trim().ifBlank { "(sans nom)" }}" + (if (handle.isNotBlank()) " (${handle.trim()})" else "") + ". " +
        "Ce que je veux lui dire : ${message.trim()}. " +
        "C'est une campagne d'une seule cible : ne cherche pas d'autres cibles, ne fais pas de liste, et arrête-toi après cette proposition. " +
        "Écris le message tel qu'on l'écrit sur $channelLabel (ton, longueur, formules de ce canal), personnalisé pour cette personne : pas de site ni de vidéo sauf si je les ai demandés. " +
        "Pousse-le sous `aiwa-proposals/`, ajoute sa proposition à `proposals.json` (le canal, le contact, le texte), et n'envoie rien : je la valide dans l'application (Propositions à valider) puis je l'envoie moi-même depuis $channelLabel."

/** The « + » of the widget: a document or a media (a file that goes with the next message), or a message to ONE contact on a channel (a campaign of one target). */
class PlusMenuActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            // the files joined and not sent yet, one by one: each can be seen and taken back on its own
            val files = remember { androidx.compose.runtime.mutableStateListOf<Pair<String, Int>>() }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                val got = withContext(Dispatchers.IO) { try { LocalClaudeBridge().status().attachmentFiles } catch (e: Exception) { emptyList() } }
                files.clear(); files.addAll(got)
            }
            val scope = rememberCoroutineScope()
            val entries = buildList {
                files.forEachIndexed { i, f ->
                    val kb = (f.second + 1023) / 1024
                    add(PickerEntry("✕  ${f.first}\n$kb Ko, part avec le prochain message : toucher pour le retirer", true) {
                        val app = applicationContext
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { LocalClaudeBridge().attachRemove(i) }
                            if (ok) {
                                files.removeAt(i)
                                toastOnMain(app, "« ${f.first} » retiré")
                                try { AiwaWidget().updateAll(app) } catch (e: Exception) { }
                            } else toastOnMain(app, "Impossible de le retirer")
                        }
                    })
                }
                add(PickerEntry("Document\nun fichier joint au prochain message", false) { attach(arrayOf("*/*")) })
                add(PickerEntry("Média\nphoto, vidéo ou son, joint au prochain message", false) { attach(arrayOf("image/*", "video/*", "audio/*")) })
                add(PickerEntry("Écrire à un contact — une campagne d'un seul contact", false, header = true) { })
                CHANNELS.forEach { c ->
                    add(PickerEntry(c.label, false) {
                        startActivity(Intent(this@PlusMenuActivity, ContactMessageActivity::class.java).putExtra("channel", c.id)); finish()
                    })
                }
            }
            PickerSheet(entries) { finish() }
        }
    }

    private fun attach(types: Array<String>) {
        startActivity(Intent(this, AttachActivity::class.java).putExtra("types", types)); finish()
    }
}

/** One contact: a name, how to reach them on the channel, what to say. The request goes to the session as a campaign of one target. */
class ContactMessageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        val channel = CHANNELS.firstOrNull { it.id == intent.getStringExtra("channel") } ?: CHANNELS[0]
        setContent {
            var name by remember { mutableStateOf("") }
            var handle by remember { mutableStateOf("") }
            var message by remember { mutableStateOf("") }
            var busy by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            DarkSheetTheme {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(22.dp), color = SheetBg, border = androidx.compose.foundation.BorderStroke(1.dp, SheetLine),
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
                    ) {
                        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                            Text("Écrire à un contact · ${channel.label}", style = MaterialTheme.typography.titleMedium)
                            Text("Claude prépare le message pour cette personne ; tu le valides dans Propositions à valider et tu l'envoies toi-même.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                            androidx.compose.material3.OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, singleLine = true, label = { Text("Nom du contact") }, modifier = Modifier.fillMaxWidth())
                            androidx.compose.material3.OutlinedTextField(value = handle, onValueChange = { handle = it.take(120) }, singleLine = true, label = { Text(channel.handle) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                            androidx.compose.material3.OutlinedTextField(value = message, onValueChange = { message = it.take(1200) }, label = { Text("Ce que tu veux lui dire") }, minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                                androidx.compose.material3.TextButton(onClick = { finish() }) { Text("Annuler") }
                                androidx.compose.material3.Button(enabled = !busy && message.isNotBlank() && (name.isNotBlank() || handle.isNotBlank()), onClick = {
                                    busy = true
                                    val app = applicationContext
                                    scope.launch {
                                        val bridge = LocalClaudeBridge()
                                        switchOptions(app, bridge, deploy = "campaign")
                                        val sent = sendAndTrack(app, bridge, contactRequest(channel.label, name, handle, message), toastErrors = true)
                                        busy = false
                                        if (sent) { toastOnMain(app, "Demande envoyée à Claude"); finish() }
                                    }
                                }) { Text(if (busy) "Envoi…" else "Préparer ce message") }
                            }
                        }
                    }
                }
            }
        }
    }
}
