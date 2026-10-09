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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.glance.appwidget.updateAll
import com.aiwa.bridge.AskInfo
import com.aiwa.bridge.LocalClaudeBridge
import kotlinx.coroutines.launch

/**
 * The question Claude asked through the widget (it cannot ask in its own conversation: nobody reads it).
 * The answer goes back to the session as its next message. Closing the window leaves the question
 * waiting: the widget keeps showing it.
 */
class AskActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent { AskWindow(onClose = { finish() }) }
    }
}

@Composable
private fun AskWindow(onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val bridge = remember { LocalClaudeBridge() }
    val scope = rememberCoroutineScope()
    val ask: AskInfo? = AiwaRepository.state.collectAsState().value.ask
    var free by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    fun answer(info: AskInfo, text: String) {
        if (busy || text.isBlank()) return
        busy = true
        note = null
        scope.launch {
            try {
                bridge.askAnswer(info.id, text.trim())
                BackendSync.refresh(bridge)
                AiwaWidget().updateAll(app)
                onClose()
            } catch (err: Exception) {
                note = err.message ?: "La réponse n'est pas partie."
                busy = false
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
                    Text("Claude te demande", style = MaterialTheme.typography.titleMedium)
                    if (ask == null) {
                        Text("Plus aucune question en attente.")
                        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
                    } else {
                        Text(ask.question, style = MaterialTheme.typography.bodyLarge)
                        ask.options.forEach { option ->
                            OutlinedButton(onClick = { answer(ask, option) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(option) }
                        }
                        OutlinedTextField(
                            value = free,
                            onValueChange = { free = it },
                            enabled = !busy,
                            label = { Text("Ou ta propre réponse") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = { answer(ask, free) }, enabled = free.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) {
                            Text(if (busy) "Envoi…" else "Répondre")
                        }
                        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}
