package com.aiwa.store
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.glance.appwidget.updateAll
import com.aiwa.bridge.BackendOutdatedException
import com.aiwa.bridge.BusyException
import com.aiwa.bridge.ClaudeBridge
import com.aiwa.bridge.LocalClaudeBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The widget has no message area, so its errors are toasts. */
fun toastOnMain(context: Context, text: String) {
    val appContext = context.applicationContext
    Handler(Looper.getMainLooper()).post { Toast.makeText(appContext, text, Toast.LENGTH_LONG).show() }
}

private fun describeFailure(context: Context, err: Exception, what: String): String = when {
    isBackendUnreachable(err) -> autoStartBackendMessage(context)
    err is BackendOutdatedException -> err.message ?: "Backend obsolète"
    else -> "$what : ${err.message}"
}

// A GitHub repository in a link or a git remote, or a plain owner/name —
// the shapes the backend accepts.
val GITHUB_REPO_IN_TEXT = Regex("github\\.com[/:][A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+|^\\s*[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+\\s*$")

/**
 * The text on the clipboard, if any. Must be called from a foreground
 * activity on the main thread (Android only hands the clipboard to the
 * app in focus).
 */
fun clipboardText(context: Context): String? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = manager.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

/**
 * The one way to change the current cloud session, shared by the widget's
 * picker and the app's dropdown (a duplicated copy would drift, as
 * sendAndTrack's once did). It only ASKS the backend, then re-reads the
 * backend's real state — never a local guess. target "new" = the next
 * message creates a session, otherwise the id of an existing one.
 */
suspend fun switchCloud(context: Context, bridge: ClaudeBridge, target: String) {
    try {
        bridge.selectCloud(target)
        AiwaRepository.update { it.copy(notice = null) }
    } catch (err: BusyException) {
        // Nothing to do: the refresh below shows what is real.
    } catch (err: Exception) {
        toastOnMain(context, describeFailure(context, err, "Impossible de changer de session"))
    }
    BackendSync.refresh(bridge)
    AiwaWidget().updateAll(context)
}

// The name of a branch of a Claude Code session, wherever it sits in what was copied (the shape the backend accepts).
val BRANCH_IN_TEXT = Regex("claude/[A-Za-z0-9._/-]+")

/**
 * Adds an existing cloud session from the name of its branch (claude/…, copied from Claude Code), with the session's link too if the
 * branch alone does not name it, and selects it. Null when it was added; otherwise why not, in the backend's own words (a toast is too
 * short to read them: the picker shows them).
 */
suspend fun tryAddCloudSession(context: Context, bridge: ClaudeBridge, text: String): String? {
    val problem = try {
        bridge.addCloud(text)
        AiwaRepository.update { it.copy(notice = null) }
        null
    } catch (err: Exception) {
        describeFailure(context, err, "Impossible d'ajouter la session")
    }
    BackendSync.refresh(bridge)
    // The branch is in several repositories: Aiwa does not pick one (it would be a guess about where Claude pushes).
    if (problem == null && AiwaRepository.state.value.repo == null) {
        toastOnMain(context, "Session ajoutée. Sa branche existe dans plusieurs dépôts : choisis celui sur lequel elle travaille (⎇, point rouge).")
    }
    AiwaWidget().updateAll(context)
    return problem
}

/** Empties the list of sessions Aiwa keeps. The sessions themselves stay in Claude. */
suspend fun clearSessionList(context: Context, bridge: ClaudeBridge) {
    try {
        bridge.clearCloudSessions()
        AiwaRepository.update { it.copy(notice = null) }
        toastOnMain(context, "Liste vidée : les sessions restent dans Claude.")
    } catch (err: Exception) {
        toastOnMain(context, describeFailure(context, err, "Impossible de vider la liste"))
    }
    BackendSync.refresh(bridge)
    AiwaWidget().updateAll(context)
}

/**
 * modelId null = the CLI's own default. The backend remembers it for the
 * next NEW cloud session (`claude --model`); when a session is already
 * open the choice is also sent to it as `/model <alias>`, the documented
 * way to change a cloud session's model. That message is queued like any
 * other, so the result is confirmed in the Claude app, not here.
 */
suspend fun switchModel(context: Context, bridge: ClaudeBridge, modelId: String?) {
    var accepted = false
    try {
        bridge.selectModel(modelId)
        accepted = true
    } catch (err: BusyException) {
        // Same as above: the refresh below shows what is real.
    } catch (err: Exception) {
        toastOnMain(context, describeFailure(context, err, "Impossible de changer de modèle"))
    }
    BackendSync.refresh(bridge)
    AiwaWidget().updateAll(context)
    if (!accepted || AiwaRepository.state.value.cloudSessionId == null) return
    val label = modelLabel(modelId)
    try {
        val result = bridge.sendCloudCommand("/model " + (modelId ?: "default"))
        toastOnMain(
            context,
            if (result.ok) "Modèle « $label » demandé à la session (à confirmer dans Claude ↗)"
            else "Modèle « $label » non transmis à la session : ${result.error}",
        )
    } catch (err: BusyException) {
        toastOnMain(context, "Un envoi est en cours : le modèle « $label » n'a pas été transmis à la session, choisis-le à nouveau.")
    } catch (err: Exception) {
        toastOnMain(context, describeFailure(context, err, "Impossible de transmettre le modèle à la session"))
    }
}

/** The repository Claude works on: the session in progress is told with its next message (it stays); with none, the next session starts there. */
suspend fun switchRepo(context: Context, bridge: ClaudeBridge, repo: String) {
    try {
        bridge.selectRepo(repo)
    } catch (err: Exception) {
        toastOnMain(context, describeFailure(context, err, "Impossible de changer de dépôt"))
    }
    BackendSync.refresh(bridge)
    AiwaWidget().updateAll(context)
}

/** owner/name out of an address copied from GitHub (a link, a git remote) or typed as owner/name, or null. */
fun parseRepoRef(text: String?): String? {
    val t = text?.trim() ?: return null
    val link = Regex("github\\.com[:/]([A-Za-z0-9_.-]{1,100})/([A-Za-z0-9_.-]{1,100}?)(?:\\.git)?(?:[/#?\\s].*)?$", RegexOption.DOT_MATCHES_ALL).find(t)
    if (link != null) return link.groupValues[1] + "/" + link.groupValues[2]
    return Regex("^([A-Za-z0-9_.-]{1,100})/([A-Za-z0-9_.-]{1,100})$").find(t)?.value
}

/** Adds a repository to read for inspiration (or removes it when it already is one); null empties the list. Returns what went wrong, or null. */
suspend fun switchSourceRepo(context: Context, bridge: ClaudeBridge, text: String?): String? {
    var problem: String? = null
    try {
        bridge.toggleSourceRepo(text)
    } catch (err: IllegalStateException) {
        problem = if (err.message?.contains("au plus") == true) "Trop de dépôts d'inspiration : retires-en un d'abord." else "Ce n'est pas l'adresse d'un dépôt GitHub."
    } catch (err: Exception) {
        problem = describeFailure(context, err, "Impossible de changer les dépôts d'inspiration")
    }
    BackendSync.refresh(bridge)
    AiwaWidget().updateAll(context)
    return problem
}

/** The news the red dot of a button was about is looked at: the dot goes. */
fun acknowledgeNews(context: Context) {
    if (!AiwaRepository.state.value.ciFresh) return
    AiwaRepository.update { it.copy(ciFresh = false) }
    val appContext = context.applicationContext
    CoroutineScope(Dispatchers.IO).launch {
        val bridge = LocalClaudeBridge()
        try { bridge.ciSeen() } catch (err: Exception) { }
        BackendSync.refresh(bridge)
        AiwaWidget().updateAll(appContext)
    }
}

/**
 * Changes the instructions integrated into the conversation; only the
 * arguments that are not null. They apply to the next message (and, for
 * the push mode, to the next new session).
 */
suspend fun switchOptions(
    context: Context,
    bridge: ClaudeBridge,
    pushMain: Boolean? = null,
    deploy: String? = null,
    extra: String? = null,
) {
    try {
        bridge.setOptions(pushMain, deploy, extra)
    } catch (err: Exception) {
        toastOnMain(context, describeFailure(context, err, "Impossible de changer les consignes"))
    }
    BackendSync.refresh(bridge)
    AiwaWidget().updateAll(context)
}

/**
 * Checks or unchecks a repository Claude may ALSO work on (null = none). Told to it
 * with the next message; whether it can reach the repository is up to the platform.
 */
suspend fun switchExtraRepo(context: Context, bridge: ClaudeBridge, repo: String?) {
    try {
        bridge.toggleExtraRepo(repo)
    } catch (err: Exception) {
        toastOnMain(context, describeFailure(context, err, "Impossible de changer les dépôts supplémentaires"))
    }
    BackendSync.refresh(bridge)
    AiwaWidget().updateAll(context)
}

/**
 * Effort level (null = automatic): remembered for the next NEW session
 * (`claude --effort`) and, when a session is open, sent to it as
 * `/effort <level>` — cloud sessions document /effort as taking its value
 * as an argument, like /model.
 */
suspend fun switchEffort(context: Context, bridge: ClaudeBridge, level: String?) {
    var accepted = false
    try {
        bridge.selectEffort(level)
        accepted = true
    } catch (err: Exception) {
        toastOnMain(context, describeFailure(context, err, "Impossible de changer l'effort"))
    }
    BackendSync.refresh(bridge)
    AiwaWidget().updateAll(context)
    if (!accepted || AiwaRepository.state.value.cloudSessionId == null) return
    val label = EFFORT_CHOICES.find { it.id == level }?.label ?: "Auto"
    try {
        val result = bridge.sendCloudCommand("/effort " + (level ?: "auto"))
        toastOnMain(
            context,
            if (result.ok) "Effort « $label » demandé à la session (à confirmer dans Claude ↗)"
            else "Effort « $label » non transmis à la session : ${result.error}",
        )
    } catch (err: BusyException) {
        toastOnMain(context, "Un envoi est en cours : l'effort « $label » n'a pas été transmis à la session, choisis-le à nouveau.")
    } catch (err: Exception) {
        toastOnMain(context, describeFailure(context, err, "Impossible de transmettre l'effort à la session"))
    }
}

/** The clipboard is empty: say what to copy. Anything else is judged by the backend, which explains itself. */
const val EMPTY_CLIPBOARD_FOR_SESSION =
    "Le presse-papiers est vide. Dans Claude Code, copie le nom de la branche de la session (claude/…)."

/** A repository given as a GitHub link or owner/name (e.g. copied from the browser): remembered and selected. */
suspend fun addRepoFromText(context: Context, bridge: ClaudeBridge, text: String) {
    try {
        bridge.addRepo(text)
    } catch (err: Exception) {
        toastOnMain(context, describeFailure(context, err, "Impossible d'ajouter le dépôt"))
    }
    BackendSync.refresh(bridge)
    AiwaWidget().updateAll(context)
}

/**
 * "Ready to see": opens the result of the work — the site when its address
 * answers, otherwise the latest Actions run (or the Actions page) — and,
 * when that was news (a new green run), tells the backend the user went to look.
 */
fun openResult(context: Context): Boolean {
    val state = AiwaRepository.state.value
    val repo = state.repo ?: return false
    val site = state.siteUrl
    val target = if (state.siteState == "live" && site != null) site else state.ciUrl ?: "https://github.com/$repo/actions"
    val opened = openUrl(context, target)
    if (opened && state.ciFresh) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val bridge = LocalClaudeBridge()
            try { bridge.ciSeen() } catch (err: Exception) { }
            BackendSync.refresh(bridge)
            AiwaWidget().updateAll(appContext)
        }
    }
    return opened
}

/** Puts text on the clipboard. */
fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText("Aiwa", text))
}

/** Opens a link in whatever handles it (the browser). */
fun openUrl(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (err: ActivityNotFoundException) {
    false
}

private const val CLAUDE_APP_PACKAGE = "com.anthropic.claude"

/**
 * Opens the current cloud session (or, when the next message will start a
 * new one, the last one) where its conversation actually lives:
 * the Claude app (Code tab). Aiwa can't show cloud replies itself, so this
 * is the one way to read them. The app is asked first (it may claim
 * claude.ai/code links); without it, whatever handles the link — the
 * browser — gets it. With no session at all (the list was emptied, nothing sent yet) it opens
 * Claude's Code tab, where one can be made or found. Returns false when nothing could open the link.
 */
fun openClaudeApp(context: Context): Boolean {
    val state = AiwaRepository.state.value
    val sessionId = state.cloudSessionId ?: state.lastSessionId
    val url = sessionId?.let { id -> state.cloudSessions.find { it.id == id }?.url }
    val target = Uri.parse(url?.takeIf { it.startsWith("https://claude.ai/") } ?: if (sessionId != null) "https://claude.ai/code/$sessionId" else "https://claude.ai/code")
    fun view() = Intent(Intent.ACTION_VIEW, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val opened = try {
        context.startActivity(view().setPackage(CLAUDE_APP_PACKAGE))
        true
    } catch (err: ActivityNotFoundException) {
        try {
            context.startActivity(view())
            true
        } catch (err2: ActivityNotFoundException) {
            false
        }
    }
    if (opened && state.waiting) {
        // The user goes to answer: the waiting indicator has done its job.
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val bridge = LocalClaudeBridge()
            try { bridge.waitingClear() } catch (err: Exception) { }
            BackendSync.refresh(bridge)
            AiwaWidget().updateAll(appContext)
        }
    }
    return opened
}

/**
 * What is sent to Claude's session to stop it ("stop Claude", "arrête Claude", said at any moment). It is a message like any other: the session
 * reads it at its next step. It is not a hard interruption of the running step (the CLI has one, for its own sessions; it was not tried here).
 */
const val STOP_CLAUDE_TEXT = "STOP. Arrête immédiatement ce que tu fais : ne lance plus aucun outil, ne modifie plus rien, ne pousse plus rien. " +
    "Dis-moi en deux phrases où tu en es, ce qui est fait et ce qui ne l'est pas, puis attends mes instructions."

/** Asks Claude's session to stop. True when the message went. */
suspend fun stopClaude(context: Context): Boolean {
    val sent = sendAndTrack(context, LocalClaudeBridge(), STOP_CLAUDE_TEXT, toastErrors = true)
    AiwaWidget().updateAll(context)
    return sent
}
