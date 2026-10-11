package com.aiwa.store
import android.os.Bundle
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.aiwa.bridge.LocalClaudeBridge
import com.aiwa.bridge.VideoList
import kotlinx.coroutines.launch

/**
 * "Vidéos": the promotional videos of the creation, files of the release `videos` of its GitHub repository (public: Aiwa asks
 * for no token). A tap on one opens it; the button asks Claude, in the current session, for a new one (the instruction that
 * tells it how comes with the message: the standard, the tools, the honesty rule).
 */
class VideosActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent { VideosWindow(onClose = { finish() }) }
    }
}

/** A document I hand over: its name and its text (a text file only: the phone cannot read a PDF or a Word file here, and says so). */
private class PickedDocument(val name: String, val text: String)

private const val DOCUMENT_MAX_CHARS = 40_000

private fun readDocument(context: Context, uri: Uri): PickedDocument? {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: "document"
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes().take(DOCUMENT_MAX_CHARS * 4).toByteArray() } ?: return null
    val text = String(bytes, Charsets.UTF_8)
    // A binary file (PDF, Word, image…) decodes into replacement characters and control codes: not readable here.
    val odd = text.count { it == '\uFFFD' || (it.code < 32 && it != '\n' && it != '\r' && it != '\t') }
    if (text.isBlank() || odd * 50 > text.length) return null
    return PickedDocument(name, text.take(DOCUMENT_MAX_CHARS))
}

/** The request for a video made from a document; my GitHub projects are named only when the box was ticked (the instruction says the same). */
private fun documentRequest(doc: PickedDocument, format: String, withGithub: Boolean): String =
    "Fais une vidéo à partir de ce document (fichier « ${doc.name} »), au format $format. " +
        (if (withGithub) "J'ai coché : ajoute mes réalisations GitHub publiques au bon moment de la vidéo. "
        else "Je n'ai PAS coché les réalisations GitHub : le fichier seul compte. ") +
        "Si un choix ne t'appartient pas, demande-le moi par la question du widget.\n\nDocument :\n<<<\n${doc.text}\n>>>"

private fun sizeLabel(bytes: Long) = if (bytes >= 1_000_000) "%.1f Mo".format(bytes / 1_000_000.0) else "${bytes / 1000} Ko"

@Composable
private fun VideosWindow(onClose: () -> Unit) {
    val context = LocalContext.current
    val bridge = remember { LocalClaudeBridge() }
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<VideoList?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var document by remember { mutableStateOf<PickedDocument?>(null) }
    var withGithub by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val picked = try { readDocument(context, uri) } catch (err: Exception) { null }
            if (picked == null) toastOnMain(context, "Ce fichier n'est pas lisible ici : donne un fichier texte (.txt, .md, .html, .csv, .json). Un PDF ou un Word, copie-en le texte dans un .txt.")
            else document = picked
        }
    }

    LaunchedEffect(Unit) {
        try { list = bridge.videos() } catch (err: Exception) { error = err.message ?: "Liste indisponible." }
    }

    fun ask(format: String) {
        if (busy) return
        busy = true
        scope.launch {
            val doc = document
            val sent = sendAndTrack(
                context, bridge,
                if (doc != null) documentRequest(doc, format, withGithub)
                else "Fais une vidéo promotionnelle de ma création, au format $format. Si un choix ne t'appartient pas, demande-le moi par la question du widget.",
                toastErrors = true,
            )
            busy = false
            if (sent) {
                toastOnMain(context, "Demande envoyée à Claude")
                onClose()
            }
        }
    }

    MaterialTheme {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 6.dp,
                modifier = Modifier.padding(24.dp).fillMaxWidth()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Vidéos de ta création", style = MaterialTheme.typography.titleMedium)
                    val current = list
                    when {
                        error != null -> Text(error ?: "", color = MaterialTheme.colorScheme.error)
                        current == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Chargement…")
                        }
                        current.repo == null -> Text("Choisis d'abord un dépôt (le bouton ⎇ du widget).")
                        current.videos.isEmpty() -> Text("Aucune vidéo pour l'instant dans ${current.repo}. Demande-en une ci-dessous ; elle apparaîtra ici dès que Claude l'aura déposée.")
                        else -> current.videos.forEach { video ->
                            OutlinedButton(onClick = { openUrl(context, video.url) }, modifier = Modifier.fillMaxWidth()) {
                                Text(video.name + " · " + sizeLabel(video.size))
                            }
                        }
                    }
                    if (current?.repo != null) {
                        Text("Nouvelle vidéo", style = MaterialTheme.typography.labelLarge)
                        val doc = document
                        OutlinedButton(onClick = { picker.launch(arrayOf("text/*", "application/json", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (doc == null) "À partir d'un fichier (CV, argumentaire…)" else "Fichier : ${doc.name} · changer")
                        }
                        if (doc != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { withGithub = !withGithub }) {
                                Checkbox(checked = withGithub, onCheckedChange = { withGithub = it })
                                Text("Ajouter mes réalisations GitHub (dépôts publics)")
                            }
                            Text("Claude reconnaît le type du document et adapte la vidéo. Sans la case, seul le fichier compte.", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { ask("9:16 (vertical)") }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("9:16") }
                            Button(onClick = { ask("16:9 (horizontal)") }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("16:9") }
                            Button(onClick = { ask("1:1 (carré)") }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("1:1") }
                        }
                    }
                    OutlinedButton(onClick = { context.startActivity(android.content.Intent(context, ProposalsActivity::class.java)); onClose() }, modifier = Modifier.fillMaxWidth()) { Text("Propositions à valider") }
                    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
                }
            }
        }
    }
}
