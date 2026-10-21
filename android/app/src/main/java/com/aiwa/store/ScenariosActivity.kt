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
                if (l == null) { add(PickerEntry("Cerveau injoignable ou trop ancien (il faut la version 166).", false, lines = 2) { }); return@buildList }
                add(PickerEntry("Dis le mot de lancement au super agent : la première fois, un double de toi est créé, tu lui parles en parallèle.", false, lines = 3) { })
                l.scenarios.forEach { sc ->
                    add(PickerEntry((if (sc.id in l.running) "● " else "") + sc.name + "  ·  « " + sc.word + " »" + (if (sc.kind == "report") "  (rapport du super agent)" else ""), false, lines = 2) {
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
            LaunchedEffect(Unit) {
                if (id.isNotEmpty()) withContext(Dispatchers.IO) { LocalClaudeBridge().scenarios() }?.scenarios?.firstOrNull { it.id == id }?.let {
                    kind = it.kind; name = it.name; word = it.word; prompt = it.prompt; speak = it.speak; words = it.words.joinToString("\n") { w -> w.word + " = " + w.does }
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
                        if (saved == null) note = "Cerveau injoignable ou trop ancien (version 166)." else if (saved.error != null) note = saved.error else { toastOnMain(applicationContext, "Enregistré ✓"); back() }
                    }
                }, enabled = name.isNotBlank() && word.isNotBlank() && (kind == "report" || prompt.isNotBlank()), modifier = Modifier.fillMaxWidth()) { Text("Enregistrer") }
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                if (id.isNotEmpty()) OutlinedButton(onClick = { scope.launch { withContext(Dispatchers.IO) { LocalClaudeBridge().scenarioDelete(id) }; back() } }, modifier = Modifier.fillMaxWidth()) { Text("Supprimer ce scénario") }
                OutlinedButton(onClick = { back() }, modifier = Modifier.fillMaxWidth()) { Text("Retour") }
            }
        }
    }
}
