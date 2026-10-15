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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.aiwa.bridge.LocalClaudeBridge
import kotlinx.coroutines.launch

/**
 * The agent: a dedicated Claude session that follows the repositories, the sessions and the loops, and talks with the person by voice only,
 * with the always-on listening. Here: make it, switch it on, the rhythm of its points, a word to it, and the proposal that waits.
 * A proposal is sent by saying "vas-y dis-lui" (the phone reads it back, then "c'est bon vas-y") or by the button below: never by itself.
 */
class AgentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent { AgentWindow(onClose = { finish() }) }
    }
}

@Composable
private fun AgentWindow(onClose: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val bridge = remember { LocalClaudeBridge() }
    val scope = rememberCoroutineScope()
    val agent = AiwaRepository.state.collectAsState().value.agent
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    // One action, then the state is read again so that the window and the widget follow.
    fun act(action: String, fields: Map<String, Any> = emptyMap(), then: () -> Unit = {}) {
        if (busy) return
        busy = true
        note = null
        scope.launch {
            val error = try { bridge.agent(action, fields) } catch (err: Exception) { err.message ?: "?" }
            if (error == null) then() else note = error
            try { BackendSync.refresh(bridge) } catch (err: Exception) { }
            AiwaWidget().updateAll(app)
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
                Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("L'agent", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Une session Claude à part. Elle suit tes dépôts, tes sessions et tes boucles, et te parle à voix haute. On l'ouvre en disant « mon super agent » (écoute de réveil allumée) ou avec le bouton ; « repasse en manuel » la ferme. " +
                            "Elle n'écrit jamais dans un dépôt et n'envoie rien toute seule : dis « vas-y dis-lui », le téléphone relit, tu dis « c'est bon vas-y ».",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (agent == null) {
                        Text("Le cerveau d'Aiwa dans Termux n'est pas joignable (ou trop ancien : cd ~/aiwa_store && git pull).")
                    } else if (agent.session == null && agent.llm != "ready") {
                        Text("Elle sera créée sur le dépôt choisi dans le widget (il ne sert que d'adresse).")
                        Button(onClick = { act("create") }, enabled = !busy && !agent.creating, modifier = Modifier.fillMaxWidth()) {
                            Text(if (agent.creating) "Création…" else "Créer l'agent")
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Switch(checked = agent.enabled, enabled = !busy, onCheckedChange = { on ->
                                // The second listening: the first one waits while it is open, and takes over again when it is closed.
                                if (on) WakeWordService.openAgentMode(app) else WakeWordService.closeAgentMode(app)
                            })
                            Text(if (agent.enabled) "Agent ouvert : tout ce que tu dis va à lui (écoute de réveil en pause)" else "Agent fermé : l'écoute de réveil attend « mon super agent »")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { act("set", mapOf("interval" to (agent.interval - 5))) }, enabled = !busy && agent.interval > 5) { Text("−") }
                            Text("Un point toutes les ${agent.interval} min (s'il y a du nouveau)")
                            OutlinedButton(onClick = { act("set", mapOf("interval" to (agent.interval + 5))) }, enabled = !busy && agent.interval < 240) { Text("+") }
                        }
                        val voiceLine = when (agent.voice) {
                            "ready" -> "Voix : naturelle (Piper), sur le téléphone."
                            "installing" -> "Voix : installation en cours (environ 80 Mo, quelques minutes). En attendant, la voix du téléphone."
                            "error" -> "Voix : l'installation a échoué. Voix du téléphone."
                            else -> "Voix : celle du téléphone. La voix naturelle s'installe une fois (environ 80 Mo)."
                        }
                        Text(voiceLine, style = MaterialTheme.typography.bodySmall)
                        if (agent.voice == "none" || agent.voice == "error") {
                            OutlinedButton(onClick = { scope.launch { try { bridge.voiceSetup(); BackendSync.refresh(bridge) } catch (err: Exception) { note = err.message ?: "?" } } }, modifier = Modifier.fillMaxWidth()) { Text("Installer la voix naturelle") }
                        }
                        OutlinedButton(onClick = { act("now") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Faire le point maintenant") }
                        OutlinedTextField(value = text, onValueChange = { text = it }, enabled = !busy, label = { Text("Dire à l'agent") }, modifier = Modifier.fillMaxWidth())
                        Button(onClick = { act("ask", mapOf("text" to text.trim())) { text = "" } }, enabled = text.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) { Text("Envoyer à l'agent") }
                        Text("À voix haute : « dis à l'agent … ».", style = MaterialTheme.typography.bodySmall)
                        agent.say?.let { Text("Il a dit : ${it.text}", style = MaterialTheme.typography.bodyMedium) }
                        agent.command?.let { c ->
                            Text(c.readBack, style = MaterialTheme.typography.bodyMedium)
                            Text("Dis « vas-y, fais-le » (agent ouvert), le téléphone relit puis tu confirmes.", style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { act("cancel", mapOf("id" to c.id)) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Annuler ces réglages") }
                        }
                        agent.proposal?.let { p ->
                            Text("Proposition pour ${p.sessionTitle} :", style = MaterialTheme.typography.titleSmall)
                            Text(p.message, style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = { act("confirm", mapOf("id" to p.id)) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Envoyer à ${p.sessionTitle}") }
                            OutlinedButton(onClick = { act("cancel", mapOf("id" to p.id)) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Annuler la proposition") }
                        }
                    }
                    if (agent != null) {
                        Text(
                            when (agent.llm) {
                                "ready" -> "Clé Anthropic : en place (réglages → Clé Anthropic). L'agent répond en une ou deux secondes."
                                "installing" -> "Clé Anthropic : installation du paquet en cours dans Termux…"
                                "error" -> "Clé Anthropic : problème (réglages → Clé Anthropic)."
                                else -> "Clé Anthropic (facultative) : à régler dans les réglages ; sans elle l'agent passe par une session cloud, plus lente."
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    agent?.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
                }
            }
        }
    }
}
