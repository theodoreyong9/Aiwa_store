package com.aiwa.store
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.aiwa.bridge.DocItem
import com.aiwa.bridge.LocalClaudeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The widget's « Docs »: the documents of the repository in use (the files of its folder `docs`), in one scrolling list, the most recently changed first, with date and time. */
class DocumentsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        if (AiwaRepository.state.value.repo == null) { toastOnMain(this, "Choisis d'abord un dépôt."); finish(); return }
        setContent {
            var docs by remember { mutableStateOf<List<DocItem>?>(null) }
            LaunchedEffect(Unit) { docs = withContext(Dispatchers.IO) { LocalClaudeBridge().documents() } }
            val list = docs
            val entries = buildList {
                if (list == null) add(PickerEntry("Chargement…", false, lines = 1) { })
                else if (list.isEmpty()) add(PickerEntry("Aucun document dans ${AiwaRepository.state.value.repo} (ou dépôt privé).", false, lines = 2) { })
                else list.forEach { d ->
                    val when_ = dateTimeLabel(d.ts)
                    add(PickerEntry(d.name + if (when_.isNotEmpty()) "\n$when_" else "", false, lines = 2) {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(d.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); finish()
                    })
                }
            }
            PickerSheet(entries) { finish() }
        }
    }
}
