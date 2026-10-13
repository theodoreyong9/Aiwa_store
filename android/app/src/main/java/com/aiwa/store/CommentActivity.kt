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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import com.aiwa.bridge.LocalClaudeBridge
import kotlinx.coroutines.launch

/**
 * The widget's comment button: a comment for the critic of the loops. It is kept by the backend and reaches the critic with its next turn: it starts nothing,
 * only the person's own message starts a loop.
 */
class CommentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent { CommentWindow(onClose = { finish() }) }
    }
}

@Composable
private fun CommentWindow(onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val send: () -> Unit = {
        if (!busy && text.isNotBlank()) {
            busy = true
            scope.launch {
                try { LocalClaudeBridge().comment(text.trim()); onClose() } catch (err: Exception) { note = err.message ?: "Le commentaire n'est pas parti."; busy = false }
            }
        }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    MaterialTheme {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp), tonalElevation = 6.dp,
                modifier = Modifier.padding(24.dp).padding(top = 48.dp).fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Commentaire pour le critique", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = text, onValueChange = { text = it }, enabled = !busy,
                        label = { Text("Ce qu'il doit prendre en compte") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                                                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                    Button(onClick = send, enabled = text.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Envoi…" else "Envoyer") }
                    Text("Il le lira au prochain tour de la boucle : rien ne démarre tant que tu n'as pas lancé la boucle.", style = MaterialTheme.typography.bodySmall)
                    note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}
