package com.aiwa.store

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.aiwa.bridge.LocalClaudeBridge
import com.aiwa.bridge.StyleStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings → « Ma façon de parler »: the agent learns how the person speaks (counts of words, never her sentences) and talks to her in a way that looks like hers. Can be switched off or emptied. */
class StyleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            val scope = rememberCoroutineScope()
            var status by remember { mutableStateOf<StyleStatus?>(null) }
            LaunchedEffect(Unit) { status = withContext(Dispatchers.IO) { LocalClaudeBridge().styleStatus() } }
            val st = status
            val entries = buildList {
                if (st == null) { add(PickerEntry("Cerveau injoignable ou trop ancien (il faut la version 169).", false, lines = 2) { }); return@buildList }
                add(PickerEntry(if (st.enabled) "✓  L'agent apprend ma façon de parler (toucher pour couper)" else "L'agent n'apprend pas ma façon de parler (toucher pour activer)", st.enabled, lines = 2) {
                    scope.launch { status = withContext(Dispatchers.IO) { LocalClaudeBridge().styleSet(!st.enabled) } }
                })
                add(PickerEntry("Seuls des comptes sont gardés (longueur des phrases, tu ou vous, quelques petits mots), dans le cerveau, jamais tes phrases. Un court résumé est ajouté à ce qu'on dit à l'agent, comme une consigne.", false, lines = 5) { })
                add(PickerEntry(if (st.ready) st.note else "Pas encore assez : ${st.count} phrases comptées sur 8.", false, lines = 8) { })
                add(PickerEntry("Tout effacer", false, button = true) { scope.launch { status = withContext(Dispatchers.IO) { LocalClaudeBridge().styleClear() } } })
            }
            PickerSheet(entries, title = "Ma façon de parler") { finish() }
        }
    }
}
