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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import com.aiwa.bridge.LocalClaudeBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The new name of a session: kept by the brain (it is the name the widget and the lists show) and told to the session itself (/rename), so that both say the same. */
class SessionRenameActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        val id = intent.getStringExtra("id")
        if (id == null) { finish(); return }
        setContent {
            var text by remember { mutableStateOf(intent.getStringExtra("title").orEmpty().take(60)) }
            DarkSheetTheme {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(22.dp), color = SheetBg, border = androidx.compose.foundation.BorderStroke(1.dp, SheetLine),
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text("Renommer la session", style = MaterialTheme.typography.titleMedium)
                            androidx.compose.material3.OutlinedTextField(value = text, onValueChange = { text = it.take(60) }, singleLine = true, label = { Text("Nom") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                                androidx.compose.material3.TextButton(onClick = { finish() }) { Text("Annuler") }
                                androidx.compose.material3.Button(enabled = text.isNotBlank(), onClick = {
                                    val app = applicationContext
                                    val name = text.trim()
                                    CoroutineScope(Dispatchers.Default).launch {
                                        val bridge = LocalClaudeBridge()
                                        val error = try { bridge.renameSession(id, name) } catch (err: Exception) { err.message ?: "?" }
                                        if (error != null) toastOnMain(app, "Pas renommée : $error")
                                        try { BackendSync.refresh(bridge) } catch (err: Exception) { }
                                        AiwaWidget().updateAll(app)
                                    }
                                    finish()
                                }) { Text("Renommer") }
                            }
                        }
                    }
                }
            }
        }
    }
}
