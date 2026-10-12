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

/** The request for a video made from a document; my GitHub projects are named only when the box was ticked (the instruction says the same). */
fun documentVideoRequest(doc: PickedDocument, format: String, withGithub: Boolean): String =
    "Fais une vidéo à partir de ce document (fichier « ${doc.name} »), au format $format. " + githubPhrase(withGithub) +
        "Si un choix ne t'appartient pas, demande-le moi par la question du widget.\n\nDocument :\n<<<\n${doc.text}\n>>>"

/** The request for a campaign of proposals from a document: the agent proposes the list of targets first and WAITS for my answer; nothing is sent by it. */
fun campaignRequest(doc: PickedDocument, targets: String, withGithub: Boolean): String =
    "Prépare une campagne de propositions à partir de ce document (fichier « ${doc.name} »). Les cibles : ${targets.trim()}. " + githubPhrase(withGithub) +
        "Établis d'abord la liste des cibles et envoie-la moi pour validation par la question du widget : ATTENDS ma réponse avant de fabriquer quoi que ce soit. " +
        "Ensuite, pour chaque cible : une maquette, une vidéo, un prix, sous `aiwa-proposals/`. Tu n'envoies rien : je valide chaque proposition dans l'application (Propositions à valider).\n\nDocument :\n<<<\n${doc.text}\n>>>"

/** The three qualities of a video, chosen where the video is asked for: a button each, the one in use filled. */
@Composable
fun QualityChoice(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Qualité de la vidéo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("simple" to "Simple", "moyen" to "Moyen", "extra" to "Extra").forEach { (id, label) ->
                if (id == selected) Button(onClick = { onSelect(id) }, modifier = Modifier.weight(1f)) { Text(label) }
                else OutlinedButton(onClick = { onSelect(id) }, modifier = Modifier.weight(1f)) { Text(label) }
            }
        }
        Text(
            when (selected) {
                "simple" -> "Quelques minutes : pages animées, rythme et voix de synthèse."
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
        setContent { FileCreationWindow(startCampaign = intent?.getBooleanExtra("campaign", false) == true, onClose = { finish() }) }
    }
}

@Composable
private fun FileCreationWindow(startCampaign: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val bridge = remember { LocalClaudeBridge() }
    val scope = rememberCoroutineScope()
    var document by remember { mutableStateOf<PickedDocument?>(null) }
    var withGithub by remember { mutableStateOf(false) }
    var campaign by remember { mutableStateOf(startCampaign) }
    var targets by remember { mutableStateOf("") }
    var quality by remember { mutableStateOf(AiwaRepository.state.value.videoQuality) }
    var busy by remember { mutableStateOf(false) }
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
                    Text("Créer à partir d'un fichier", style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(onClick = { picker.launch(arrayOf("text/*", "application/json", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) {
                        Text(document?.let { "Fichier : ${it.name} · changer" } ?: "Choisir un fichier (CV, argumentaire, brochure…)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { withGithub = !withGithub }) {
                        Checkbox(checked = withGithub, onCheckedChange = { withGithub = it })
                        Column {
                            Text("Ajouter mes réalisations GitHub")
                            Text("Dépôts publics seulement. Décoché : le fichier seul compte.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("Que veux-tu en faire ?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!campaign) Button(onClick = { campaign = false }, modifier = Modifier.weight(1f)) { Text("Une vidéo") } else OutlinedButton(onClick = { campaign = false }, modifier = Modifier.weight(1f)) { Text("Une vidéo") }
                        if (campaign) Button(onClick = { campaign = true }, modifier = Modifier.weight(1f)) { Text("Une campagne") } else OutlinedButton(onClick = { campaign = true }, modifier = Modifier.weight(1f)) { Text("Une campagne") }
                    }
                    QualityChoice(quality) { quality = it }
                    val doc = document
                    if (!campaign) {
                        Text("Claude reconnaît le type du document (parcours, vente…) et adapte la vidéo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for ((label, format) in listOf("9:16" to "9:16 (vertical)", "16:9" to "16:9 (horizontal)", "1:1" to "1:1 (carré)")) {
                                Button(onClick = { doc?.let { send(documentVideoRequest(it, format, withGithub)) } }, enabled = doc != null && !busy, modifier = Modifier.weight(1f)) { Text(label) }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = targets, onValueChange = { targets = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text("À qui ? Décris la liste (métier, ville, critère…)") },
                        )
                        Text(
                            "Claude te propose d'abord la liste : tu la valides. Il prépare ensuite, pour chaque cible, une maquette, une vidéo et un prix. Rien n'est envoyé : tu valides chaque proposition et tu l'envoies toi-même depuis ta messagerie.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { doc?.let { send(campaignRequest(it, targets, withGithub)) } }, enabled = doc != null && targets.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) { Text("Proposer la liste des cibles") }
                    }
                    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
                }
            }
        }
    }
}
