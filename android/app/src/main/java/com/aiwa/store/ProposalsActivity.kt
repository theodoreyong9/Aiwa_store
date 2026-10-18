package com.aiwa.store
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import android.speech.RecognizerIntent
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import com.aiwa.bridge.Campaign
import com.aiwa.bridge.CampaignRequest
import kotlinx.coroutines.launch
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
import androidx.compose.runtime.collectAsState
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
 * "Campagnes": one tab per campaign (its folder under docs/campagnes/) and a tab for a new one. A campaign tab lists what a session prepared, by channel (WhatsApp, Telegram, Mail,
 * LinkedIn): per line the buttons of what was produced (docs, video, page, app, Aiwa), the name and a share that opens a PREVIEW of what goes out, before the confirmation; one at a
 * time. NOTHING is sent from here: the confirmation opens THEIR OWN application with the message filled in, and the sending is done there. A campaign made as a test lists only the
 * buttons and a plain share. The new-campaign tab is the form (name, session and repository, documents, repositories, test, what to produce, what to use, the request).
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


/** The campaigns just sent, which have no folder in the repository yet: they show as a tab at once. */
private object PendingCampaigns {
    private const val PREFS = "aiwa_campaigns_pending"
    fun list(context: Context, repo: String): List<Pair<String, String>> =
        (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(repo, emptySet()) ?: emptySet()).mapNotNull { it.split('\t').takeIf { p -> p.size == 2 }?.let { p -> p[0] to p[1] } }
    fun add(context: Context, repo: String, slug: String, name: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(repo, ((prefs.getStringSet(repo, emptySet()) ?: emptySet()) + "$slug\t$name")).apply()
    }
    fun forget(context: Context, repo: String, slug: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(repo, (prefs.getStringSet(repo, emptySet()) ?: emptySet()).filterNot { it.startsWith("$slug\t") }.toSet()).apply()
    }
}

private val TYPE_LABELS = linkedMapOf("whatsapp" to "WhatsApp", "telegram" to "Telegram", "mail" to "Mail", "linkedin" to "LinkedIn", "" to "Autres")

/** The channel of a proposal: the one the session wrote, else the best one its contact details allow. */
private fun typeOf(p: Proposal): String = when {
    p.type.isNotBlank() -> p.type
    p.email.isNotBlank() -> "mail"
    p.phone.isNotBlank() -> "whatsapp"
    p.profileUrl.contains("linkedin") -> "linkedin"
    else -> ""
}

/** What goes out, as the preview shows it (the same text the application opens with). */
private fun previewText(p: Proposal, test: Boolean): String = when (typeOf(p)) {
    "mail" -> ProposalMail.draft(p, p.price, test).let { it.subject + "\n\n" + it.body }
    else -> ProposalShare.message(p, p.price, test)
}

/** The confirmed send: the channel's own application opens with the message ready (LinkedIn has no such address: the message is copied and the profile opens). */
private fun launch(context: Context, p: Proposal, test: Boolean): Boolean = when (typeOf(p)) {
    "mail" -> openMail(context, p, p.price, test)
    "whatsapp" -> openChat(context, "whatsapp", p, p.price, test)
    "telegram" -> openChat(context, "telegram", p, p.price, test)
    "linkedin" -> {
        copyToClipboard(context, previewText(p, test))
        toastOnMain(context, "Message copié : colle-le dans LinkedIn.")
        p.profileUrl.isNotBlank() && openUrl(context, p.profileUrl)
    }
    else -> openMail(context, p, p.price, test)
}

/** The buttons of what was produced for this proposal: each opens it. */
/** The request to the session for a change to ONE proposal of a campaign: nothing else is touched, nothing is sent to the target, and the change is pushed for me to read again. Pure, so that it can be tested. */
fun proposalChangeRequest(campaign: String, id: String, name: String, change: String): String =
    "Modification de UNE proposition de la campagne « $campaign » : « ${name.ifBlank { id }} » (id `$id` dans `docs/campagnes/$campaign/proposals.json`). " +
        "Ce que je veux changer : ${change.trim()}. " +
        "Corrige UNIQUEMENT cette proposition (son texte, ses documents ou sa vidéo si c'est ce que je demande), garde son `id`, ne touche à aucune autre cible, n'invente rien, n'envoie rien à la cible. " +
        "Pousse la correction : je relis la nouvelle version dans l'application avant de l'envoyer moi-même. Ne reprends pas la recherche de nouvelles cibles pour ça."

@Composable
private fun ModeButtons(context: Context, p: Proposal) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Docs" to p.docsUrl, "Vidéo" to p.videoUrl, "Page" to p.demoUrl, "App" to p.appUrl, "Aiwa" to p.aiwaUrl).filter { it.second.isNotBlank() }.forEach { (label, url) ->
            OutlinedButton(onClick = { openUrl(context, url) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text(label, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun ProposalsWindow(onClose: () -> Unit) {
    val context = LocalContext.current
    val bridge = remember { LocalClaudeBridge() }
    val scope = rememberCoroutineScope()
    val state by AiwaRepository.state.collectAsState()
    var campaigns by remember { mutableStateOf<List<Campaign>?>(null) }
    var repo by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf("new") }
    var proposals by remember { mutableStateOf<List<Proposal>?>(null) }
    val stamp = remember { mutableStateOf(0) }
    var preview by remember { mutableStateOf<Proposal?>(null) }

    // the form
    var name by remember { mutableStateOf("") }
    var test by remember { mutableStateOf(false) }
    var prompt by remember { mutableStateOf("") }
    var docFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var repoNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var tickedDocs by remember { mutableStateOf<Set<String>>(emptySet()) }
    var tickedRepos by remember { mutableStateOf<Set<String>>(emptySet()) }
    var makes by remember { mutableStateOf<Set<String>>(emptySet()) }
    var uses by remember { mutableStateOf<Set<String>>(emptySet()) }
    var sending by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var confirmStop by remember { mutableStateOf(false) }
    // a change dictated for ONE proposal: the words of the phone's speech recognition land in a box that can be corrected, then go to the session with the proposal named
    var editing by remember { mutableStateOf<Proposal?>(null) }
    var editText by remember { mutableStateOf("") }
    var editSending by remember { mutableStateOf(false) }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val said = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!said.isNullOrBlank()) editText = (editText + " " + said).trim()
    }

    LaunchedEffect(Unit) {
        try {
            val got = bridge.campaigns()
            repo = got.repo
            campaigns = got.campaigns
            if (got.repo != null) {
                docFiles = Productions.documentFiles(got.repo!!)
                got.campaigns.forEach { PendingCampaigns.forget(context, got.repo!!, it.slug) }
            }
            repoNames = try { bridge.githubRepos().map { it.name } } catch (err: Exception) { emptyList() }
            if (got.campaigns.isNotEmpty()) tab = got.campaigns.first().slug
        } catch (err: Exception) { error = err.message ?: "Liste indisponible." }
    }
    LaunchedEffect(tab, campaigns) {
        proposals = null
        if (tab != "new") proposals = try { bridge.campaignProposals(tab).proposals } catch (err: Exception) { emptyList() }
    }

    fun fillFrom(c: Campaign) {
        name = c.name + " 2"; test = c.test; prompt = c.prompt
        tickedDocs = c.documents.toSet(); tickedRepos = c.repos.toSet(); makes = c.makes.toSet(); uses = c.uses.toSet()
        tab = "new"
    }

    fun submit() {
        val currentRepo = state.repo
        if (name.isBlank() || currentRepo == null || sending) { note = if (currentRepo == null) "Choisis d'abord un dépôt." else "Donne un nom à la campagne."; return }
        sending = true
        scope.launch {
            val slug = CampaignRequest.slug(name)
            // the campaign is a mode of work: the instruction of the campaign goes with this message
            switchOptions(context, bridge, deploy = "campaign")
            val text = CampaignRequest.message(name, slug, test, tickedDocs.sorted(), tickedRepos.sorted(), makes.toList().sorted(), uses.toList().sorted(), prompt)
            val sent = sendAndTrack(context, bridge, text, toastErrors = true)
            sending = false
            if (sent) {
                PendingCampaigns.add(context, currentRepo, slug, name.trim())
                campaigns = (campaigns ?: emptyList()) + Campaign(slug, name.trim(), test, System.currentTimeMillis() / 1000, tickedDocs.sorted(), tickedRepos.sorted(), makes.toList().sorted(), uses.toList().sorted(), prompt)
                toastOnMain(context, "Campagne envoyée à Claude : son onglet apparaît ici.")
                tab = slug
            } else note = "Pas envoyée (voir le message du widget)."
        }
    }

    DarkSheetTheme {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp), color = SheetBg, border = androidx.compose.foundation.BorderStroke(1.dp, SheetLine),
                modifier = Modifier.padding(12.dp).fillMaxWidth()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            ) {
                Column(Modifier.padding(14.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Campagnes", style = MaterialTheme.typography.titleMedium)
                    // the tabs: one per campaign, then the new one
                    val list = campaigns ?: emptyList()
                    val pending = (repo ?: state.repo)?.let { r -> PendingCampaigns.list(context, r).filter { p -> list.none { it.slug == p.first } } } ?: emptyList()
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        list.forEach { c ->
                            if (tab == c.slug) Button(onClick = { tab = c.slug }) { Text(c.name.ifBlank { c.slug }, maxLines = 1) }
                            else OutlinedButton(onClick = { tab = c.slug }) { Text(c.name.ifBlank { c.slug }, maxLines = 1) }
                        }
                        pending.forEach { (slug, label) ->
                            if (tab == slug) Button(onClick = { tab = slug }) { Text("$label …", maxLines = 1) }
                            else OutlinedButton(onClick = { tab = slug }) { Text("$label …", maxLines = 1) }
                        }
                        if (tab == "new") Button(onClick = { tab = "new" }) { Text("＋ Nouvelle") } else OutlinedButton(onClick = { tab = "new" }) { Text("＋ Nouvelle") }
                    }
                    stamp.value
                    if (error != null) Text(error ?: "", color = MaterialTheme.colorScheme.error)
                    else if (campaigns == null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Chargement…")
                    }
                    else if (tab == "new") {
                        // ---- the form ----
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom de la campagne") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text("Session : ${state.session}", style = MaterialTheme.typography.bodyMedium)
                        Text("Dépôt : ${state.repo ?: "à choisir"}", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { context.startActivity(Intent(context, SessionPickerActivity::class.java)) }, modifier = Modifier.weight(1f)) { Text("Session ▾") }
                            OutlinedButton(onClick = { context.startActivity(Intent(context, RepoPickerActivity::class.java)) }, modifier = Modifier.weight(1f)) { Text("Dépôt ▾") }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { test = !test }) {
                            Checkbox(checked = test, onCheckedChange = { test = it })
                            Text("Test : une seule proposition, pour moi-même")
                        }
                        Text("Documents du dossier docs/", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        if (docFiles.isEmpty()) Text("Aucun trouvé (ou dépôt privé).", style = MaterialTheme.typography.bodySmall)
                        docFiles.forEach { f ->
                            Row(Modifier.fillMaxWidth().clickable { tickedDocs = if (f in tickedDocs) tickedDocs - f else tickedDocs + f }, verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = f in tickedDocs, onCheckedChange = { tickedDocs = if (it) tickedDocs + f else tickedDocs - f }); Text(f, maxLines = 1)
                            }
                        }
                        Text("Mes dépôts GitHub à utiliser", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        repoNames.take(30).forEach { r ->
                            Row(Modifier.fillMaxWidth().clickable { tickedRepos = if (r in tickedRepos) tickedRepos - r else tickedRepos + r }, verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = r in tickedRepos, onCheckedChange = { tickedRepos = if (it) tickedRepos + r else tickedRepos - r }); Text(r, maxLines = 1)
                            }
                        }
                        Text("Créations à produire pour chaque cible", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            listOf("video" to "Vidéo", "docs" to "Documents", "page" to "Page").forEach { (k, label) ->
                                Row(Modifier.clickable { makes = if (k in makes) makes - k else makes + k }, verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = k in makes, onCheckedChange = { makes = if (it) makes + k else makes - k }); Text(label) }
                            }
                        }
                        Text("Utiliser tel quel (non personnalisable)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            listOf("app" to "App", "aiwa" to "Aiwa").forEach { (k, label) ->
                                Row(Modifier.clickable { uses = if (k in uses) uses - k else uses + k }, verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = k in uses, onCheckedChange = { uses = if (it) uses + k else uses - k }); Text(label) }
                            }
                        }
                        OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text("Ta demande") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        Button(onClick = { submit() }, enabled = !sending, modifier = Modifier.fillMaxWidth()) { Text("Valider et envoyer") }
                    } else {
                        // ---- a campaign ----
                        val campaign = list.firstOrNull { it.slug == tab }
                        val inPreparation = campaign == null
                        val isTest = campaign?.test == true
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val folder = if (tab.isBlank()) "aiwa-proposals" else "docs/campagnes/$tab"
                            OutlinedButton(onClick = { repo?.let { openUrl(context, "https://github.com/$it/tree/HEAD/$folder") } }, modifier = Modifier.weight(1f)) { Text("📁 Voir le dossier") }
                            if (campaign != null) OutlinedButton(onClick = { fillFrom(campaign) }, modifier = Modifier.weight(1f)) { Text("↻ Relancer") }
                        }
                        // The work goes on target by target until it is stopped: the session is told to stop, and reads it at the end of its current step
                        if (!isTest) {
                            if (!confirmStop) OutlinedButton(onClick = { confirmStop = true }, modifier = Modifier.fillMaxWidth()) { Text("■ Arrêter les travaux de la campagne") }
                            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    confirmStop = false
                                    scope.launch {
                                        val problem = try { bridge.campaignStop() } catch (err: Exception) { err.message ?: "?" }
                                        note = problem ?: "Arrêt envoyé : la session s'arrête à la fin de son étape en cours (un arrêt immédiat se fait dans l'appli Claude)."
                                    }
                                }, modifier = Modifier.weight(1f)) { Text("Oui, arrêter") }
                                OutlinedButton(onClick = { confirmStop = false }, modifier = Modifier.weight(1f)) { Text("Non") }
                            }
                        }
                        if (tab != "new") note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        val items = proposals
                        when {
                            items == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text("Chargement…")
                            }
                            items.isEmpty() -> Text(if (inPreparation) "Claude prépare cette campagne : les propositions apparaîtront ici (rouvre la fenêtre dans quelques minutes)." else "Aucune proposition pour l'instant dans cette campagne.")
                            else -> TYPE_LABELS.forEach { (type, label) ->
                                val group = items.filter { typeOf(it) == type && !marked(context, repo ?: "", it.id, "dismissed") }
                                if (group.isNotEmpty()) {
                                    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    group.forEach { p ->
                                        Surface(shape = RoundedCornerShape(12.dp), color = SheetChip, modifier = Modifier.fillMaxWidth()) {
                                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                ModeButtons(context, p)
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text(p.name.ifBlank { p.id }, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 2)
                                                    OutlinedButton(onClick = {
                                                        editing = p; editText = ""
                                                        try {
                                                            speech.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR").putExtra(RecognizerIntent.EXTRA_PROMPT, "Dicte la modification pour " + p.name.ifBlank { p.id }))
                                                        } catch (err: Exception) { toastOnMain(context, "La reconnaissance vocale n'est pas disponible : écris la modification.") }
                                                    }) { Text("🎙") }
                                                    if (isTest) OutlinedButton(onClick = {
                                                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, previewText(p, true))
                                                        try { context.startActivity(Intent.createChooser(send, "Partager").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (err: Exception) { toastOnMain(context, "Partage impossible.") }
                                                    }) { Text("Share") }
                                                    else {
                                                        val done = marked(context, repo ?: "", p.id, "opened")
                                                        Button(onClick = { preview = p }, enabled = preview == null) { Text(if (done) "↗ Reprendre" else "↗ Partager") }
                                                    }
                                                }
                                                if (p.reasons.isNotBlank() && !isTest) Text(p.reasons, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
                }
            }
        }
        // the change dictated for one proposal: read it, correct it, send it to the session
        editing?.let { p ->
            AlertDialog(
                onDismissRequest = { if (!editSending) editing = null },
                title = { Text("Modifier : " + p.name.ifBlank { p.id }) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = editText, onValueChange = { editText = it.take(1500) }, label = { Text("Ce qu'il faut changer") }, minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth())
                        OutlinedButton(onClick = {
                            try {
                                speech.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR").putExtra(RecognizerIntent.EXTRA_PROMPT, "Dicte la suite"))
                            } catch (err: Exception) { toastOnMain(context, "La reconnaissance vocale n'est pas disponible.") }
                        }) { Text("🎙 Ajouter à voix haute") }
                    }
                },
                confirmButton = {
                    TextButton(enabled = !editSending && editText.isNotBlank(), onClick = {
                        editSending = true
                        scope.launch {
                            val sent = sendAndTrack(context, bridge, proposalChangeRequest(tab, p.id, p.name, editText), toastErrors = true)
                            editSending = false
                            if (sent) { toastOnMain(context, "Modification envoyée à Claude"); editing = null }
                        }
                    }) { Text(if (editSending) "Envoi…" else "Envoyer à Claude") }
                },
                dismissButton = { TextButton(onClick = { editing = null }, enabled = !editSending) { Text("Annuler") } },
            )
        }
        // the preview of what goes out, BEFORE the confirmation: one proposal at a time
        preview?.let { p ->
            val campaignTest = campaigns?.firstOrNull { it.slug == tab }?.test == true
            AlertDialog(
                onDismissRequest = { preview = null },
                title = { Text("Avant d'envoyer : " + p.name.ifBlank { p.id }) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Canal : " + (TYPE_LABELS[typeOf(p)] ?: "Autres"), style = MaterialTheme.typography.labelMedium)
                        Text(previewText(p, campaignTest))
                        if (p.profileUrl.isNotBlank()) Text("Profil : " + p.profileUrl, style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val ok = launch(context, p, campaignTest)
                        if (ok) { mark(context, repo ?: "", p.id, "opened", true); rememberSent(context, repo ?: "", typeOf(p), p.id); stamp.value++ } else toastOnMain(context, "L'application du canal n'a pas pu s'ouvrir.")
                        preview = null
                    }) { Text("Confirmer et ouvrir") }
                },
                dismissButton = { TextButton(onClick = { preview = null }) { Text("Annuler") } },
            )
        }
    }
}
