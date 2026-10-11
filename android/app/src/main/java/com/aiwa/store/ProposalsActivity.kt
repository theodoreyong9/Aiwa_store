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

    MaterialTheme {
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
                    Text("Propositions à valider", style = MaterialTheme.typography.titleMedium)
                    Text("Rien n'est envoyé d'ici : « Envoyer » ouvre ta messagerie avec le mail rempli, tu le relis et tu l'envoies toi-même.", style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { test = !test }) {
                        Checkbox(checked = test, onCheckedChange = { test = it })
                        Text("Test : le mail s'ouvre sans destinataire (écris ta propre adresse)")
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
                                                if (!test) { mark(context, repoName, p.id, "opened", true); stamp.value++ }
                                            } else toastOnMain(context, "Aucune messagerie trouvée sur ce téléphone.")
                                        }, modifier = Modifier.weight(1f)) { Text(if (done) "Rouvrir le mail" else if (test) "Envoyer (test)" else "Envoyer") }
                                        OutlinedButton(onClick = { mark(context, repoName, p.id, "dismissed", true); stamp.value++ }, modifier = Modifier.weight(1f)) { Text("Écarter") }
                                    }
                                    if (done) Text("Mail ouvert dans ta messagerie (c'est toi qui l'envoies).", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
                }
            }
        }
    }
}
