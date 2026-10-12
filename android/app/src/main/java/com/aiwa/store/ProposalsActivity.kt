package com.aiwa.store
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiwa.bridge.LocalClaudeBridge
import com.aiwa.bridge.Proposal
import com.aiwa.bridge.ProposalList
import com.aiwa.bridge.ProposalMail
import com.aiwa.bridge.ProposalShare

/**
 * "Propositions": what a session prepared for a list of people (a mock-up, a video, a price) and wrote in the repository. NOTHING is sent from here: for each one the
 * person reads the message, changes the price, and presses « Envoyer » — which opens THEIR OWN mail application with the mail filled in; the sending is done there,
 * by them. No mail account is ever handed to Aiwa. A test box fills the mail for nobody, to try it on oneself. What was opened or put aside is remembered on the phone.
 */
class ProposalsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent { ProposalsWindow(onClose = { finish() }) }
    }
}

private const val STATE_PREFS = "aiwa_proposals"

private fun marked(context: Context, repo: String, id: String, kind: String) =
    context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).getBoolean("$kind:$repo:$id", false)

private fun mark(context: Context, repo: String, id: String, kind: String, value: Boolean) =
    context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).edit().putBoolean("$kind:$repo:$id", value).apply()

/** The channel a door belongs to for the campaign's history: mail, chat (WhatsApp, SMS, Telegram) or x. */
private fun channelOf(kind: String) = when (kind) { "mail" -> "mail"; "x" -> "x"; else -> "chat" }

/** What was last sent on a channel (a real send, not a test): remembered on the phone, to start the next creation of that channel from it. */
private fun rememberSent(context: Context, repo: String, kind: String, id: String) =
    context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).edit().putString("lastsent:$repo:${channelOf(kind)}", id).apply()

/** For each channel, the proposal last sent on it (among the repository's proposals). */
internal fun lastSentByChannel(context: Context, repo: String, proposals: List<Proposal>): Map<String, Proposal> {
    val prefs = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
    val result = LinkedHashMap<String, Proposal>()
    for (channel in listOf("mail", "chat", "x")) {
        val id = prefs.getString("lastsent:$repo:$channel", null) ?: continue
        proposals.firstOrNull { it.id == id }?.let { result[channel] = it }
    }
    return result
}

private fun openMail(context: Context, p: Proposal, price: String, test: Boolean): Boolean {
    val draft = ProposalMail.draft(p, price, test)
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        if (draft.to.isNotBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(draft.to))
        putExtra(Intent.EXTRA_SUBJECT, draft.subject)
        putExtra(Intent.EXTRA_TEXT, draft.body)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try { context.startActivity(intent); true } catch (err: Exception) { false }
}

/** The chat applications: the address each publishes opens the application with the message ready; the person sends it there. */
private fun openChat(context: Context, kind: String, p: Proposal, price: String, test: Boolean): Boolean {
    val intent = when (kind) {
        "whatsapp" -> ProposalShare.whatsapp(p, price, test)?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)) }
        "x" -> Intent(Intent.ACTION_VIEW, Uri.parse(ProposalShare.x(p, price, test)))
        "sms" -> ProposalShare.smsAddress(p, test)?.let { Intent(Intent.ACTION_SENDTO, Uri.parse(it)).putExtra("sms_body", ProposalShare.message(p, price, test)) }
        else -> Intent(Intent.ACTION_VIEW, Uri.parse(ProposalShare.telegram(p, price, test)))
    } ?: return false
    return try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (err: Exception) { false }
}

@Composable
private fun ProposalsWindow(onClose: () -> Unit) {
    val context = LocalContext.current
    val bridge = remember { LocalClaudeBridge() }
    var list by remember { mutableStateOf<ProposalList?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var test by remember { mutableStateOf(true) }   // on by default: the first mail goes to oneself
    val prices = remember { mutableStateMapOf<String, String>() }
    val stamp = remember { mutableStateOf(0) }       // changes when a card is marked, to redraw

    LaunchedEffect(Unit) {
        try {
            val got = bridge.proposals()
            got.proposals.forEach { prices[it.id] = it.price }
            list = got
        } catch (err: Exception) { error = err.message ?: "Liste indisponible." }
    }

    AiwaTheme {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp), tonalElevation = 6.dp,
                modifier = Modifier.padding(16.dp).fillMaxWidth()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            ) {
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Propositions et gestion de la campagne", style = MaterialTheme.typography.titleMedium)
                    Text("Rien n'est envoyé d'ici : « Envoyer » ouvre ta messagerie, et WhatsApp, SMS ou Telegram ouvrent leur application, avec le message rempli ; tu le relis et tu l'envoies toi-même.", style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { test = !test }) {
                        Checkbox(checked = test, onCheckedChange = { test = it })
                        Text("Test : le message s'ouvre sans destinataire (choisis ta propre adresse ou ton propre contact)")
                    }
                    val current = list
                    val repoName = current?.repo ?: ""
                    stamp.value
                    when {
                        error != null -> Text(error ?: "", color = MaterialTheme.colorScheme.error)
                        current == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Chargement…")
                        }
                        current.repo == null -> Text("Choisis d'abord un dépôt (le bouton ⎇ du widget).")
                        current.proposals.isEmpty() -> Text("Aucune proposition préparée dans ${current.repo}. Demande à Claude d'en préparer pour une liste de destinataires ; elles apparaîtront ici.")
                        else -> current.proposals.filter { !marked(context, repoName, it.id, "dismissed") }.forEach { p ->
                            val done = marked(context, repoName, p.id, "opened")
                            Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(p.name.ifBlank { p.id }, style = MaterialTheme.typography.titleSmall)
                                    Text(if (p.email.isBlank()) "Pas d'adresse sûre : tu la tapes dans ta messagerie." else p.email, style = MaterialTheme.typography.bodySmall)
                                    Text(p.body, style = MaterialTheme.typography.bodySmall, maxLines = 6)
                                    OutlinedTextField(
                                        value = prices[p.id] ?: "", onValueChange = { prices[p.id] = it },
                                        label = { Text("Prix proposé") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (p.demoUrl.isNotBlank()) OutlinedButton(onClick = { openUrl(context, p.demoUrl) }, modifier = Modifier.weight(1f)) { Text("Maquette") }
                                        if (p.videoUrl.isNotBlank()) OutlinedButton(onClick = { openUrl(context, p.videoUrl) }, modifier = Modifier.weight(1f)) { Text("Vidéo") }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = {
                                            if (openMail(context, p, prices[p.id] ?: "", test)) {
                                                if (!test) { mark(context, repoName, p.id, "opened", true); rememberSent(context, repoName, "mail", p.id); stamp.value++ }
                                            } else toastOnMain(context, "Aucune messagerie trouvée sur ce téléphone.")
                                        }, modifier = Modifier.weight(1f)) { Text(if (done) "Rouvrir le mail" else if (test) "Envoyer (test)" else "Envoyer") }
                                        OutlinedButton(onClick = { mark(context, repoName, p.id, "dismissed", true); stamp.value++ }, modifier = Modifier.weight(1f)) { Text("Écarter") }
                                    }
                                    // The other doors, each an application of the person's own phone (nothing is sent from here)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("whatsapp" to "WhatsApp", "sms" to "SMS", "telegram" to "Telegram", "x" to "X").forEach { (kind, label) ->
                                            val usable = kind == "telegram" || kind == "x" || test || ProposalShare.digits(p.phone) != null
                                            OutlinedButton(
                                                enabled = usable,
                                                onClick = {
                                                    if (openChat(context, kind, p, prices[p.id] ?: "", test)) {
                                                        if (!test) { mark(context, repoName, p.id, "opened", true); rememberSent(context, repoName, kind, p.id); stamp.value++ }
                                                    } else toastOnMain(context, "$label n'a pas pu s'ouvrir sur ce téléphone.")
                                                },
                                                modifier = Modifier.weight(1f),
                                            ) { Text(label) }
                                        }
                                    }
                                    if (p.videoUrl.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = { VideoSharing.share(context, p.videoUrl, p.name.ifBlank { p.id }, ProposalShare.videoDescription(p, prices[p.id] ?: "")) },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) { Text("Partager la vidéo (YouTube, Instagram…)") }
                                    }
                                    if (done) Text("Mail ouvert dans ta messagerie (c'est toi qui l'envoies).", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { context.startActivity(Intent(context, FileCreationActivity::class.java).putExtra("campaign", true)); onClose() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Nouvelle campagne à partir d'un fichier") }
                    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
                }
            }
        }
    }
}
