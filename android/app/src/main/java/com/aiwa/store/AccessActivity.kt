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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.aiwa.bridge.DeviceCode
import com.aiwa.bridge.GitHubDeviceFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Who may use this build (AccessGate): the account that is signed in and what it may do, and the sign-in (GitHub's device flow:
 * a short code typed once on github.com). The Store opens this window instead of itself when the account has no access to it,
 * and the widget opens it when nothing is allowed.
 */
class AccessActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent { AccessWindow(onClose = { finish() }) }
    }
}

@Composable
private fun AccessWindow(onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    val state by AiwaRepository.state.collectAsState()
    var code by remember { mutableStateOf<DeviceCode?>(null) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val clientId = remember { AccessGate.clientId(app) }

    LaunchedEffect(Unit) {
        AccessGate.refresh(app, force = true)
        AiwaWidget().updateAll(app)
    }

    fun signIn() {
        if (busy) return
        busy = true
        note = null
        scope.launch {
            try {
                val flow = GitHubDeviceFlow()
                val device = withContext(Dispatchers.IO) { flow.start(clientId, "public_repo") }
                code = device
                copyToClipboard(context, device.userCode)
                if (device.verificationUri.startsWith("https://github.com/")) openUrl(context, device.verificationUri)
                val token = withContext(Dispatchers.IO) { flow.awaitToken(clientId, device) }
                AccessGate.signedIn(app, token)
                AiwaWidget().updateAll(app)
                code = null
            } catch (err: Exception) {
                note = err.message ?: "La connexion à GitHub a échoué."
                code = null
            }
            busy = false
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
                    Text("Accès à Aiwa", style = MaterialTheme.typography.titleMedium)
                    val notice = accessNotice(state, 2)
                    val shown = code
                    when {
                        !state.accessEnforced -> Text("Aucune restriction : tout est ouvert.")
                        notice == null -> Text("Compte « ${state.accessLogin} » : accès complet.")
                        else -> Text(notice)
                    }
                    if (shown != null) {
                        Text("Tape ce code sur github.com/login/device (il est copié) :", style = MaterialTheme.typography.bodyMedium)
                        Text(shown.userCode, style = MaterialTheme.typography.headlineMedium)
                    }
                    val needsSignIn = state.accessEnforced && (state.accessLogin == null || state.accessLevel == 0)
                    if (needsSignIn && shown == null) {
                        if (clientId.isEmpty()) {
                            Text(
                                "Cette version n'a pas encore d'application GitHub pour la connexion (deployment.json, github.clientId).",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            Button(onClick = { signIn() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                Text(if (state.accessLogin == null) "Se connecter avec GitHub" else "Changer de compte GitHub")
                            }
                        }
                    }
                    note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    OutlinedButton(
                        onClick = { scope.launch { AccessGate.refresh(app, force = true); AiwaWidget().updateAll(app) } },
                        enabled = !busy, modifier = Modifier.fillMaxWidth(),
                    ) { Text("Vérifier à nouveau") }
                    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
                }
            }
        }
    }
}
