package com.aiwa.store
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aiwa.bridge.CritiqueList
import com.aiwa.bridge.LocalClaudeBridge
import kotlinx.coroutines.launch

/**
 * The history of what the critic of a pair wrote on the corpus: its critiques (`aiwa-loop/<pair>/critique-<turn>.md` on its branch), the last turn first, and
 * the summary of facts it keeps (`aiwa-loop/resume.md`). Read from GitHub through the backend; a tap opens one.
 */
class CritiquesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        val pair = intent.getStringExtra("pair").orEmpty()
        setContent { CritiquesWindow(pair, onClose = { finish() }) }
    }
}

@Composable
private fun CritiquesWindow(pair: String, onClose: () -> Unit) {
    val bridge = remember { LocalClaudeBridge() }
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<CritiqueList?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var opened by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pair) {
        try { list = bridge.critiques(pair) } catch (err: Exception) { error = err.message ?: "Critiques illisibles." }
    }
    fun open(name: String) {
        opened = name; text = null
        scope.launch { try { text = bridge.critiqueText(pair, name) } catch (err: Exception) { error = err.message ?: "Lecture impossible." } }
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Critiques du corpus · boucle $pair", style = MaterialTheme.typography.titleMedium)
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                val current = list
                if (opened != null) {
                    Text(opened!!, style = MaterialTheme.typography.titleSmall)
                    Text(text ?: "Lecture…", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { opened = null; text = null }, modifier = Modifier.fillMaxWidth()) { Text("Retour à la liste") }
                } else if (current == null) {
                    if (error == null) Text("Chargement…")
                } else {
                    Text("Branche du critique : ${current.branch}", style = MaterialTheme.typography.bodySmall)
                    if (current.resume) OutlinedButton(onClick = { open("resume.md") }, modifier = Modifier.fillMaxWidth()) { Text("Résumé des faits (resume.md)") }
                    if (current.items.isEmpty()) Text("Aucune critique écrite pour l'instant : le critique en écrit une à chacun de ses tours.")
                    current.items.forEach { item ->
                        OutlinedButton(onClick = { open(item.name) }, modifier = Modifier.fillMaxWidth()) { Text("Tour ${item.n} · ${item.size / 1024 + 1} Ko") }
                    }
                }
                OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
            }
        }
    }
}
