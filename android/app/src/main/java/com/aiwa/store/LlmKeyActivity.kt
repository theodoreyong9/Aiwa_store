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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import com.aiwa.bridge.LocalClaudeBridge
import kotlinx.coroutines.launch

/**
 * The person's own Anthropic key, entered ONCE, from the settings: it serves the speaker (the voice commands the keyword parser did not understand) and the super agent at the same
 * time (they share the one key kept in the brain on this phone). Optional: without it both go through a cloud session, slower.
 */
class LlmKeyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            val app = applicationContext
            val bridge = remember { LocalClaudeBridge() }
            val scope = rememberCoroutineScope()
            val llm = AiwaRepository.state.collectAsState().value.agent?.llm ?: "none"
            var key by remember { mutableStateOf("") }
            var note by remember { mutableStateOf<String?>(null) }
            var busy by remember { mutableStateOf(false) }
            MaterialTheme {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp), tonalElevation = 6.dp,
                        modifier = Modifier.padding(24.dp).fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Clé Anthropic", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Une seule clé pour le parleur (les ordres vocaux que les mots-clés ratent) et pour le super agent : il répond alors en une ou deux secondes. Facturée par Anthropic, à l'usage. Facultative : sans elle, tout passe par une session cloud, plus lente.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                when (llm) {
                                    "ready" -> "En place."
                                    "installing" -> "Installation du paquet en cours dans Termux…"
                                    "error" -> "Problème : la clé ou le paquet ne marche pas (voir la fenêtre de l'agent)."
                                    else -> "Absente."
                                },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (llm == "none" || llm == "error") {
                                OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("sk-ant-…") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                                Text("Elle reste dans le cerveau Termux de ce téléphone (fichier lisible par toi seul) et ne va qu'à Anthropic : jamais dans un dépôt.", style = MaterialTheme.typography.bodySmall)
                                Button(onClick = {
                                    busy = true
                                    scope.launch {
                                        val error = try { bridge.llmKey(key.trim()) } catch (err: Exception) { err.message ?: "?" }
                                        if (error == null) key = "" else note = error
                                        try { BackendSync.refresh(bridge) } catch (err: Exception) { }
                                        AiwaWidget().updateAll(app)
                                        busy = false
                                    }
                                }, enabled = key.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) { Text("Enregistrer la clé") }
                            } else if (llm == "ready") {
                                OutlinedButton(onClick = {
                                    busy = true
                                    scope.launch {
                                        try { bridge.llmKey(null); BackendSync.refresh(bridge) } catch (err: Exception) { note = err.message ?: "?" }
                                        AiwaWidget().updateAll(app)
                                        busy = false
                                    }
                                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Retirer la clé") }
                            }
                            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                            OutlinedButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
                        }
                    }
                }
            }
        }
    }
}
