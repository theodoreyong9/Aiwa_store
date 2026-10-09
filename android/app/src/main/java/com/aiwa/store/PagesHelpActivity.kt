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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * The deployment of the site stopped on a setting of GitHub that only the owner can change (the failed run says which: see
 * aiwa_github.pages_hint). Neither Claude nor Aiwa can change it (Aiwa never asks for a token), so the person is told what it is,
 * in steps, with a button to the page of that setting. It is what the widget's globe and Actions buttons, the status line and the
 * notification open while the problem stands.
 */
class PagesHelpActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent { PagesHelpWindow(onClose = { finish() }) }
    }
}

@Composable
private fun PagesHelpWindow(onClose: () -> Unit) {
    val context = LocalContext.current
    val state by AiwaRepository.state.collectAsState()
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
                Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val title = state.ciHintTitle
                    if (title == null) {
                        Text("Plus rien à régler sur GitHub.", style = MaterialTheme.typography.titleMedium)
                    } else {
                        Text("⚠ Un réglage de GitHub à faire", style = MaterialTheme.typography.titleLarge)
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        state.ciHintSteps.forEachIndexed { i, step -> Text("${i + 1}. $step", style = MaterialTheme.typography.bodyLarge) }
                        Text(
                            "C'est le seul réglage que ni Claude ni Aiwa ne peuvent faire à ta place : il demande tes droits sur le dépôt.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        state.ciHintUrl?.let { url ->
                            Button(onClick = { openUrl(context, url) }, modifier = Modifier.fillMaxWidth()) { Text("Ouvrir ce réglage sur GitHub") }
                        }
                    }
                    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
                }
            }
        }
    }
}
