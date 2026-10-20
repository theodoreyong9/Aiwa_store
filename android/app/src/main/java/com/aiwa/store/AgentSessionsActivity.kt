package com.aiwa.store

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.appwidget.updateAll
import com.aiwa.bridge.LocalClaudeBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Settings → « Super agent »: the session of the agent. « Nouvelle session » puts the one in place aside (it joins the list of the old ones) and goes to the choice of the agent's repository to
 * make a new one; each old one can be taken back, and the one in place is put aside in its turn. The conversations stay in claude.ai: Aiwa only remembers which they were.
 */
class AgentSessionsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            val agent = AiwaRepository.state.collectAsState().value.agent
            val entries = buildList {
                if (agent == null) { add(PickerEntry("Le cerveau ne répond pas (ou il est trop ancien : il faut la version 154).", false, lines = 2) { }); return@buildList }
                // What waits for the person's word: a proposal for a session, or settings the agent asked for (said aloud too: « vas-y, dis-lui » / « vas-y, fais-le »)
                agent.proposal?.let { p ->
                    add(PickerEntry("Proposition pour ${p.sessionTitle} :\n${p.message.take(200)}", false, header = true, lines = 6) { })
                    add(PickerEntry("Envoyer à ${p.sessionTitle}", false, lines = 1, button = true) { act("confirm", p.id) })
                    add(PickerEntry("Annuler la proposition", false, lines = 1, button = true) { act("cancel", p.id) })
                }
                agent.command?.let { c ->
                    add(PickerEntry(c.readBack + "\nDis « vas-y, fais-le » (agent ouvert).", false, header = true, lines = 4) { })
                    add(PickerEntry("Annuler ces réglages", false, lines = 1, button = true) { act("cancel", c.id) })
                }
                add(PickerEntry("＋ Nouvelle session", false, lines = 1, button = true) { startNew(agent.session != null) })
                if (agent.session != null) add(PickerEntry("En place : ${agent.repo ?: "session de l'agent"}", true, lines = 1) { })
                agent.history.forEach { old ->
                    add(PickerEntry(old.title + (old.repo?.let { "\n$it" } ?: ""), false, lines = 2) { resume(old.id) })
                }
                if (agent.history.isEmpty()) add(PickerEntry("Pas d'ancienne session pour l'instant : elles apparaissent ici quand tu en commences une nouvelle.", false, lines = 2) { })
            }
            PickerSheet(entries) { finish() }
        }
    }

    // One action on the agent (a proposal sent or cancelled), then the state is read again.
    private fun act(action: String, id: String) {
        val app = applicationContext
        finish()
        CoroutineScope(Dispatchers.Default).launch {
            val bridge = LocalClaudeBridge()
            val error = try { bridge.agent(action, mapOf("id" to id)) } catch (err: Exception) { err.message ?: "?" }
            if (error != null) toastOnMain(app, error)
            try { BackendSync.refresh(bridge) } catch (err: Exception) { }
            AiwaWidget().updateAll(app)
        }
    }

    // finish() first, work in a scope that outlives this activity.
    private fun startNew(hasOne: Boolean) {
        val app = applicationContext
        finish()
        CoroutineScope(Dispatchers.Default).launch {
            val bridge = LocalClaudeBridge()
            if (hasOne) {
                WakeWordService.closeAgentMode(app)
                val error = try { bridge.agent("destroy") } catch (err: Exception) { err.message ?: "?" }
                if (error != null) { toastOnMain(app, error); return@launch }       // « destroy » puts it among the old ones
                try { BackendSync.refresh(bridge) } catch (err: Exception) { }
            }
            app.startActivity(Intent(app, AgentRepoPickerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun resume(id: String) {
        val app = applicationContext
        finish()
        CoroutineScope(Dispatchers.Default).launch {
            val bridge = LocalClaudeBridge()
            WakeWordService.closeAgentMode(app)
            val error = try { bridge.agent("resume", mapOf("id" to id)) } catch (err: Exception) { err.message ?: "?" }
            toastOnMain(app, error ?: "Agent repris.")
            try { BackendSync.refresh(bridge) } catch (err: Exception) { }
            AiwaWidget().updateAll(app)
        }
    }
}
