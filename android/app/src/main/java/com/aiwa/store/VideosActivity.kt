package com.aiwa.store
import android.os.Bundle
import androidx.glance.appwidget.updateAll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
 * "Vidéos": the videos of the creation, the video files of `docs/videos/` of its GitHub repository (public: Aiwa asks for no token). At the top, the LAST production: a tap plays it in the app,
 * with its buttons cast, download and share; the folder of the videos is one button away. Under it, a new video: the documents of `docs/` to tick (or none), the quality, and the format; the request
 * goes to Claude in the current session (the instruction that tells it how comes with the message: the standard, the tools, the honesty rule).
 */
class VideosActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent { VideosWindow(onClose = { finish() }) }
    }
}

private fun sizeLabel(bytes: Long) = if (bytes <= 0) "" else if (bytes >= 1_000_000) "%.1f Mo".format(bytes / 1_000_000.0) else "${bytes / 1000} Ko"

/** The file goes to the Downloads folder of the phone (Android's download manager: a notification shows it). */
private fun download(context: Context, url: String, name: String) {
    try {
        val request = android.app.DownloadManager.Request(Uri.parse(url))
            .setTitle(name)
            .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, name)
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager).enqueue(request)
        toastOnMain(context, "Téléchargement de $name lancé (dossier Téléchargements).")
    } catch (err: Exception) {
        toastOnMain(context, "Téléchargement impossible : ${err.message ?: "?"}")
        openUrl(context, url)
    }
}

private fun share(context: Context, url: String, name: String) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
        .putExtra(android.content.Intent.EXTRA_SUBJECT, name).putExtra(android.content.Intent.EXTRA_TEXT, url)
    try { context.startActivity(android.content.Intent.createChooser(send, "Partager la vidéo").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (err: Exception) { toastOnMain(context, "Partage impossible.") }
}

@Composable
private fun VideosWindow(onClose: () -> Unit) {
    val context = LocalContext.current
    val bridge = remember { LocalClaudeBridge() }
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<VideoList?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var quality by remember { mutableStateOf(AiwaRepository.state.value.videoQuality) }
    var docs by remember { mutableStateOf<List<String>>(emptyList()) }
    var ticked by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        try {
            list = bridge.videos()
            val repo = list?.repo
            val had = Productions.has(context, repo, "video")
            val has = list?.videos?.isNotEmpty() == true
            if (repo != null && had != has) { Productions.set(context, repo, "video", has); AiwaWidget().updateAll(context) }
            if (repo != null) docs = Productions.documentFiles(repo)
        } catch (err: Exception) { error = err.message ?: "Liste indisponible." }
    }

    fun ask(format: String) {
        if (busy) return
        busy = true
        scope.launch {
            // the quality is chosen here, where the video is asked for: it goes to the brain just before the request
            switchOptions(context, bridge, videoQuality = quality)
            val sources = if (ticked.isEmpty()) " N'utilise aucun document du dossier docs/ : seulement ma création."
                else " Utilise ces documents du dossier docs/ (lis-les en entier) : " + ticked.sorted().joinToString(", ") { "docs/$it" } + "."
            val sent = sendAndTrack(
                context, bridge,
                "Fais une vidéo promotionnelle de ma création, au format $format.$sources Ne me pose aucune question : décide et va jusqu'au bout.",
                toastErrors = true,
            )
            busy = false
            if (sent) {
                toastOnMain(context, "Demande envoyée à Claude")
                onClose()
            }
        }
    }

    AiwaTheme {
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
                Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Vidéos de ta création", style = MaterialTheme.typography.titleMedium)
                    val current = list
                    fun play(video: com.aiwa.bridge.VideoItem) = context.startActivity(
                        android.content.Intent(context, VideoPlayerActivity::class.java)
                            .putExtra(VideoPlayerActivity.EXTRA_URL, video.url).putExtra(VideoPlayerActivity.EXTRA_TITLE, video.name),
                    )
                    when {
                        error != null -> Text(error ?: "", color = MaterialTheme.colorScheme.error)
                        current == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Chargement…")
                        }
                        current.repo == null -> Text("Choisis d'abord un dépôt (le bouton ⎇ du widget).")
                        current.videos.isEmpty() -> Text("Aucune vidéo pour l'instant dans ${current.repo}. Demande-en une ci-dessous ; elle apparaîtra ici dès que Claude l'aura déposée.")
                        else -> {
                            // The last production: a tap plays it, then its three buttons
                            val last = current.videos.first()
                            Text("Dernière production", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Button(onClick = { play(last) }, modifier = Modifier.fillMaxWidth()) { Text("▶  " + last.name + sizeLabel(last.size).let { if (it.isEmpty()) "" else " · $it" }, maxLines = 2) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    context.startActivity(
                                        android.content.Intent(context, CastActivity::class.java)
                                            .putExtra(CastActivity.EXTRA_URL, last.url).putExtra(CastActivity.EXTRA_TITLE, last.name),
                                    )
                                }, modifier = Modifier.weight(1f)) { Text("📺 Cast") }
                                OutlinedButton(onClick = { download(context, last.url, last.name) }, modifier = Modifier.weight(1f)) { Text("⇩ Download") }
                                OutlinedButton(onClick = { share(context, last.url, last.name) }, modifier = Modifier.weight(1f)) { Text("↗ Share") }
                            }
                        }
                    }
                    current?.repo?.let { repo ->
                        OutlinedButton(onClick = { openUrl(context, "https://github.com/$repo/tree/HEAD/docs/videos") }, modifier = Modifier.fillMaxWidth()) { Text("📁 Voir le dossier des vidéos") }
                    }
                    Text("Nouvelle vidéo de ma création", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    // The documents of docs/ to use, or none (a private repository cannot be listed here: nothing to tick, the creation alone counts)
                    if (docs.isEmpty()) Text("Documents du dossier docs/ : aucun trouvé (ou dépôt privé) — la vidéo part de ta création.", style = MaterialTheme.typography.bodySmall)
                    else {
                        Text("Documents du dossier docs/ à utiliser (aucun coché : ta création seule)", style = MaterialTheme.typography.bodySmall)
                        docs.forEach { name ->
                            Row(
                                Modifier.fillMaxWidth().clickable { ticked = if (name in ticked) ticked - name else ticked + name },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = name in ticked, onCheckedChange = { ticked = if (it) ticked + name else ticked - name })
                                Text(name, maxLines = 1)
                            }
                        }
                    }
                    QualityChoice(quality) { quality = it }
                    // A video made from a file (a CV, a pitch, a brochure): it belongs here, with the videos of the creation. The campaign (a site and a video for each target) is the envelope's.
                    OutlinedButton(
                        onClick = { context.startActivity(android.content.Intent(context, FileCreationActivity::class.java).putExtra("video", true)); onClose() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Une vidéo à partir d'un fichier") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { ask("9:16 (vertical)") }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("9:16") }
                        Button(onClick = { ask("16:9 (horizontal)") }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("16:9") }
                        Button(onClick = { ask("1:1 (carré)") }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("1:1") }
                    }
                    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
                }
            }
        }
    }
}
