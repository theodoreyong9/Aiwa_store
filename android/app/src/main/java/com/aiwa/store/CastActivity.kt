package com.aiwa.store
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.aiwa.bridge.Dlna
import com.aiwa.bridge.looksLikeVideo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * « Envoyer à la TV »: casts a video to a television (or any DLNA / UPnP renderer) of the same Wi-Fi. The phone looks for the renderers, downloads the video, serves it itself
 * over http (a television reads that better than a github.com release address) and tells the chosen renderer to play it. The screen has to stay open while it plays (it is
 * the phone that serves the file). If nothing is found, the screen cast of Android is offered.
 */
class CastActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Vidéo Aiwa" }
        setContent { CastWindow(url, title, onClose = { finish() }) }
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
    }
}

@Composable
private fun CastWindow(url: String, title: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf("search") }          // search, choose, prepare, play
    var devices by remember { mutableStateOf<List<Dlna.Renderer>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var chosen by remember { mutableStateOf<Dlna.Renderer?>(null) }
    var server by remember { mutableStateOf<LocalMediaServer?>(null) }

    fun search() {
        phase = "search"; message = null
        scope.launch {
            devices = try { CastEngine.discover(context) } catch (err: Exception) { emptyList() }
            phase = "choose"
        }
    }
    fun cast(renderer: Dlna.Renderer) {
        chosen = renderer; phase = "prepare"; message = "Je prépare la vidéo…"
        scope.launch {
            try {
                if (!url.startsWith("https://")) throw IllegalStateException("cette vidéo n'a pas d'adresse sûre")
                val file = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "share").apply { mkdirs() }
                    val target = File(dir, "cast.mp4")
                    val connection = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 60_000; instanceFollowRedirects = true }
                    connection.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
                    val head = target.inputStream().use { s -> ByteArray(12).also { s.read(it) } }
                    check(looksLikeVideo(head, target.length())) { "le fichier téléchargé n'est pas une vidéo" }
                    target
                }
                val local = CastEngine.localAddressToward(renderer.host) ?: throw IllegalStateException("le téléphone n'est pas sur le même réseau que ${renderer.name}")
                server?.stop()
                val media = LocalMediaServer(file).also { it.start() }
                server = media
                val refused = CastEngine.play(renderer, "http://$local:${media.port}/video.mp4", title)
                if (refused == null) { phase = "play"; message = null } else { media.stop(); server = null; phase = "choose"; message = refused }
            } catch (err: Exception) {
                phase = "choose"; message = "Envoi impossible : ${err.message ?: err.javaClass.simpleName}"
            }
        }
    }
    LaunchedEffect(Unit) { search() }
    // Closing the window ends the cast: the phone is the one that serves the file
    DisposableEffect(Unit) {
        onDispose {
            server?.stop()
            chosen?.let { r -> CoroutineScope(Dispatchers.IO).launch { CastEngine.stop(r) } }
        }
    }

    AiwaTheme {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (phase != "play") onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp), tonalElevation = 6.dp,
                modifier = Modifier.padding(16.dp).fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            ) {
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Envoyer à la TV", style = MaterialTheme.typography.titleMedium)
                    Text(title, style = MaterialTheme.typography.bodySmall)
                    message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (phase == "choose") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) }
                    when (phase) {
                        "search" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Je cherche les téléviseurs du Wi-Fi…")
                        }
                        "choose" -> {
                            if (devices.isEmpty()) {
                                Text("Aucun téléviseur trouvé. Le téléphone et la TV doivent être sur le même Wi-Fi (pas un réseau invité), et la TV doit accepter DLNA / le partage multimédia.", style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text("Choisis l'appareil :", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                devices.forEach { d -> Button(onClick = { cast(d) }, modifier = Modifier.fillMaxWidth()) { Text("📺  " + d.name, maxLines = 1) } }
                            }
                            OutlinedButton(onClick = { search() }, modifier = Modifier.fillMaxWidth()) { Text("↻  Chercher de nouveau") }
                            // the other road: the screen cast of Android, which many phones have
                            OutlinedButton(
                                onClick = {
                                    val opened = try { context.startActivity(Intent(Settings.ACTION_CAST_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (err: Exception) { false }
                                    if (!opened) toastOnMain(context.applicationContext, "Ce téléphone n'a pas de diffusion d'écran dans les réglages : cherche « Smart View » ou « Diffuser ».")
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Diffusion d'écran d'Android") }
                        }
                        "prepare" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Préparation et envoi à ${chosen?.name ?: "la TV"}…")
                        }
                        "play" -> {
                            Text("▶  Lecture sur ${chosen?.name}. Garde cet écran ouvert : c'est le téléphone qui envoie la vidéo.", style = MaterialTheme.typography.bodyMedium)
                            Button(
                                onClick = {
                                    val r = chosen
                                    server?.stop(); server = null
                                    if (r != null) scope.launch { CastEngine.stop(r) }
                                    phase = "choose"; message = "Lecture arrêtée."
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Arrêter la lecture") }
                        }
                    }
                    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(if (phase == "play") "Arrêter et fermer" else "Fermer") }
                }
            }
        }
    }
}
