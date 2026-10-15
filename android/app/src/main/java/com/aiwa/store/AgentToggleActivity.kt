package com.aiwa.store
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.aiwa.bridge.LocalClaudeBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The 🤖 button of the widget: the switch to the second listening (the liaison agent) and back. A no-UI trampoline, because starting a microphone service needs a
 * visible tap. Open: closes it (the first listening takes over again). Closed: opens it, and the first listening waits. Without an agent yet, it opens the window that makes one.
 */
class AgentToggleActivity : ComponentActivity() {
    companion object { const val EXTRA_REPORT = "report" }
    private fun reportAfterOpening() {
        // opened by « Oui » (the notification): once the agent listens, it is asked for the report of the whole activity and an idea
        if (!intent.getBooleanExtra(EXTRA_REPORT, false)) return
        CoroutineScope(Dispatchers.IO).launch {
            delay(2500)
            try { LocalClaudeBridge().agent("report", emptyMap()) } catch (err: Exception) { /* no agent */ }
        }
    }

    // The button is the trigger of the whole listening: the first tap asks for the microphone if needed, turns the wake listening on and opens the agent; after that everything goes by voice.
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { WakeWordService.openAgentMode(applicationContext); reportAfterOpening() } else toastOnMain(applicationContext, "Sans la permission du micro, l'agent ne peut pas écouter.")
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        val agent = AiwaRepository.state.value.agent
        when {
            agent == null || (agent.session == null && agent.llm != "ready") -> startActivity(Intent(this, AgentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            agent.enabled -> WakeWordService.closeAgentMode(applicationContext)
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED -> { micPermission.launch(Manifest.permission.RECORD_AUDIO); return }
            else -> { WakeWordService.openAgentMode(applicationContext); reportAfterOpening() }
        }
        finish()
    }
}
