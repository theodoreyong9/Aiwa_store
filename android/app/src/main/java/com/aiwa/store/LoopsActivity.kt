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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import com.aiwa.bridge.LocalClaudeBridge
import com.aiwa.bridge.LoopLink
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

private fun linkLabel(l: LoopLink): String = when (l.state) {
    "proposed" -> "invitation envoyée : ${l.receiverTitle} n'a pas encore répondu"
    "accepted" -> "accepté : tour ${l.rounds + 1}/${l.limit}, au tour ${if (l.turn == "receiver") "du récepteur" else "de l'émetteur"}"
    "refused" -> "refusé par ${l.receiverTitle}"
    "stopped" -> "arrêté (${l.rounds}/${l.limit})"
    "done" -> "terminé : ${l.rounds}/${l.limit} allers-retours"
    else -> l.state
}

@Composable
private fun LoopsWindow(onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
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
    var showGraph by remember { mutableStateOf(false) }
    var linkSender by remember { mutableStateOf<String?>(null) }
    var linkReceiver by remember { mutableStateOf<String?>(null) }
    var linkPicking by remember { mutableStateOf(false) }
    var linkText by remember { mutableStateOf("Salut, qu'est-ce que tu fais ? J'ai envie de t'aider.") }

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
                    if (current != null && (current.pairs.isNotEmpty() || current.links.isNotEmpty())) {
                        OutlinedButton(onClick = { showGraph = !showGraph }, modifier = Modifier.fillMaxWidth()) { Text(if (showGraph) "Masquer le graphe" else "Voir le graphe des boucles") }
                        if (showGraph) LoopsGraph(current.pairs, current.links)
                    }

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
                                    OutlinedButton(
                                        onClick = { context.startActivity(android.content.Intent(context, CritiquesActivity::class.java).putExtra("pair", p.id)) },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text("Historique des critiques") }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (p.started != null) OutlinedButton(onClick = { rollbackOf = p }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Revenir en arrière") }
                                        if (p.state != "running") OutlinedButton(onClick = { act("delete", mapOf("id" to p.id)) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Supprimer") }
                                    }
                                }
                            }
                        }
                    }

                    // The other style of loop: exchanges between CRITICS (no producer). The sender opens, the receiver accepts or refuses in its own session.
                    if (rollbackOf == null && current != null) {
                        Text("Échanges entre critiques", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "Seuls des critiques (une session qui est le critique d'une paire) peuvent s'écrire : pour discuter, ou pour qu'un critique aide en parallèle celui qui travaille déjà pour un producteur. " +
                                "L'émetteur dit d'abord bonjour et propose son aide ; le récepteur accepte (ou non) cette conversation dans sa session. Ce que l'un écrit à l'autre est une contribution, jamais un ordre.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (current.links.isEmpty()) Text("Aucun échange pour l'instant.", style = MaterialTheme.typography.bodySmall)
                        current.links.forEach { l ->
                            Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("${l.id} · ${l.senderTitle} ▸ ${l.receiverTitle}", style = MaterialTheme.typography.titleSmall)
                                    Text(linkLabel(l), style = MaterialTheme.typography.bodySmall)
                                    Text("« ${l.opening} »", style = MaterialTheme.typography.bodySmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (l.state == "proposed") {
                                            Button(onClick = { act("link_answer", mapOf("id" to l.id, "accept" to "true"), "Accepté à la place de ${l.receiverTitle}.") }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Accepter à sa place") }
                                            OutlinedButton(onClick = { act("link_answer", mapOf("id" to l.id, "accept" to "false"), "Refusé.") }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Refuser") }
                                        } else if (l.state == "accepted") {
                                            Button(onClick = { act("link_stop", mapOf("id" to l.id), "Échange ${l.id} arrêté.") }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Arrêter") }
                                        }
                                        if (l.state != "accepted") OutlinedButton(onClick = { act("link_delete", mapOf("id" to l.id)) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Supprimer") }
                                        if (l.state == "proposed") OutlinedButton(onClick = { act("link_stop", mapOf("id" to l.id), "Invitation retirée.") }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Retirer") }
                                    }
                                }
                            }
                        }
                        if (!linkPicking) {
                            OutlinedButton(onClick = { linkPicking = true; linkSender = null; linkReceiver = null }, enabled = current.critics.size >= 2, modifier = Modifier.fillMaxWidth()) { Text("Nouvel échange entre critiques") }
                            if (current.critics.size < 2) Text("Il faut au moins deux critiques (deux sessions qui sont le critique d'une paire).", style = MaterialTheme.typography.bodySmall)
                        } else if (linkSender == null || linkReceiver == null) {
                            Text(if (linkSender == null) "Quel critique écrit en premier ? (l'émetteur)" else "À quel critique ? (le récepteur)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            current.critics.filter { it.id != linkSender }.forEach { c ->
                                OutlinedButton(onClick = { if (linkSender == null) linkSender = c.id else linkReceiver = c.id }, modifier = Modifier.fillMaxWidth()) { Text(c.title, maxLines = 1) }
                            }
                            OutlinedButton(onClick = { linkPicking = false }, modifier = Modifier.fillMaxWidth()) { Text("Annuler") }
                        } else {
                            OutlinedTextField(value = linkText, onValueChange = { linkText = it }, label = { Text("Ce que l'émetteur dit en premier") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                            Button(
                                onClick = {
                                    act("link_create", mapOf("sender" to (linkSender ?: ""), "receiver" to (linkReceiver ?: ""), "opening" to linkText), "Invitation envoyée : le récepteur peut accepter dans sa session.")
                                    linkPicking = false; linkSender = null; linkReceiver = null
                                },
                                enabled = !busy && linkText.isNotBlank(), modifier = Modifier.fillMaxWidth(),
                            ) { Text("Envoyer l'invitation") }
                            OutlinedButton(onClick = { linkPicking = false; linkSender = null; linkReceiver = null }, modifier = Modifier.fillMaxWidth()) { Text("Annuler") }
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



/**
 * The graph of the loops: the sessions are the nodes, each pair an arrow from the builder to the critic (green running, orange stopped, blue finished, grey not started), with its name and its
 * round; each exchange between critics a DASHED purple arrow from the sender to the receiver (pale until accepted). The sessions are placed on a circle; what is connected shows at a glance (a chain, a hub, a cycle).
 */
@Composable
private fun LoopsGraph(pairs: List<LoopPair>, links: List<LoopLink> = emptyList()) {
    val nodes = (pairs.flatMap { listOf(it.builder to it.builderTitle, it.critic to it.criticTitle) } + links.flatMap { listOf(it.sender to it.senderTitle, it.receiver to it.receiverTitle) }).distinctBy { it.first }
    val linkColor = Color(0xFF8E5BC0)
    val density = LocalDensity.current
    val nodeW = 92.dp
    val nodeH = 36.dp
    fun colorOf(p: LoopPair) = when (p.state) {
        "running" -> Color(0xFF2E7D5A)
        "stopped" -> Color(0xFFD08A2E)
        "done" -> Color(0xFF3F7FBF)
        else -> Color(0xFF8E8EA0)
    }
    BoxWithConstraints(Modifier.fillMaxWidth().height(280.dp)) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val nodeWPx = with(density) { nodeW.toPx() }
        val nodeHPx = with(density) { nodeH.toPx() }
        val radius = minOf(widthPx - nodeWPx, heightPx - nodeHPx) / 2f
        val centre = Offset(widthPx / 2f, heightPx / 2f)
        val position = nodes.mapIndexed { i, node ->
            val angle = -PI / 2 + 2 * PI * i / maxOf(1, nodes.size)
            node.first to (if (nodes.size == 1) centre else Offset(centre.x + (radius * cos(angle)).toFloat(), centre.y + (radius * sin(angle)).toFloat()))
        }.toMap()
        Canvas(Modifier.fillMaxSize()) {
            pairs.forEach { p ->
                val from = position[p.builder] ?: return@forEach
                val to = position[p.critic] ?: return@forEach
                val dx = to.x - from.x
                val dy = to.y - from.y
                val length = hypot(dx, dy)
                if (length < 1f) return@forEach
                val ux = dx / length
                val uy = dy / length
                // the arrow stops at the border of the critic's box
                val back = minOf(nodeWPx / 2f / maxOf(0.01f, kotlin.math.abs(ux)), nodeHPx / 2f / maxOf(0.01f, kotlin.math.abs(uy))) + 4f
                val start = Offset(from.x + ux * 20f, from.y + uy * 20f)
                val end = Offset(to.x - ux * back, to.y - uy * back)
                drawLine(colorOf(p), start, end, strokeWidth = 4f)
                val angle = atan2(uy, ux)
                val head = Path().apply {
                    moveTo(end.x, end.y)
                    lineTo((end.x - 22f * cos(angle - 0.45)).toFloat(), (end.y - 22f * sin(angle - 0.45)).toFloat())
                    lineTo((end.x - 22f * cos(angle + 0.45)).toFloat(), (end.y - 22f * sin(angle + 0.45)).toFloat())
                    close()
                }
                drawPath(head, colorOf(p))
            }
            // the exchanges between critics: dashed, with an arrowhead from the sender to the receiver
            links.filter { it.state != "refused" }.forEach { l ->
                val from = position[l.sender] ?: return@forEach
                val to = position[l.receiver] ?: return@forEach
                val dx = to.x - from.x
                val dy = to.y - from.y
                val length = hypot(dx, dy)
                if (length < 1f) return@forEach
                val ux = dx / length
                val uy = dy / length
                val back = minOf(nodeWPx / 2f / maxOf(0.01f, kotlin.math.abs(ux)), nodeHPx / 2f / maxOf(0.01f, kotlin.math.abs(uy))) + 4f
                val start = Offset(from.x + ux * 20f, from.y + uy * 20f)
                val end = Offset(to.x - ux * back, to.y - uy * back)
                val tone = if (l.state == "accepted") linkColor else linkColor.copy(alpha = 0.45f)
                drawLine(tone, start, end, strokeWidth = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f)))
                val angle = atan2(uy, ux)
                val head = Path().apply {
                    moveTo(end.x, end.y)
                    lineTo((end.x - 22f * cos(angle - 0.45)).toFloat(), (end.y - 22f * sin(angle - 0.45)).toFloat())
                    lineTo((end.x - 22f * cos(angle + 0.45)).toFloat(), (end.y - 22f * sin(angle + 0.45)).toFloat())
                    close()
                }
                drawPath(head, tone)
            }
        }
        // the labels of the pairs, at the middle of their arrows
        pairs.forEach { p ->
            val from = position[p.builder] ?: return@forEach
            val to = position[p.critic] ?: return@forEach
            val mid = Offset((from.x + to.x) / 2f, (from.y + to.y) / 2f)
            Text(
                p.id + (if (p.state == "running" || p.rounds > 0) " ${p.rounds}/${p.limit}" else ""),
                color = colorOf(p), style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.offset { IntOffset((mid.x - 14f).roundToInt(), (mid.y - 22f).roundToInt()) },
            )
        }
        links.filter { it.state != "refused" }.forEach { l ->
            val from = position[l.sender] ?: return@forEach
            val to = position[l.receiver] ?: return@forEach
            val mid = Offset((from.x + to.x) / 2f, (from.y + to.y) / 2f)
            Text(
                l.id + (if (l.state == "accepted" || l.rounds > 0) " ${l.rounds}/${l.limit}" else " ?"),
                color = linkColor, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.offset { IntOffset((mid.x - 14f).roundToInt(), (mid.y + 4f).roundToInt()) },
            )
        }
        // the sessions
        nodes.forEach { (id, title) ->
            val at = position[id] ?: return@forEach
            Surface(
                shape = RoundedCornerShape(10.dp), tonalElevation = 4.dp,
                modifier = Modifier.width(nodeW).height(nodeH).offset { IntOffset((at.x - nodeWPx / 2f).roundToInt(), (at.y - nodeHPx / 2f).roundToInt()) },
            ) {
                Box(Modifier.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) { Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 2) }
            }
        }
    }
}
