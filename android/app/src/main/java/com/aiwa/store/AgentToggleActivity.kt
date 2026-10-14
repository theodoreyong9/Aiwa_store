package com.aiwa.store
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * The 🤖 button of the widget: the switch to the second listening (the liaison agent) and back. A no-UI trampoline, because starting a microphone service needs a
 * visible tap. Open: closes it (the first listening takes over again). Closed: opens it, and the first listening waits. Without an agent yet, it opens the window that makes one.
 */
class AgentToggleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        val agent = AiwaRepository.state.value.agent
        when {
            agent?.session == null -> startActivity(Intent(this, AgentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            agent.enabled -> WakeWordService.closeAgentMode(applicationContext)
            else -> WakeWordService.openAgentMode(applicationContext)
        }
        finish()
    }
}
