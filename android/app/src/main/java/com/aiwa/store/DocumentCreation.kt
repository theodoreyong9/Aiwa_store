package com.aiwa.store
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiwa.bridge.CampaignChannels
import com.aiwa.bridge.LocalClaudeBridge
import kotlinx.coroutines.launch

/** A document I hand over: its name and its text (a text file only: the phone cannot read a PDF or a Word file here, and says so). */
class PickedDocument(val name: String, val text: String)

private const val DOCUMENT_MAX_CHARS = 40_000

private fun readDocument(context: Context, uri: Uri): PickedDocument? {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: "document"
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes().take(DOCUMENT_MAX_CHARS * 4).toByteArray() } ?: return null
    val text = String(bytes, Charsets.UTF_8)
    // A binary file (PDF, Word, image…) decodes into replacement characters and control codes: not readable here.
    val odd = text.count { it == '�' || (it.code < 32 && it != '\n' && it != '\r' && it != '\t') }
    if (text.isBlank() || odd * 50 > text.length) return null
    return PickedDocument(name, text.take(DOCUMENT_MAX_CHARS))
}

private fun githubPhrase(withGithub: Boolean) =
    if (withGithub) "J'ai coché : ajoute mes réalisations GitHub publiques là où elles servent. "
    else "Je n'ai PAS coché les réalisations GitHub : le fichier seul compte. "

/** What the request is made from: the document picked on the phone (its text travels in the message) and/or files of the repository's folder aiwa-campagne/ (read there). */
private fun sourcesPhrase(doc: PickedDocument?, repoFiles: List<String>, repoKnown: Boolean): String {
    val parts = mutableListOf<String>()
    if (doc != null) parts.add("le document « ${doc.name} » (collé plus bas" + (if (repoKnown) " ; range-en une copie dans `aiwa-campagne/${doc.name}` du dépôt" else "") + ")")
    if (repoFiles.isNotEmpty()) parts.add("les fichiers du dossier `aiwa-campagne/` du dépôt : " + repoFiles.joinToString(", ") + " (lis-les là)")
    return parts.joinToString(" et ")
}

/** A campaign needs no file: my creation in the repository, its videos and, if I ticked it, my public GitHub work can be all it starts from. */
private fun campaignSources(doc: PickedDocument?, repoFiles: List<String>, repoKnown: Boolean, withGithub: Boolean): String {
    val given = sourcesPhrase(doc, repoFiles, repoKnown)
    if (given.isNotEmpty()) return given
    return (if (repoKnown) "ma création, dans le dépôt en cours (lis-le), et les vidéos de `docs/` s'il y en a" else "ce que je te dis ici") +
        (if (withGithub) " ainsi que mes réalisations GitHub publiques" else "")
}

private fun documentBlock(doc: PickedDocument?) = if (doc == null) "" else "\n\nDocument :\n<<<\n${doc.text}\n>>>"

/** The request for a video made from a document and/or files of the repository; my GitHub projects are named only when the box was ticked (the instruction says the same). */
fun documentVideoRequest(doc: PickedDocument?, format: String, withGithub: Boolean, repoFiles: List<String> = emptyList(), repoKnown: Boolean = false): String =
    "Fais une vidéo à partir de ${sourcesPhrase(doc, repoFiles, repoKnown)}, au format $format. " + githubPhrase(withGithub) +
        "Si un choix ne t'appartient pas, demande-le moi par la question du widget." + documentBlock(doc)

/** The request for a campaign of proposals from a document and/or files: the agent works target by target (no list to validate) until I stop it; nothing is sent by it. */
fun campaignRequest(doc: PickedDocument?, targets: String, withGithub: Boolean, repoFiles: List<String> = emptyList(), repoKnown: Boolean = false, channels: String = "", produce: String = "both"): String =
    "Prépare une campagne de propositions à partir de " + campaignSources(doc, repoFiles, repoKnown, withGithub) + ". Les cibles : ${targets.trim()}. " + githubPhrase(withGithub) +
        "Travaille cible par cible, sans attendre de validation de liste : pour chaque cible, fabrique ce qui est demandé, pousse, ajoute sa proposition, puis passe à la suivante, jusqu'à mon message « [Aiwa — stop campagne] ». " +
        when (produce) {
            "site" -> "Pour chaque cible, je veux SEULEMENT un site (la maquette) et un prix, pas de vidéo, sous `aiwa-proposals/`. "
            "video" -> "Pour chaque cible, je veux SEULEMENT une vidéo et un prix, pas de maquette de site, sous `aiwa-proposals/`. "
            else -> "Ensuite, pour chaque cible : une maquette, une vidéo, un prix, sous `aiwa-proposals/`. "
        } + "Tu n'envoies rien : je valide chaque proposition dans l'application (Propositions à valider). " + channels + documentBlock(doc)

/** The three qualities of a video, chosen where the video is asked for: a button each, the one in use filled. */
@Composable
fun QualityChoice(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Qualité de la vidéo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("simple" to "Simple", "moyen" to "Moyen", "extra" to "Extra", "max" to "Max").forEach { (id, label) ->
                if (id == selected) Button(onClick = { onSelect(id) }, modifier = Modifier.weight(1f)) { Text(label) }
                else OutlinedButton(onClick = { onSelect(id) }, modifier = Modifier.weight(1f)) { Text(label) }
            }
        }
        Text(
            when (selected) {
                "simple" -> "Quelques minutes : pages animées, rythme, sans voix."
                "max" -> "Décors 3D réels, son conçu, un film unique par utilisateur. Le plus long, plusieurs heures."
                "extra" -> "Cinématographique : plans 3D, critique poussée. TRÈS long, plusieurs heures."
                else -> "Vraie musique, voix, un tour de critique."
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * "Créer à partir d'un fichier": a system of its own, apart from the videos of my creation. A text file (a CV, a sales pitch, a brochure, notes), the box of my public GitHub work
 * (off: only the file counts), and what I want from it: a VIDEO that adapts to the type of the document, or a CAMPAIGN of proposals (the agent proposes the list of targets, I validate it, then
 * it prepares a mock-up, a video and a price for each, and I validate and send each one from my own mail application: Propositions à valider).
 */
class FileCreationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        // « campaign »: the campaign only (targets, a site and a video for each). « video »: one video from a file, which belongs to the video button. Neither: both, as before.
        setContent { FileCreationWindow(startCampaign = intent?.getBooleanExtra("campaign", false) == true, videoOnly = intent?.getBooleanExtra("video", false) == true, onClose = { finish() }) }
    }
}

@Composable
private fun FileCreationWindow(startCampaign: Boolean, videoOnly: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val bridge = remember { LocalClaudeBridge() }
    val scope = rememberCoroutineScope()
    var document by remember { mutableStateOf<PickedDocument?>(null) }
    var withGithub by remember { mutableStateOf(false) }
    var campaign by remember { mutableStateOf(startCampaign && !videoOnly) }
    val choice = !startCampaign && !videoOnly       // only a window opened with neither asks which of the two
    var targets by remember { mutableStateOf("") }
    var quality by remember { mutableStateOf(AiwaRepository.state.value.videoQuality) }
    var busy by remember { mutableStateOf(false) }
    val state by AiwaRepository.state.collectAsState()
    val repoKnown = state.repo != null
    // The files of the repository's folder aiwa-campagne/, several of which can be ticked
    var repoFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var repoFilesNote by remember { mutableStateOf<String?>(null) }
    val chosen = remember { mutableStateListOf<String>() }
    // The campaign's creation: « classique » (the style of the last send on each channel) or « innovante » (something else), channel by channel
    var innovative by remember { mutableStateOf(false) }
    // What the campaign makes for each target: a site, a video, or both
    var produce by remember { mutableStateOf("both") }
    var proposalList by remember { mutableStateOf<List<com.aiwa.bridge.Proposal>>(emptyList()) }
    fun loadFiles() {
        scope.launch {
            repoFilesNote = "Chargement…"
            try {
                val got = bridge.inputs()
                repoFiles = got.files.map { it.name }
                chosen.retainAll(repoFiles.toSet())
                repoFilesNote = if (got.repo == null) "Choisis d'abord un dépôt (⎇)." else if (repoFiles.isEmpty()) "Rien dans aiwa-campagne/ pour l'instant." else null
            } catch (err: Exception) { repoFilesNote = "Liste indisponible : ${err.message ?: "?"}" }
        }
    }
    LaunchedEffect(repoKnown) {
        if (repoKnown) {
            loadFiles()
            proposalList = try { bridge.proposals().proposals } catch (err: Exception) { emptyList() }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val picked = try { readDocument(context, uri) } catch (err: Exception) { null }
            if (picked == null) toastOnMain(context, "Ce fichier n'est pas lisible ici : donne un fichier texte (.txt, .md, .html, .csv, .json). Un PDF ou un Word, copie-en le texte dans un .txt.")
            else document = picked
        }
    }

    fun send(text: String) {
        if (busy) return
        busy = true
        scope.launch {
            // a campaign is a mode of its own (the project-type list): the instruction of the mode goes with the request
            switchOptions(context, bridge, videoQuality = quality, deploy = if (campaign) "campaign" else null)
            val sent = sendAndTrack(context, bridge, text, toastErrors = true)
            busy = false
            if (sent) { toastOnMain(context, "Demande envoyée à Claude"); onClose() }
        }
    }

    AiwaTheme {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(if (videoOnly) "Une vidéo à partir d'un fichier" else if (startCampaign) "Une campagne à partir d'un fichier" else "Créer à partir d'un fichier", style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(onClick = { picker.launch(arrayOf("text/*", "application/json", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) {
                        Text(document?.let { "Fichier du téléphone : ${it.name} · changer" } ?: "Choisir un fichier texte du téléphone (CV, argumentaire, notes…)")
                    }
                    if (repoKnown) {
                        Text("Fichiers de ton dépôt (dossier aiwa-campagne/)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        repoFiles.forEach { name ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { if (name in chosen) chosen.remove(name) else chosen.add(name) }) {
                                Checkbox(checked = name in chosen, onCheckedChange = { if (it) chosen.add(name) else chosen.remove(name) })
                                Text(name)
                            }
                        }
                        repoFilesNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(
                            "Dépose tes fichiers (CSV, Excel, PDF, images, vidéos) dans ce dossier sur github.com (Add file ▸ Upload files), le dépôt étant public, puis ↻. Un fichier texte choisi sur le téléphone y est rangé par Claude.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = { loadFiles() }, modifier = Modifier.fillMaxWidth()) { Text("↻  Actualiser la liste") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { withGithub = !withGithub }) {
                        Checkbox(checked = withGithub, onCheckedChange = { withGithub = it })
                        Column {
                            Text("Ajouter mes réalisations GitHub")
                            Text("Dépôts publics seulement. Décoché : le fichier seul compte.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (choice) {
                        Text("Que veux-tu en faire ?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!campaign) Button(onClick = { campaign = false }, modifier = Modifier.weight(1f)) { Text("Une vidéo") } else OutlinedButton(onClick = { campaign = false }, modifier = Modifier.weight(1f)) { Text("Une vidéo") }
                            if (campaign) Button(onClick = { campaign = true }, modifier = Modifier.weight(1f)) { Text("Une campagne") } else OutlinedButton(onClick = { campaign = true }, modifier = Modifier.weight(1f)) { Text("Une campagne") }
                        }
                    }
                    QualityChoice(quality) { quality = it }
                    val doc = document
                    val hasSource = doc != null || chosen.isNotEmpty()
                    if (!campaign) {
                        Text("Claude reconnaît le type du document (parcours, vente…) et adapte la vidéo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for ((label, format) in listOf("9:16" to "9:16 (vertical)", "16:9" to "16:9 (horizontal)", "1:1" to "1:1 (carré)")) {
                                Button(onClick = { send(documentVideoRequest(doc, format, withGithub, chosen.toList(), repoKnown)) }, enabled = hasSource && !busy, modifier = Modifier.weight(1f)) { Text(label) }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = targets, onValueChange = { targets = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text("À qui ? Décris la liste (métier, ville, critère…)") },
                        )
                        Text("Pour chaque cible, la campagne produit :", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("both" to "Site + vidéo", "site" to "Un site", "video" to "Une vidéo").forEach { (id, label) ->
                                if (produce == id) Button(onClick = { produce = id }, modifier = Modifier.weight(1f)) { Text(label, maxLines = 1) }
                                else OutlinedButton(onClick = { produce = id }, modifier = Modifier.weight(1f)) { Text(label, maxLines = 1) }
                            }
                        }
                        Text("La création : classique ou innovante ?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!innovative) Button(onClick = { innovative = false }, modifier = Modifier.weight(1f)) { Text("Classique") } else OutlinedButton(onClick = { innovative = false }, modifier = Modifier.weight(1f)) { Text("Classique") }
                            if (innovative) Button(onClick = { innovative = true }, modifier = Modifier.weight(1f)) { Text("Innovante") } else OutlinedButton(onClick = { innovative = true }, modifier = Modifier.weight(1f)) { Text("Innovante") }
                        }
                        Text(
                            "Canal par canal (mail, messageries, X) : classique reste dans le style du dernier envoi sur ce canal, innovante cherche autre chose. Sans envoi précédent sur un canal, Claude part de ta demande.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Un fichier n'est pas obligatoire : ta création du dépôt, ses vidéos et (si tu coches) tes réalisations GitHub peuvent suffire. Claude te propose d'abord la liste : tu la valides. Il prépare ensuite, pour chaque cible, une maquette, une vidéo et un prix. Rien n'est envoyé : tu valides chaque proposition et tu l'envoies toi-même depuis ta messagerie.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { send(campaignRequest(doc, targets, withGithub, chosen.toList(), repoKnown, CampaignChannels.phrase(innovative, state.repo?.let { lastSentByChannel(context, it, proposalList) } ?: emptyMap()), produce)) }, enabled = (hasSource || repoKnown || withGithub) && targets.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) { Text("Lancer la campagne") }
                    }
                    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
                }
            }
        }
    }
}
