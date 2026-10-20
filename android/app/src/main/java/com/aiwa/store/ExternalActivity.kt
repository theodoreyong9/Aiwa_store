package com.aiwa.store

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
                if (st == null) { add(PickerEntry("Cerveau injoignable ou trop ancien (il faut la version 158).", false, lines = 2) { }); return@buildList }
                add(PickerEntry(if (st.open) "Porte ouverte : les agents externes peuvent te parler (toucher pour fermer)" else "Porte fermée (toucher pour ouvrir)", st.open, lines = 2) {
                    scope.launch { status = withContext(Dispatchers.IO) { LocalClaudeBridge().externalOpen(!st.open) } }
                })
                add(PickerEntry("Copier mon canal (à mettre dans le secret NTFY_TOPIC du Worker Cloudflare)", false, lines = 2, button = true) {
                    try { (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Aiwa", st.topic)) } catch (err: Exception) { }
                })
                add(PickerEntry("Mode d'emploi : déploie docs/mcp/worker.js sur Cloudflare (Workers, gratuit), ajoute les secrets NTFY_TOPIC (ce canal) et MCP_KEY (une clé longue de ton choix), puis donne à l'autre agent l'adresse https://ton-worker.workers.dev/<MCP_KEY>/mcp comme serveur MCP.", false, lines = 6) { })
                add(PickerEntry("Ce qui arrive est dit comme une information, jamais exécuté. Ton super agent doit être en marche pour le dire à voix haute.", false, lines = 3) { })
                if (st.inbox.isNotEmpty()) add(PickerEntry("Derniers messages", false, header = true) { })
                st.inbox.forEach { add(PickerEntry(it, false, lines = 3) { }) }
            }
            PickerSheet(entries) { finish() }
        }
    }
}
