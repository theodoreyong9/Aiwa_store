package com.aiwa.store

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.aiwa.bridge.LocalClaudeBridge
import com.aiwa.bridge.Scenario
import com.aiwa.bridge.ScenarioList
import com.aiwa.bridge.ScenarioWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings → « Scénarios »: a prompt kept under a launch word of the person's own. Said to the super agent, the word creates a temporary double (the first time) that she talks to in parallel. */
class ScenariosActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            var list by remember { mutableStateOf<ScenarioList?>(null) }
            LaunchedEffect(Unit) { list = withContext(Dispatchers.IO) { LocalClaudeBridge().scenarios() } }
            val l = list
            val entries = buildList {
                if (l == null) { add(PickerEntry("Cerveau injoignable ou trop ancien (il faut la version 168).", false, lines = 2) { }); return@buildList }
                add(PickerEntry("Dis le mot de lancement au super agent : la première fois, un double de toi est créé, tu lui parles en parallèle.", false, lines = 3) { })
                l.scenarios.forEach { sc ->
                    add(PickerEntry((if (sc.session != null) "● " else "") + sc.name + "  ·  « " + sc.word + " »" + (if (sc.kind == "report") "  (rapport du super agent)" else ""), false, lines = 2) {
                        startActivity(Intent(this@ScenariosActivity, ScenarioEditActivity::class.java).putExtra("id", sc.id)); finish()
                    })
                }
                add(PickerEntry("Nouveau scénario", false, button = true) { startActivity(Intent(this@ScenariosActivity, ScenarioEditActivity::class.java)); finish() })
            }
            PickerSheet(entries) { finish() }
        }
    }
}

/** One scenario: name, launch word, the prompt, when the double speaks, and its special words (one per line: « mot = ce que ça fait »). */
class ScenarioEditActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        val id = intent.getStringExtra("id") ?: ""
        setContent {
            val scope = rememberCoroutineScope()
            var kind by remember { mutableStateOf("double") }
            var name by remember { mutableStateOf("") }
            var word by remember { mutableStateOf("") }
            var prompt by remember { mutableStateOf("") }
            var speak by remember { mutableStateOf("") }
            var words by remember { mutableStateOf("") }
            var note by remember { mutableStateOf<String?>(null) }
            var current by remember { mutableStateOf<Scenario?>(null) }
            LaunchedEffect(Unit) {
                if (id.isNotEmpty()) withContext(Dispatchers.IO) { LocalClaudeBridge().scenarios() }?.scenarios?.firstOrNull { it.id == id }?.let {
                    current = it
                    kind = it.kind; name = it.name; word = it.word; prompt = it.prompt; speak = it.speak; words = it.words.joinToString("\n") { w -> w.word + " = " + w.does }
                }
            }
            // while the session is being made, look at it every two seconds
            LaunchedEffect(current?.creating) {
                while (current?.creating == true) {
                    kotlinx.coroutines.delay(2_000)
                    current = withContext(Dispatchers.IO) { LocalClaudeBridge().scenarios() }?.scenarios?.firstOrNull { it.id == id } ?: current
                }
            }
            fun back() { startActivity(Intent(this@ScenarioEditActivity, ScenariosActivity::class.java)); finish() }
            StockSheet({ finish() }) {
                Text(if (id.isEmpty()) "Nouveau scénario" else name, style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = word, onValueChange = { word = it }, label = { Text("Mot de lancement (à dire au super agent)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (kind == "double") {
                    OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text("Consigne : ce que le double fait") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = speak, onValueChange = { speak = it }, label = { Text("Quand il parle à voix haute (sinon il note en silence)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = words, onValueChange = { words = it }, label = { Text("Mots spéciaux, un par ligne : mot = ce que ça fait") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                } else Text("Ce scénario demande au super agent le rapport de tout ce qui s'est passé depuis la dernière conversation.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = {
                    val parsed = words.lines().mapNotNull { line -> line.split("=", limit = 2).takeIf { it.size == 2 && it[0].isNotBlank() && it[1].isNotBlank() }?.let { ScenarioWord(it[0].trim(), it[1].trim()) } }
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) { LocalClaudeBridge().scenarioSave(Scenario(id, name.trim(), word.trim(), kind, prompt.trim(), speak.trim(), parsed)) }
                        if (saved == null) note = "Cerveau injoignable ou trop ancien (version 168)." else if (saved.error != null) note = saved.error else { toastOnMain(applicationContext, "Enregistré ✓"); back() }
                    }
                }, enabled = name.isNotBlank() && word.isNotBlank() && (kind == "report" || prompt.isNotBlank()), modifier = Modifier.fillMaxWidth()) { Text("Enregistrer") }
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                if (id.isNotEmpty() && kind == "double") {
                    val cur = current
                    Text(when {
                        cur == null -> ""
                        cur.creating -> "Session en cours de création…"
                        cur.error != null -> "Création impossible : " + cur.error
                        cur.session != null -> "Session : " + (cur.sessions.firstOrNull { it.current }?.title ?: cur.session)
                        else -> "Pas encore de session : elle se crée au premier lancement (ou avec le bouton)."
                    }, style = MaterialTheme.typography.bodySmall)
                    if (cur?.session == null) OutlinedButton(onClick = { scope.launch { current = withContext(Dispatchers.IO) { LocalClaudeBridge().scenarioCreate(id) }?.scenarios?.firstOrNull { it.id == id } ?: current } },
                        enabled = cur?.creating != true, modifier = Modifier.fillMaxWidth()) { Text("Créer la session") }
                    OutlinedButton(onClick = { startActivity(Intent(this@ScenarioEditActivity, ScenarioSessionsActivity::class.java).putExtra("id", id)); finish() }, modifier = Modifier.fillMaxWidth()) { Text(if (cur?.session == null) "Choisir une session" else "Changer de session") }
                    Button(onClick = {
                        scope.launch {
                            val launched = withContext(Dispatchers.IO) { LocalClaudeBridge().scenarioLaunch(id = id) }
                            if (launched == null) note = "Lancement impossible : le cerveau ne répond pas, ou il est trop ancien (version 168)." else { WakeWordService.openAgentMode(applicationContext, launched.first); finish() }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Lancer le scénario") }
                } else if (id.isEmpty()) Text("Enregistre d'abord : la session et le lancement viennent ensuite.", style = MaterialTheme.typography.bodySmall)
                if (id.isNotEmpty()) OutlinedButton(onClick = { scope.launch { withContext(Dispatchers.IO) { LocalClaudeBridge().scenarioDelete(id) }; back() } }, modifier = Modifier.fillMaxWidth()) { Text("Supprimer ce scénario") }
                OutlinedButton(onClick = { back() }, modifier = Modifier.fillMaxWidth()) { Text("Retour") }
            }
        }
    }
}

/** The sessions of one scenario: the one in use is ticked; « Nouvelle session » makes another one (the others stay, and can be taken back). */
class ScenarioSessionsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        val id = intent.getStringExtra("id") ?: ""
        setContent {
            val scope = rememberCoroutineScope()
            var sc by remember { mutableStateOf<Scenario?>(null) }
            LaunchedEffect(Unit) { sc = withContext(Dispatchers.IO) { LocalClaudeBridge().scenarios() }?.scenarios?.firstOrNull { it.id == id } }
            fun back() { startActivity(Intent(this@ScenarioSessionsActivity, ScenarioEditActivity::class.java).putExtra("id", id)); finish() }
            val s = sc
            val entries = buildList {
                if (s == null) { add(PickerEntry("Cerveau injoignable ou trop ancien.", false, lines = 2) { }); return@buildList }
                add(PickerEntry("Nouvelle session", false, button = true) { scope.launch { withContext(Dispatchers.IO) { LocalClaudeBridge().scenarioCreate(id, fresh = true) }; back() } })
                s.sessions.forEach { x ->
                    add(PickerEntry(x.title, x.current, lines = 2) { scope.launch { withContext(Dispatchers.IO) { LocalClaudeBridge().scenarioUse(id, x.id) }; back() } })
                }
                // her other sessions: choosing one sends it the scenario (its prompt and rules), and it becomes the scenario's session
                val mine = s.sessions.map { it.id }.toSet()
                val others = AiwaRepository.state.value.cloudSessions.filter { it.id !in mine }
                if (others.isNotEmpty()) add(PickerEntry("Envoyer le scénario à une session connue", false, header = true) { })
                others.forEach { c ->
                    add(PickerEntry(c.title.take(60), false, lines = 2) { scope.launch { withContext(Dispatchers.IO) { LocalClaudeBridge().scenarioUse(id, c.id) }; back() } })
                }
            }
            PickerSheet(entries) { back() }
        }
    }
}
