package com.aiwa.store
import android.content.Context
import androidx.glance.appwidget.updateAll
import com.aiwa.bridge.BackendOutdatedException
import com.aiwa.bridge.BusyException
import com.aiwa.bridge.ClaudeBridge
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Whether this process is itself waiting for the backend's answer to a message. */
object SendTracker {
    val inFlight = AtomicBoolean(false)
}

/**
 * The one real send path — shared by MainActivity's "Envoyer"/mic and
 * DictateActivity's own mic, instead of each duplicating the same
 * coroutine (a duplicated copy once drifted).
 *
 * The message goes to the current Claude Code cloud session (a new one
 * when none is selected). Documented limit: the CLI only queues it — the
 * reply can't be read back — so Aiwa shows no conversation at all: the
 * exchange lives in the Claude app, one tap away ("Claude ↗"). Refused up
 * front while another send is running. Returns whether the message went.
 *
 * The send is not cancelled with the screen that started it (leaving the app must not turn a message that
 * went into an error), and the widget's "Envoi en cours…" does not depend on this process alone: the backend
 * says whether it is still working on a message (BackendSync), so opening or leaving the app does not wipe it.
 */
suspend fun sendAndTrack(context: Context, bridge: ClaudeBridge, text: String, toastErrors: Boolean = false): Boolean {
    if (text.isBlank()) return false
    accessNotice(AiwaRepository.state.value, 1)?.let {
        if (toastErrors) toastOnMain(context, it)
        return false
    }
    if (AiwaRepository.state.value.needsRepo) {
        if (toastErrors) toastOnMain(context, "Choisis d'abord un dépôt (le bouton ⎇ du widget) : chaque session démarre sur un dépôt.")
        return false
    }
    if (!SendTracker.inFlight.compareAndSet(false, true)) {
        tellBusy(context, toastErrors)
        return false
    }
    return try {
        // Working because the backend says so (a send begun before the app was reopened): not a second one.
        if (AiwaRepository.state.value.status == AiwaState.Status.WORKING) {
            tellBusy(context, toastErrors)
            false
        } else {
            withContext(NonCancellable) { deliver(context, bridge, text, toastErrors) }
        }
    } finally {
        SendTracker.inFlight.set(false)
    }
}

/**
 * A message was refused because another is on its way: it did not go. The widget says so for a moment (a toast
 * alone is gone in a second, and a widget-only user may not see it), then goes back to its usual line.
 */
fun tellBusy(context: Context, toast: Boolean) {
    AiwaRepository.update { it.copy(busyNoticeAt = System.currentTimeMillis()) }
    CoroutineScope(Dispatchers.Default).launch {
        AiwaWidget().updateAll(context)
        delay(BUSY_NOTICE_MS + 500)
        AiwaWidget().updateAll(context)
    }
    if (toast) toastOnMain(context, "Un envoi est déjà en cours : celui-ci n'est pas parti, renvoie-le quand « Envoi en cours… » disparaît.")
}

private suspend fun deliver(context: Context, bridge: ClaudeBridge, text: String, toastErrors: Boolean): Boolean {
    AiwaRepository.update { it.copy(status = AiwaState.Status.WORKING, notice = null, sendingSince = System.currentTimeMillis()) }
    // Fire-and-forget: makes sure a widget composition is alive to show
    // the WORKING state and the result.
    CoroutineScope(Dispatchers.Default).launch { AiwaWidget().updateAll(context) }
    var failure: String? = null
    var busy = false
    try {
        val result = bridge.sendCloud(text)
        if (!result.ok) failure = "Envoi cloud impossible : ${result.error}"
    } catch (err: BusyException) {
        // The backend is working on an earlier message: that one is the real send, and the widget goes on saying so. This one did not go.
        busy = true
    } catch (err: Exception) {
        // A widget-only user never opens the app, so the backend's
        // auto-start there never runs — trigger it from here too.
        failure = when {
            isBackendUnreachable(err) -> autoStartBackendMessage(context)
            err is BackendOutdatedException -> err.message ?: "Backend obsolète"
            else -> "[erreur: ${err.message}]"
        }
    }
    if (busy) {
        tellBusy(context, toastErrors)
        BackendSync.refresh(bridge)
        return false
    }
    AiwaRepository.update {
        it.copy(status = if (failure == null) AiwaState.Status.DONE else AiwaState.Status.ERROR, notice = failure)
    }
    if (toastErrors) toastOnMain(context, failure ?: "Envoyé — réponse dans l'appli Claude")
    BackendSync.refresh(bridge)
    return failure == null
}
