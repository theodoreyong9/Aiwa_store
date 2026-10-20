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
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.aiwa.bridge.ExternalStatus
import com.aiwa.bridge.LocalClaudeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings → « Agents externes »: the door for other agents (docs/mcp/worker.js). Closed by default; what comes in is said as information, never executed. */
class ExternalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            val scope = rememberCoroutineScope()
            var status by remember { mutableStateOf<ExternalStatus?>(null) }
            LaunchedEffect(Unit) { status = withContext(Dispatchers.IO) { LocalClaudeBridge().externalStatus() } }
            val st = status
            val entries = buildList {
                if (st == null) { add(PickerEntry("Cerveau injoignable ou trop ancien (il faut la version 161).", false, lines = 2) { }); return@buildList }
                add(PickerEntry(if (st.open) "Porte ouverte : les agents externes peuvent te parler (toucher pour fermer)" else "Porte fermée (toucher pour ouvrir)", st.open, lines = 2) {
                    scope.launch { status = withContext(Dispatchers.IO) { LocalClaudeBridge().externalOpen(!st.open) } }
                })
                add(PickerEntry(if (st.configured) "Worker : ${st.url} (toucher pour changer l'adresse ou la clé)" else "Indiquer l'adresse du Worker et ma clé", false, lines = 3, button = true) {
                    startActivity(Intent(this@ExternalActivity, ExternalConfigActivity::class.java)); finish()
                })
                add(PickerEntry("Mode d'emploi : sur Cloudflare, crée un Worker avec le code de docs/mcp/worker.js, associe-lui un espace KV nommé INBOX (Settings → Bindings) et un secret MCP_KEY (une clé longue de ton choix). Donne à l'autre agent l'adresse https://ton-worker.workers.dev/<MCP_KEY>/mcp, et indique ici l'adresse du Worker et la même clé : le téléphone vient chercher les messages toutes les 30 secondes.", false, lines = 8) { })
                add(PickerEntry("Ce qui arrive est écrit dans la session de ton super agent comme une information, jamais exécuté. Pour répondre, dis-lui « réponds à ChatGPT : … » : l'autre agent lira ta réponse au prochain passage.", false, lines = 3) { })
                if (st.inbox.isNotEmpty()) add(PickerEntry("Derniers messages (leur texte est dans la session de l'agent)", false, header = true) { })
                st.inbox.forEach { add(PickerEntry(it, false, lines = 3) { }) }
            }
            PickerSheet(entries) { finish() }
        }
    }
}

/** The Worker's address and the person's key: kept in the brain's Linux only (owner-only file), used to fetch the messages from that Worker only. */
class ExternalConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            val scope = rememberCoroutineScope()
            var url by remember { mutableStateOf("") }
            var key by remember { mutableStateOf("") }
            var note by remember { mutableStateOf<String?>(null) }
            var kept by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { withContext(Dispatchers.IO) { LocalClaudeBridge().externalStatus() }?.let { if (it.configured) { url = it.url; kept = true } } }
            StockSheet({ finish() }) {
                Text("Mon Worker", style = MaterialTheme.typography.titleMedium)
                Text("L'adresse de ton Worker Cloudflare (finit par .workers.dev, tu peux coller l'adresse complète du connecteur) et la clé MCP_KEY que tu as choisie. Elles restent dans le cerveau Termux (fichier lisible par toi seul).", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Adresse du Worker") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text(if (kept) "Clé MCP_KEY (laisse vide pour garder celle enregistrée)" else "Clé MCP_KEY") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                Button(onClick = {
                    scope.launch {
                        val st = withContext(Dispatchers.IO) { LocalClaudeBridge().externalConfig(url.trim(), key.trim()) }
                        if (st == null) note = "Cerveau injoignable ou trop ancien (version 161)." else if (st.error != null) note = st.error else { toastOnMain(applicationContext, "Enregistré ✓"); startActivity(Intent(this@ExternalConfigActivity, ExternalActivity::class.java)); finish() }
                    }
                }, enabled = url.isNotBlank() && (key.isNotBlank() || kept), modifier = Modifier.fillMaxWidth()) { Text("Enregistrer") }
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                OutlinedButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
            }
        }
    }
}
