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
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import com.aiwa.bridge.LocalClaudeBridge
import com.aiwa.bridge.LoopPair
import com.aiwa.bridge.LoopsInfo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * « Boucles builder / critique »: the loops between two sessions. A pair is a builder and a critic; the backend carries the messages between them (each session signals
 * that its turn is done, the backend tells the other). Here: the limit of round trips, the pairs, and for each one start, stop, relaunch (same starting point, counter at
 * zero), new loop (a new starting point) and rollback (the repository goes back to where it was at the start of that pair's loop, and so does its whole chain).
 */
class LoopsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent { LoopsWindow(onClose = { finish() }) }
    }
}

private fun dateLabel(seconds: Long?): String = if (seconds == null) "—" else SimpleDateFormat("d MMM HH:mm", Locale.FRENCH).format(Date(seconds * 1000))

private fun stateLabel(p: LoopPair): String = when (p.state) {
    "running" -> "en cours : tour ${p.rounds + 1}/${p.limit}, au tour du ${if (p.turn == "critic") "critique" else "builder"}"
    "stopped" -> "arrêtée (${p.rounds}/${p.limit})"
    "done" -> "terminée : ${p.rounds}/${p.limit} allers-retours"
    else -> "pas démarrée"
}

@Composable
private fun LoopsWindow(onClose: () -> Unit) {
    val bridge = remember { LocalClaudeBridge() }
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val state by AiwaRepository.state.collectAsState()
    var info by remember { mutableStateOf<LoopsInfo?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pickingBuilder by remember { mutableStateOf(false) }
    var builderId by remember { mutableStateOf<String?>(null) }
    var rollbackOf by remember { mutableStateOf<LoopPair?>(null) }

    fun refresh() {
        scope.launch {
            try { info = bridge.loops(); message = null } catch (err: Exception) { message = "Boucles indisponibles : ${err.message ?: "?"}" }
        }
    }
    fun act(action: String, fields: Map<String, String> = emptyMap(), done: String? = null) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val refused = bridge.loopsAction(action, fields)
                message = refused ?: done
                BackendSync.refresh(bridge)
            } catch (err: Exception) { message = "Impossible : ${err.message ?: "?"}" }
            busy = false
            refresh()
            AiwaWidget().updateAll(appContext)
        }
    }
    LaunchedEffect(Unit) { refresh() }

    AiwaTheme {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp), tonalElevation = 6.dp,
                modifier = Modifier.padding(16.dp).fillMaxWidth()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            ) {
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Boucles builder / critique", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Un builder travaille, un critique relit, Aiwa porte les messages entre les deux sessions. Chaque paire a un sens (jamais deux rôles avec le même partenaire) ; une session peut avoir autant de paires que tu veux.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val current = info
                    // The limit of round trips, in the settings of the loops
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Limite : ${current?.limit ?: "…"} allers-retours", modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { current?.let { act("limit", mapOf("limit" to (maxOf(1, it.limit - 1)).toString())) } }, enabled = current != null && !busy) { Text("−") }
                        OutlinedButton(onClick = { current?.let { act("limit", mapOf("limit" to (minOf(50, it.limit + 1)).toString())) } }, enabled = current != null && !busy) { Text("+") }
                    }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                    val rollback = rollbackOf
                    if (rollback != null && current != null) {
                        // The chain, with the date each pair began, to choose from: what comes after the chosen date is undone
                        val chain = current.chains.firstOrNull { c -> c.any { it.id == rollback.id } } ?: emptyList()
                        val chosenStart = rollback.started ?: 0L
                        Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Revenir au départ de la paire ${rollback.id} (${dateLabel(rollback.started)})", style = MaterialTheme.typography.titleSmall)
                                Text("Le dépôt retrouve l'état de ce moment, dans un nouveau commit : l'historique reste. Toute la chaîne revient au même endroit :", style = MaterialTheme.typography.bodySmall)
                                chain.forEach { entry ->
                                    val pair = current.pairs.firstOrNull { it.id == entry.id }
                                    val later = (entry.started ?: 0L) > chosenStart
                                    Text(
                                        (if (entry.id == rollback.id) "▶ " else "· ") + entry.id + " " + (pair?.let { it.builderTitle + " ▸ " + it.criticTitle } ?: "") +
                                            " — départ " + dateLabel(entry.started) + if (later) " (démarrée après : son travail est défait)" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Text("Choisir une autre paire de la chaîne change la date de retour :", style = MaterialTheme.typography.bodySmall)
                                chain.filter { it.id != rollback.id && it.started != null }.forEach { entry ->
                                    OutlinedButton(onClick = { rollbackOf = current.pairs.firstOrNull { it.id == entry.id } }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Plutôt au départ de ${entry.id} (${dateLabel(entry.started)})")
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { act("rollback", mapOf("id" to rollback.id), "Retour demandé : le builder de ${rollback.id} remet le dépôt au départ."); rollbackOf = null }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Confirmer le retour") }
                                    OutlinedButton(onClick = { rollbackOf = null }, modifier = Modifier.weight(1f)) { Text("Annuler") }
                                }
                            }
                        }
                    } else if (current == null) {
                        Text("Chargement…")
                    } else if (current.pairs.isEmpty()) {
                        Text("Aucune paire pour l'instant.")
                    } else {
                        current.pairs.forEach { p ->
                            Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("${p.id} · ${p.builderTitle} ▸ ${p.criticTitle}", style = MaterialTheme.typography.titleSmall)
                                    Text("builder ▸ critique · " + stateLabel(p) + " · départ " + dateLabel(p.started) + (p.rolledBack?.let { " · retour le " + dateLabel(it) } ?: ""), style = MaterialTheme.typography.bodySmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (p.state == "running") {
                                            Button(onClick = { act("stop", mapOf("id" to p.id), "Boucle ${p.id} arrêtée.") }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Arrêter") }
                                        } else if (p.state == "idle") {
                                            Button(onClick = { act("start", mapOf("id" to p.id), "Boucle ${p.id} démarrée : parle maintenant au builder.") }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Démarrer") }
                                        } else {
                                            Button(onClick = { act("relaunch", mapOf("id" to p.id), "Boucle ${p.id} relancée, compteur à zéro.") }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Relancer") }
                                            OutlinedButton(onClick = { act("start", mapOf("id" to p.id), "Nouvelle boucle ${p.id} : nouveau départ.") }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Nouvelle boucle") }
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (p.started != null) OutlinedButton(onClick = { rollbackOf = p }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Revenir en arrière") }
                                        if (p.state != "running") OutlinedButton(onClick = { act("delete", mapOf("id" to p.id)) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Supprimer") }
                                    }
                                }
                            }
                        }
                    }

                    // A new pair: the builder, then the critic, among the sessions Aiwa knows
                    if (rollbackOf == null) {
                        if (!pickingBuilder) {
                            OutlinedButton(onClick = { pickingBuilder = true; builderId = null }, modifier = Modifier.fillMaxWidth()) { Text("Nouvelle paire") }
                        } else {
                            Text(if (builderId == null) "Quelle session construit ? (le builder)" else "Quelle session critique ? (le critique)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            val taken = current?.pairs?.filter { it.builder == builderId || it.critic == builderId }?.flatMap { listOf(it.builder, it.critic) }?.toSet() ?: emptySet()
                            val sessions = state.cloudSessions.filter { builderId == null || (it.id != builderId && it.id !in taken) }
                            if (sessions.isEmpty()) Text("Aucune session disponible : crée-en une depuis le widget (sélecteur de session).", style = MaterialTheme.typography.bodySmall)
                            sessions.forEach { s ->
                                OutlinedButton(
                                    onClick = {
                                        val b = builderId
                                        if (b == null) builderId = s.id
                                        else { act("create", mapOf("builder" to b, "critic" to s.id), "Paire créée."); pickingBuilder = false; builderId = null }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(s.title, maxLines = 1) }
                            }
                            OutlinedButton(onClick = { pickingBuilder = false; builderId = null }, modifier = Modifier.fillMaxWidth()) { Text("Annuler") }
                        }
                    }
                    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
                }
            }
        }
    }
}

