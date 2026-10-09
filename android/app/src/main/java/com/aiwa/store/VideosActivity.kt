package com.aiwa.store
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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

private fun sizeLabel(bytes: Long) = if (bytes >= 1_000_000) "%.1f Mo".format(bytes / 1_000_000.0) else "${bytes / 1000} Ko"

@Composable
private fun VideosWindow(onClose: () -> Unit) {
    val context = LocalContext.current
    val bridge = remember { LocalClaudeBridge() }
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<VideoList?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try { list = bridge.videos() } catch (err: Exception) { error = err.message ?: "Liste indisponible." }
    }

    fun ask(format: String) {
        if (busy) return
        busy = true
        scope.launch {
            val sent = sendAndTrack(
                context, bridge,
                "Fais une vidéo promotionnelle de ma création, au format $format. Si un choix ne t'appartient pas, demande-le moi par la question du widget.",
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { ask("9:16 (vertical)") }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("9:16") }
                            Button(onClick = { ask("16:9 (horizontal)") }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("16:9") }
                            Button(onClick = { ask("1:1 (carré)") }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("1:1") }
                        }
                    }
                    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
                }
            }
        }
    }
}
