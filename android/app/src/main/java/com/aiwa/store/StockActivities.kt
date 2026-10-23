package com.aiwa.store

import android.content.Intent
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
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aiwa.bridge.LocalClaudeBridge
import com.aiwa.bridge.StockClip
import com.aiwa.bridge.StockStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Free stock videos (Pexels, Pixabay) with the person's OWN keys (brain: aiwa_stock.py): the keys, a search, the clips kept on the phone and put in the release `stock` for the sessions that make a film.

private fun sourceLabel(source: String) = if (source == "pixabay") "Pixabay" else "Pexels"

@androidx.compose.runtime.Composable
internal fun StockSheet(onClose: () -> Unit, content: @androidx.compose.runtime.Composable () -> Unit) {
    DarkSheetTheme {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp), color = SheetBg, border = androidx.compose.foundation.BorderStroke(1.dp, SheetLine),
                modifier = Modifier.padding(16.dp).fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            ) {
                Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
            }
        }
    }
}

/** Settings → « Banques de vidéos libres »: the two keys and the search. */
class StockSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            var status by remember { mutableStateOf<StockStatus?>(null) }
            LaunchedEffect(Unit) { status = withContext(Dispatchers.IO) { LocalClaudeBridge().stockStatus() } }
            val st = status
            val entries = buildList {
                if (st == null) { add(PickerEntry("Cerveau injoignable ou trop ancien (il faut la version 156).", false, lines = 2) { }); return@buildList }
                add(PickerEntry("Chercher un clip", false, lines = 1, button = true) { startActivity(Intent(this@StockSettingsActivity, StockSearchActivity::class.java)); finish() })
                add(PickerEntry("Clé Pexels : ${if (st.pexels) "en place" else "absente"}", false, lines = 1, button = true) { startActivity(Intent(this@StockSettingsActivity, StockKeyActivity::class.java).putExtra("source", "pexels")); finish() })
                add(PickerEntry("Clé Pixabay : ${if (st.pixabay) "en place" else "absente"}", false, lines = 1, button = true) { startActivity(Intent(this@StockSettingsActivity, StockKeyActivity::class.java).putExtra("source", "pixabay")); finish() })
                add(PickerEntry("Les clés sont gratuites (compte sur pexels.com/api ou pixabay.com/api/docs). Elles restent dans Termux, jamais dans un dépôt.", false, lines = 3) { })
            }
            PickerSheet(entries, title = "Banques de vidéos") { finish() }
        }
    }
}

/** One bank's key: kept in the brain's Linux only (owner-only file), sent to that bank only. */
class StockKeyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        val source = intent.getStringExtra("source") ?: "pexels"
        setContent {
            val scope = rememberCoroutineScope()
            var key by remember { mutableStateOf("") }
            var has by remember { mutableStateOf<Boolean?>(null) }
            var note by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) { has = withContext(Dispatchers.IO) { LocalClaudeBridge().stockStatus()?.let { if (source == "pixabay") it.pixabay else it.pexels } } }
            StockSheet({ finish() }) {
                Text("Clé ${sourceLabel(source)}", style = MaterialTheme.typography.titleMedium)
                Text("Pour chercher et télécharger des vidéos libres. Tu crées gratuitement un compte sur ${sourceLabel(source)}, tu demandes une clé d'API, et tu la colles ici. Elle reste dans le cerveau Termux (fichier lisible par toi seul) et ne va qu'à ${sourceLabel(source)}.", style = MaterialTheme.typography.bodySmall)
                Text(if (has == true) "En place." else if (has == false) "Absente." else "…", style = MaterialTheme.typography.titleSmall)
                if (has != true) {
                    OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("Ta clé ${sourceLabel(source)}") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                    Button(onClick = {
                        scope.launch {
                            val error = withContext(Dispatchers.IO) { LocalClaudeBridge().stockKey(source, key.trim()) }
                            if (error == null) { key = ""; has = true; note = null } else note = error
                        }
                    }, enabled = key.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Enregistrer la clé") }
                } else {
                    OutlinedButton(onClick = { scope.launch { withContext(Dispatchers.IO) { LocalClaudeBridge().stockKey(source, null) }; has = false } }, modifier = Modifier.fillMaxWidth()) { Text("Retirer la clé") }
                }
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                OutlinedButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
            }
        }
    }
}

/** Search a clip, keep it on the phone, put it in the release `stock` of the repository in use (with its author and licence) for the sessions. */
class StockSearchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            val scope = rememberCoroutineScope()
            var query by remember { mutableStateOf(intent.getStringExtra("query").orEmpty()) }
            var source by remember { mutableStateOf("pexels") }
            var portrait by remember { mutableStateOf(true) }
            var results by remember { mutableStateOf<List<StockClip>>(emptyList()) }
            var status by remember { mutableStateOf<StockStatus?>(null) }
            var busy by remember { mutableStateOf("") }
            var note by remember { mutableStateOf<String?>(null) }
            suspend fun refresh() { status = withContext(Dispatchers.IO) { LocalClaudeBridge().stockStatus() } }
            LaunchedEffect(Unit) { refresh() }
            // while a clip is being saved in GitHub (a session may take minutes), the status is read again
            val pending = status?.clips?.any { it.saved == "pending" } == true
            LaunchedEffect(pending) { if (pending) { while (true) { delay(5_000); refresh(); if (status?.clips?.none { it.saved == "pending" } == true) break } } }
            StockSheet({ if (busy.isEmpty()) finish() }) {
                Text("Clips libres", style = MaterialTheme.typography.titleMedium)
                val st = status
                if (st != null && !st.pexels && !st.pixabay) {
                    Text("Il faut d'abord une clé gratuite Pexels ou Pixabay.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { startActivity(Intent(this@StockSearchActivity, StockSettingsActivity::class.java)); finish() }, modifier = Modifier.fillMaxWidth()) { Text("Mettre une clé") }
                } else {
                    OutlinedTextField(value = query, onValueChange = { query = it.take(120) }, label = { Text("Ce que je cherche (ex : homme sur un canapé)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { source = if (source == "pexels") "pixabay" else "pexels" }, modifier = Modifier.weight(1f)) { Text(sourceLabel(source)) }
                        OutlinedButton(onClick = { portrait = !portrait }, modifier = Modifier.weight(1f)) { Text(if (portrait) "Vertical" else "Tous formats") }
                    }
                    Button(onClick = {
                        busy = "Je cherche…"; note = null
                        scope.launch {
                            val (found, error) = withContext(Dispatchers.IO) { LocalClaudeBridge().stockSearch(query.trim(), source, portrait) }
                            results = found; note = error ?: if (found.isEmpty()) "Rien trouvé : essaie d'autres mots (en anglais, souvent plus de résultats)." else null
                            busy = ""
                        }
                    }, enabled = query.isNotBlank() && busy.isEmpty(), modifier = Modifier.fillMaxWidth()) { Text(if (busy.isNotEmpty()) busy else "Chercher") }
                    note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    results.forEach { c ->
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(c.title, style = MaterialTheme.typography.bodyMedium)
                            Text("${c.duration} s · ${c.author} · ${c.licence}", style = MaterialTheme.typography.bodySmall, color = SheetDim)
                            OutlinedButton(onClick = {
                                busy = "Je télécharge…"; note = null
                                scope.launch {
                                    val error = withContext(Dispatchers.IO) { LocalClaudeBridge().stockDownload(c) }
                                    if (error != null) note = error else { toastOnMain(applicationContext, "Clip téléchargé sur ce téléphone."); refresh() }
                                    busy = ""
                                }
                            }, enabled = busy.isEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Télécharger") }
                        }
                    }
                    st?.clips?.takeIf { it.isNotEmpty() }?.let { clips ->
                        Text("Mes clips", style = MaterialTheme.typography.titleSmall)
                        clips.forEach { c ->
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(c.title + " · " + c.author, style = MaterialTheme.typography.bodyMedium)
                                when (c.saved) {
                                    "saved" -> Text("Sauvegardé dans GitHub (release stock) ✓", style = MaterialTheme.typography.bodySmall)
                                    "pending" -> Text("Sauvegarde dans GitHub en cours…", style = MaterialTheme.typography.bodySmall)
                                    else -> {
                                        if (c.saved == "failed") Text(c.savedError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                        else Text("Seulement sur ce téléphone.", style = MaterialTheme.typography.bodySmall, color = SheetDim)
                                        OutlinedButton(onClick = {
                                            scope.launch {
                                                val result = withContext(Dispatchers.IO) { LocalClaudeBridge().stockPublish(c.id) }
                                                if (!result.first) toastOnMain(applicationContext, result.second ?: "Sauvegarde impossible.")
                                                refresh()
                                            }
                                        }, modifier = Modifier.fillMaxWidth()) { Text("Sauvegarder dans GitHub (pour les sessions)") }
                                    }
                                }
                            }
                        }
                    }
                }
                OutlinedButton(onClick = { finish() }, enabled = busy.isEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
            }
        }
    }
}
