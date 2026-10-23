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
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val bridge = remember { LocalClaudeBridge() }
    val scope = rememberCoroutineScope()
    val state by AiwaRepository.state.collectAsState()
    var info by remember { mutableStateOf<LoopsInfo?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            try { info = bridge.loops() } catch (err: Exception) { message = "Boucles indisponibles : ${err.message ?: "?"}" }
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
            AiwaWidget().updateAll(app)
        }
    }
    // The session in use is the PRODUCTION; its critic is a new session, made here, that loops with it
    fun createCritic() {
        val builder = state.cloudSessionId ?: return
        val title = state.session
        if (busy) return
        busy = true
        message = "Création du critique… (une minute)"
        scope.launch {
            try {
                switchCloud(app, bridge, "new")
                val sent = sendAndTrack(app, bridge, "[Aiwa] Tu seras le critique de la session « $title » : Aiwa te transmettra son travail, tu le relis et tu proposes des corrections. Pour l'instant, attends.", toastErrors = true)
                BackendSync.refresh(bridge)
                val critic = AiwaRepository.state.value.cloudSessionId
                if (!sent || critic == null || critic == builder) message = "Le critique n'a pas pu être créé."
                else {
                    switchCloud(app, bridge, builder)
                    message = bridge.loopsAction("create", mapOf("builder" to builder, "critic" to critic)) ?: "Critique créé."
                }
            } catch (err: Exception) { message = "Impossible : ${err.message ?: "?"}" }
            busy = false
            refresh()
            AiwaWidget().updateAll(app)
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
                shape = RoundedCornerShape(24.dp), color = Color.Transparent, border = androidx.compose.foundation.BorderStroke(1.dp, SheetLine),
                modifier = Modifier.padding(16.dp).fillMaxWidth().background(SheetBrush, RoundedCornerShape(24.dp)).background(GlassSheen, RoundedCornerShape(24.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            ) {
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val current = info
                    val builder = state.cloudSessionId
                    val pair = current?.pairs?.firstOrNull { it.builder == builder }
                    val link = pair?.let { p -> current.links.firstOrNull { it.sender == p.critic || it.receiver == p.critic } }
                    Text("Boucle · ${state.session}", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (builder == null) {
                        Text("Choisis d'abord une session.", style = MaterialTheme.typography.bodyMedium)
                    } else if (current == null) {
                        Text("Chargement…")
                    } else if (pair == null) {
                        // Number of round trips, then the critic
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Allers-retours : ${current.limit}", modifier = Modifier.weight(1f))
                            GlassOutlinedButton(onClick = { act("limit", mapOf("limit" to (maxOf(1, current.limit - 1)).toString())) }, enabled = !busy) { Text("−") }
                            GlassOutlinedButton(onClick = { act("limit", mapOf("limit" to (minOf(50, current.limit + 1)).toString())) }, enabled = !busy) { Text("+") }
                        }
                        GlassButton(onClick = { createCritic() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Créer le critique") }
                    } else {
                        Text("Critique : ${pair.criticTitle}", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(stateLabel(pair), style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (pair.state == "running") {
                                GlassButton(onClick = { act("stop", mapOf("id" to pair.id), "Boucle arrêtée.") }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Arrêter") }
                            } else if (pair.state == "idle") {
                                GlassButton(onClick = { act("start", mapOf("id" to pair.id), "Boucle démarrée : parle maintenant à la production.") }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Démarrer") }
                            } else {
                                GlassButton(onClick = { act("relaunch", mapOf("id" to pair.id), "Boucle relancée.") }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Relancer") }
                            }
                            if (pair.state != "running") GlassOutlinedButton(onClick = { act("delete", mapOf("id" to pair.id)) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Supprimer") }
                        }
                        // ONE companion for the critic, among the sessions that are critics too
                        if (link != null) {
                            Text("Compagnon : " + (if (link.sender == pair.critic) link.receiverTitle else link.senderTitle) + " · " + linkLabel(link), style = MaterialTheme.typography.bodySmall)
                            GlassOutlinedButton(onClick = { act(if (link.state == "accepted" || link.state == "proposed") "link_stop" else "link_delete", mapOf("id" to link.id)) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                Text(if (link.state == "accepted" || link.state == "proposed") "Retirer le compagnon" else "Supprimer")
                            }
                        } else if (!picking) {
                            GlassOutlinedButton(onClick = { picking = true }, enabled = !busy && current.critics.any { it.id != pair.critic }, modifier = Modifier.fillMaxWidth()) { Text("Compagnon du critique") }
                            if (current.critics.none { it.id != pair.critic }) Text("Aucune autre session n'est critique : il n'y a personne à choisir.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            current.critics.filter { it.id != pair.critic }.forEach { c ->
                                GlassOutlinedButton(onClick = { picking = false; act("link_create", mapOf("sender" to pair.critic, "receiver" to c.id, "opening" to "Salut, qu'est-ce que tu fais ? J'ai envie de t'aider."), "Invitation envoyée.") }, modifier = Modifier.fillMaxWidth()) { Text(c.title, maxLines = 1) }
                            }
                            GlassOutlinedButton(onClick = { picking = false }, modifier = Modifier.fillMaxWidth()) { Text("Annuler") }
                        }
                    }
                    GlassOutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
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
                shape = RoundedCornerShape(12.dp), color = Color.Transparent, border = androidx.compose.foundation.BorderStroke(1.dp, GlassEdge),
                modifier = Modifier.width(nodeW).height(nodeH).background(GlassBody, RoundedCornerShape(12.dp)).offset { IntOffset((at.x - nodeWPx / 2f).roundToInt(), (at.y - nodeHPx / 2f).roundToInt()) },
            ) {
                Box(Modifier.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) { Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 2) }
            }
        }
    }
}
