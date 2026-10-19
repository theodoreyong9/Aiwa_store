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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The « + » of the widget: a document or a media (a file that goes with the next message), (writing to a contact is a connector now). */
class PlusMenuActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            // the files joined and not sent yet, one by one: each can be seen and taken back on its own
            val files = remember { androidx.compose.runtime.mutableStateListOf<Pair<String, Int>>() }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                val got = withContext(Dispatchers.IO) { try { LocalClaudeBridge().status().attachmentFiles } catch (e: Exception) { emptyList() } }
                files.clear(); files.addAll(got)
            }
            val scope = rememberCoroutineScope()
            val entries = buildList {
                files.forEachIndexed { i, f ->
                    val kb = (f.second + 1023) / 1024
                    add(PickerEntry("✕  ${f.first}\n$kb Ko, part avec le prochain message : toucher pour le retirer", true) {
                        val app = applicationContext
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { LocalClaudeBridge().attachRemove(i) }
                            if (ok) {
                                files.removeAt(i)
                                toastOnMain(app, "« ${f.first} » retiré")
                                try { AiwaWidget().updateAll(app) } catch (e: Exception) { }
                            } else toastOnMain(app, "Impossible de le retirer")
                        }
                    })
                }
                add(PickerEntry("Document\nun fichier joint au prochain message", false) { attach(arrayOf("*/*")) })
                add(PickerEntry("Média\nphoto, vidéo ou son, joint au prochain message", false) { attach(arrayOf("image/*", "video/*", "audio/*")) })
            }
            PickerSheet(entries) { finish() }
        }
    }

    private fun attach(types: Array<String>) {
        startActivity(Intent(this, AttachActivity::class.java).putExtra("types", types)); finish()
    }
}
