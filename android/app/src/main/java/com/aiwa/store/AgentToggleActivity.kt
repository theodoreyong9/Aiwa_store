package com.aiwa.store
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * The 🤖 button of the widget: the switch to the second listening (the liaison agent) and back. A no-UI trampoline, because starting a microphone service needs a
 * visible tap. Open: closes it (the first listening takes over again). Closed: opens it, and the first listening waits. Without an agent yet, it opens the window that makes one.
 */
class AgentToggleActivity : ComponentActivity() {
    // The button is the trigger of the whole listening: the first tap asks for the microphone if needed, turns the wake listening on and opens the agent; after that everything goes by voice.
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) WakeWordService.openAgentMode(applicationContext) else toastOnMain(applicationContext, "Sans la permission du micro, l'agent ne peut pas écouter.")
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
            else -> WakeWordService.openAgentMode(applicationContext)
        }
        finish()
    }
}
