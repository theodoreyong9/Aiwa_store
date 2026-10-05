package com.aiwa.store
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.aiwa.bridge.LocalClaudeBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The widget's "Claude ↗" button: a no-UI trampoline (same translucent,
 * own-task setup as the pickers) that opens the current cloud session in
 * the Claude app, where its conversation lives. An Activity because a
 * widget can only fire an intent, and the session to open is only known
 * from Aiwa's state at tap time.
 */
class OpenClaudeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        val known = AiwaRepository.state.value.let { it.cloudSessionId != null || it.lastSessionId != null }
        if (known && openClaudeApp(this)) {
            finish()
            return
        }
        // No session known: the process may have restarted since the widget last drew, so ask the backend which one is
        // current before opening (a session if there is one, else Claude's Code tab).
        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.IO) { BackendSync.refresh(LocalClaudeBridge()) }
            if (!openClaudeApp(this@OpenClaudeActivity)) toastOnMain(this@OpenClaudeActivity, "Impossible d'ouvrir l'appli Claude.")
            finish()
        }
    }
}
