package com.aiwa.store
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.aiwa.bridge.RepoInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Reported live: the model list in the widget showed only "Auto". A
// widget cannot draw an overlay dropdown, so the list had to fit in
// whatever few dp were left under the row — about one entry. Each of the
// widget's two list buttons now opens one of these small floating
// windows (same translucent, own-task setup as DictateActivity) instead,
// so the widget itself can stay a single compact row.

// header: a section title, not something to tap. lines: how many lines the label may take.
// side: a second, small button at the end of the row (a repository's page on GitHub, for instance) with its own action.
class PickerEntry(val label: String, val active: Boolean, val header: Boolean = false, val lines: Int = 2, val side: String? = null, val onSide: () -> Unit = {}, val onClick: () -> Unit)

@Composable
private fun PickerSheet(entries: List<PickerEntry>, onDismiss: () -> Unit) {
    MaterialTheme {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 6.dp,
                modifier = Modifier.padding(24.dp).fillMaxWidth().heightIn(max = 460.dp),
            ) {
                LazyColumn(Modifier.padding(vertical = 8.dp)) {
                    items(entries) { entry ->
                        if (entry.header) {
                            Text(
                                text = entry.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
                            )
                        } else {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = (if (entry.active) "●  " else "    ") + entry.label,
                                    color = if (entry.active) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                    maxLines = entry.lines,
                                    modifier = Modifier.weight(1f).clickable { entry.onClick() }
                                        .padding(start = 20.dp, end = if (entry.side == null) 20.dp else 8.dp, top = 14.dp, bottom = 14.dp),
                                )
                                if (entry.side != null) {
                                    Text(
                                        text = entry.side,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { entry.onSide() }.padding(start = 12.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class SessionPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            val state by AiwaRepository.state.collectAsState()
            var confirmClear by remember { mutableStateOf(false) }
            val adding by addingSession
            val problem by addProblem
            val branchRead by pendingBranch
            // Fresh list from the backend every time the picker opens (which
            // is started first if it isn't running).
            LaunchedEffect(Unit) {
                val bridge = LocalClaudeBridge()
                ensureBackend(applicationContext, bridge)
                BackendSync.refresh(bridge)
            }
            val entries = buildList {
                add(PickerEntry("+  Nouvelle session", state.cloudSessionId == null) { pickCloud("new") })
                state.cloudSessions.forEach { c ->
                    add(PickerEntry(c.title.take(60), c.id == state.cloudSessionId) { pickCloud(c.id) })
                }
                // The CLI has no command to list the account's cloud
                // sessions, so one made elsewhere is added by pasting the
                // name of its branch (the rarely used entries come last).
                if (adding) add(PickerEntry("⏳  Recherche de la session à partir de la branche… (quelques secondes)", false, lines = 2) { })
                else add(PickerEntry("⎘  Ajouter une session existante (nom de branche copié)", false) { addFromClipboard() })
                // Why it did not work, in full: a toast is gone before it can be read.
                problem?.let { add(PickerEntry("⚠  $it", false, lines = 10) { }) }
                // The branch was found but no commit names the session (it has not committed yet): the link of the session completes it.
                branchRead?.let { add(PickerEntry("⎘  Coller maintenant le lien de la session (la branche $it est déjà lue)", false, lines = 3) { addFromClipboard(withBranch = it) }) }
                // Emptied in two taps: the list is only Aiwa's, nothing is deleted in Claude.
                if (state.cloudSessions.isNotEmpty()) {
                    if (!confirmClear) add(PickerEntry("🗑  Vider cette liste…", false) { confirmClear = true })
                    else add(PickerEntry("⚠  Confirmer : vider la liste (les sessions restent dans Claude)", false, lines = 2) { clearList() })
                }
            }
            PickerSheet(entries) { finish() }
        }
    }

    // finish() first, work in a scope that outlives this activity.
    private fun pickCloud(target: String) {
        val appContext = applicationContext
        finish()
        CoroutineScope(Dispatchers.Default).launch { switchCloud(appContext, LocalClaudeBridge(), target) }
    }

    private fun clearList() {
        val appContext = applicationContext
        finish()
        CoroutineScope(Dispatchers.Default).launch { clearSessionList(appContext, LocalClaudeBridge()) }
    }

    private val addingSession = mutableStateOf(false)
    private val addProblem = mutableStateOf<String?>(null)
    private val pendingBranch = mutableStateOf<String?>(null)

    // Read here, on the main thread of the focused activity: that is the
    // only place Android hands the clipboard over. The picker stays open until the answer is known, so that the reason
    // of a failure can be read.
    private fun addFromClipboard(withBranch: String? = null) {
        val text = clipboardText(this)
        if (text.isNullOrBlank()) {
            addProblem.value = EMPTY_CLIPBOARD_FOR_SESSION
            return
        }
        val appContext = applicationContext
        val whole = if (withBranch != null) "$withBranch $text" else text
        addingSession.value = true
        addProblem.value = null
        CoroutineScope(Dispatchers.Main).launch {
            val problem = withContext(Dispatchers.IO) { tryAddCloudSession(appContext, LocalClaudeBridge(), whole) }
            addingSession.value = false
            if (problem == null) { finish(); return@launch }
            addProblem.value = problem
            // "no commit names the session": keep the branch, ask for the link
            pendingBranch.value = if (problem.contains("ne mentionne la session")) BRANCH_IN_TEXT.find(whole)?.value else null
        }
    }
}

// Model and effort, like the Claude app's own chip ("Sonnet 5.5 · Moyen"):
// both are sent to the open session (/model, /effort) and remembered for the
// next new one.
class ModelPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            val state by AiwaRepository.state.collectAsState()
            LaunchedEffect(Unit) {
                val bridge = LocalClaudeBridge()
                ensureBackend(applicationContext, bridge)
                BackendSync.refresh(bridge)
            }
            val entries = buildList {
                add(PickerEntry("Modèle — envoyé à la session (/model)", false, header = true) { })
                MODEL_CHOICES.forEach { choice ->
                    add(PickerEntry(choice.label, choice.id == state.model) { pickModel(choice.id) })
                }
                add(PickerEntry("Effort de raisonnement", false, header = true) { })
                EFFORT_CHOICES.forEach { choice ->
                    add(PickerEntry(choice.label, choice.id == state.effort) { pickEffort(choice.id) })
                }
            }
            PickerSheet(entries) { finish() }
        }
    }

    private fun pickModel(modelId: String?) {
        val appContext = applicationContext
        finish()
        CoroutineScope(Dispatchers.Default).launch { switchModel(appContext, LocalClaudeBridge(), modelId) }
    }

    private fun pickEffort(level: String?) {
        val appContext = applicationContext
        finish()
        CoroutineScope(Dispatchers.Default).launch { switchEffort(appContext, LocalClaudeBridge(), level) }
    }
}

// The repository Claude works on and pushes to: the session in progress is told with its next message, and the next new session starts there. The list is discovered on its own by the backend: the
// public repositories of the owner of the Aiwa checkout and of the owners
// of repositories already used, plus the ones of an existing `gh` login.
class RepoPickerActivity : ComponentActivity() {
    private val problem = mutableStateOf<String?>(null)

    // Read here, on the main thread of the focused activity: the only place Android hands the clipboard over.
    private fun forkFromClipboard() {
        val ref = parseRepoRef(clipboardText(this))
        if (ref == null) {
            problem.value = "Copie d'abord le lien du dépôt à copier (sur GitHub : l'adresse de sa page), puis reviens ici."
            return
        }
        toastOnMain(applicationContext, "Sur GitHub, touche « Create fork ». Ta copie apparaîtra dans cette liste.")
        if (!openUrl(applicationContext, "https://github.com/$ref/fork")) toastOnMain(applicationContext, "Impossible d'ouvrir le navigateur.")
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            val state by AiwaRepository.state.collectAsState()
            var repos by remember { mutableStateOf<List<RepoInfo>?>(null) }
            var starting by remember { mutableStateOf(false) }
            val note by problem
            LaunchedEffect(Unit) {
                val bridge = LocalClaudeBridge()
                // The backend may not be running (a widget tap never went
                // through the app): start it and wait, don't show an empty list.
                val up = try { bridge.status(); true } catch (err: Exception) { !isBackendUnreachable(err) }
                if (!up) {
                    starting = true
                    ensureBackend(applicationContext, bridge)
                    starting = false
                }
                BackendSync.refresh(bridge)
                repos = try { bridge.githubRepos() } catch (err: Exception) { emptyList() }
            }
            val entries = buildList {
                // Every session starts on a repository: there is no free conversation.
                if (state.repo == null) add(PickerEntry("Choisis le dépôt sur lequel Claude travaille :", false) { })
                // Right under the first line, not after a list that can run to a hundred names. The new repository shows up in
                // the list when this window is opened again (the backend re-reads the owner's list when it is older than 30 s).
                add(PickerEntry("＋  Créer un dépôt GitHub ↗", false) { openNewRepoPage() })
                // A copy of someone's repository in the person's account: GitHub's own fork page, for the address copied. Right there under
                // the line above, and whether or not a repository is chosen (it used to sit two screens deep, and only once one was).
                add(PickerEntry("⑂  Copier un dépôt dans mon compte (fork) ↗ — copie d'abord son lien", false) { forkFromClipboard() })
                note?.let { add(PickerEntry("⚠  $it", false, lines = 3) { problem.value = null }) }
                val list = repos
                if (list == null) {
                    add(PickerEntry(if (starting) "Démarrage du backend (Termux)… quelques secondes" else "Chargement des dépôts…", false) { })
                } else {
                    list.forEach { r ->
                        add(PickerEntry(r.name + if (r.isPrivate) "  (privé)" else "", r.name == state.repo, side = "↗", onSide = { openRepoOnGithub(r.name) }) { pickRepo(r.name) })
                    }
                    if (list.isEmpty()) add(PickerEntry("Aucun dépôt trouvé", false) { })
                }
                // Repositories Claude may ALSO work on, told to it with the next message.
                if (state.repo != null) {
                    val n = state.extraRepos.size
                    add(PickerEntry("＋  Autres dépôts où Claude peut intervenir" + (if (n > 0) " ($n)" else "") + "…", n > 0) { openExtraRepos() })
                }
                val sources = state.sourceRepos.size
                add(PickerEntry("＋  Dépôts d'inspiration, en lecture seule" + (if (sources > 0) " ($sources)" else "") + "…", sources > 0) { openSourceRepos() })
                // Claude Code reaches GitHub with ITS OWN connection, made in Claude's
                // settings: Aiwa never logs in to GitHub and cannot tell whether it is
                // connected. So ONE entry, which opens the page of Claude's connectors —
                // it shows the real state and offers Connect or Disconnect accordingly.
                add(PickerEntry("🔗  Connexion GitHub de Claude : connecter, changer, déconnecter ↗", false) { openPage(CLAUDE_CONNECTORS_URL) })
            }
            PickerSheet(entries) { finish() }
        }
    }

    private fun pickRepo(repo: String) {
        val appContext = applicationContext
        finish()
        CoroutineScope(Dispatchers.Default).launch { switchRepo(appContext, LocalClaudeBridge(), repo) }
    }

    private fun openExtraRepos() {
        startActivity(Intent(this, ExtraReposPickerActivity::class.java))
        finish()
    }

    private fun openSourceRepos() {
        startActivity(Intent(this, SourceReposPickerActivity::class.java))
        finish()
    }

    private fun openPage(url: String) {
        if (!openUrl(applicationContext, url)) toastOnMain(applicationContext, "Impossible d'ouvrir le navigateur.")
        finish()
    }

    private fun openNewRepoPage() = openPage(GITHUB_NEW_REPO_URL)

    // The repository's own page on GitHub, in the browser; the list stays open.
    private fun openRepoOnGithub(repo: String) {
        if (!openUrl(applicationContext, "https://github.com/$repo")) toastOnMain(applicationContext, "Impossible d'ouvrir le navigateur.")
    }
}

// GitHub's own page for creating a repository (the browser is logged in there, Aiwa never is).
private const val GITHUB_NEW_REPO_URL = "https://github.com/new"

// Claude's list of connectors: GitHub is connected, switched to another account or
// disconnected there, and the page shows which of those applies.
private const val CLAUDE_CONNECTORS_URL = "https://claude.ai/customize/connectors"

/**
 * The repositories Claude may ALSO work on, checked here (the picker stays open:
 * several can be checked). The primary repository is the one the session starts on;
 * these are only NAMED to Claude in the instructions of the next message, with the
 * request to attach them itself (`add_repo`) when it needs them — the platform, not
 * the text, decides whether it can reach them (the GitHub connection of the Claude
 * account).
 */
class ExtraReposPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            val state by AiwaRepository.state.collectAsState()
            var repos by remember { mutableStateOf<List<RepoInfo>?>(null) }
            LaunchedEffect(Unit) {
                val bridge = LocalClaudeBridge()
                BackendSync.refresh(bridge)
                repos = try { bridge.githubRepos() } catch (err: Exception) { emptyList() }
            }
            val entries = buildList {
                add(PickerEntry("Claude peut aussi intervenir sur (dès le prochain message) — ${state.repo?.substringAfter('/') ?: "?"} reste le dépôt principal", false, header = true) { })
                val list = repos
                if (list == null) {
                    add(PickerEntry("Chargement des dépôts…", false) { })
                } else {
                    list.filter { it.name != state.repo }.forEach { r ->
                        add(PickerEntry(r.name + if (r.isPrivate) "  (privé)" else "", r.name in state.extraRepos) { toggle(r.name) })
                    }
                    if (state.extraRepos.isNotEmpty()) add(PickerEntry("Tout décocher", false) { toggle(null) })
                }
            }
            PickerSheet(entries) { finish() }
        }
    }

    private fun toggle(repo: String?) {
        val appContext = applicationContext
        CoroutineScope(Dispatchers.Default).launch { switchExtraRepo(appContext, LocalClaudeBridge(), repo) }
    }
}

/**
 * The repositories Claude READS for inspiration — the user's own or anybody's, public ones: Claude is told to look at them
 * (the platform attaches them for reading when it asks) and never to change them or push to them. Added from an address copied
 * in the browser (a link or owner/name), removed with the ✕. The picker stays open.
 *
 * Copying a repository into the user's account (a fork) is GitHub's own page: Aiwa never logs in to GitHub and holds no token, so it
 * cannot do it itself. The entry opens that page for the copied address; once forked, the copy is an ordinary repository of the list.
 */
class SourceReposPickerActivity : ComponentActivity() {
    private val problem = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            val state by AiwaRepository.state.collectAsState()
            val note by problem
            LaunchedEffect(Unit) { BackendSync.refresh(LocalClaudeBridge()) }
            val entries = buildList {
                add(PickerEntry("Dépôts d'inspiration : Claude les lit, il ne les modifie jamais", false, header = true) { })
                state.sourceRepos.forEach { r ->
                    add(PickerEntry(r, true, side = "✕", onSide = { switchSource(r) }) { openRepoOnGithub(r) })
                }
                if (state.sourceRepos.isEmpty()) add(PickerEntry("Aucun pour l'instant", false) { })
                note?.let { add(PickerEntry("⚠  $it", false, lines = 4) { problem.value = null }) }
                add(PickerEntry("＋  Ajouter le dépôt copié (lien GitHub ou owner/nom)", false) { addFromClipboard() })
                add(PickerEntry("⑂  Faire une copie dans mon compte (le lien copié) ↗", false) { forkFromClipboard() })
                if (state.sourceRepos.isNotEmpty()) add(PickerEntry("Tout retirer", false) { switchSource(null) })
            }
            PickerSheet(entries) { finish() }
        }
    }

    // Read here, on the main thread of the focused activity: the only place Android hands the clipboard over.
    private fun addFromClipboard() {
        val text = clipboardText(this)
        if (parseRepoRef(text) == null) {
            problem.value = "Copie d'abord le lien du dépôt (dans GitHub : le lien de la page du dépôt), puis reviens ici."
            return
        }
        problem.value = null
        switchSource(text)
    }

    private fun forkFromClipboard() {
        val ref = parseRepoRef(clipboardText(this))
        if (ref == null) {
            problem.value = "Copie d'abord le lien du dépôt à copier, puis reviens ici."
            return
        }
        problem.value = null
        toastOnMain(applicationContext, "Sur GitHub, touche « Create fork ». Ta copie apparaîtra dans la liste des dépôts.")
        if (!openUrl(applicationContext, "https://github.com/$ref/fork")) toastOnMain(applicationContext, "Impossible d'ouvrir le navigateur.")
    }

    private fun switchSource(text: String?) {
        val appContext = applicationContext
        CoroutineScope(Dispatchers.Main).launch {
            val failed = withContext(Dispatchers.IO) { switchSourceRepo(appContext, LocalClaudeBridge(), text) }
            problem.value = failed
        }
    }

    private fun openRepoOnGithub(repo: String) {
        if (!openUrl(applicationContext, "https://github.com/$repo")) toastOnMain(applicationContext, "Impossible d'ouvrir le navigateur.")
    }
}

/**
 * The widget's voice button (before the mic): the voice commands, written out, and the switch of the always-on listening. The list is the one
 * place the commands are given in the app; VoiceCommands.kt (the bridge) is what understands them.
 */
class VoiceHelpActivity : ComponentActivity() {
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) turnOn() else toastOnMain(applicationContext, "Écoute permanente : sans la permission du micro, rien à faire.")
    }

    private fun turnOn() {
        ListenSettings.setEnabled(this, true)
        WakeWordService.start(this)
    }

    private fun toggle() {
        if (ListenSettings.enabled(this)) {
            ListenSettings.setEnabled(this, false)
            WakeWordService.stop(this)
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            turnOn()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            val state by AiwaRepository.state.collectAsState()
            // the switch is the person's wish; "listening" is whether the service is running (after a restart of the phone it is not, until the app is opened)
            val wanted = ListenSettings.enabled(this) || state.listening
            val entries = buildList {
                add(PickerEntry("Écoute permanente", false, header = true) { })
                add(PickerEntry(if (wanted) "✓  Allumée : le téléphone écoute « mon agent », « instruction » et « stop Claude » — toucher pour l'éteindre" else "Éteinte — toucher pour l'allumer", wanted, lines = 3) { toggle() })
                if (ListenSettings.enabled(this@VoiceHelpActivity) && !state.listening) {
                    add(PickerEntry("⚠  Allumée mais arrêtée (le téléphone a redémarré ?) — toucher pour la relancer", false, lines = 3) { turnOn() })
                }
                add(PickerEntry("Elle reste sur le téléphone (rien n'est envoyé). Le point vert du micro reste affiché et la batterie baisse plus vite. 41 Mo à télécharger la première fois.", false, lines = 4) { })

                add(PickerEntry("Dictée (le micro)", false, header = true) { })
                add(PickerEntry("Parle : c'est du texte pour Claude. « C'est bon vas-y » envoie.", false, lines = 3) { })
                add(PickerEntry("« Non, c'est pas bon, arrête » ou « annule », à la fin : tout est abandonné.", false, lines = 3) { })

                add(PickerEntry("Après « instruction Aiwa » (enchaîne autant que tu veux)", false, header = true) { })
                add(PickerEntry("modèle Opus · Sonnet · Haiku · Fable (+ version « 5.5 », « un million ») · automatique", false, lines = 3) { })
                add(PickerEntry("push main · push branche", false) { })
                add(PickerEntry("déploiement Pages · Android · Store · Aiwa · aucun", false, lines = 3) { })
                add(PickerEntry("nouvelle session · session + un bout du titre", false, lines = 3) { })
                add(PickerEntry("dépôt + son nom (le téléphone demande confirmation à voix haute)", false, lines = 3) { })
                add(PickerEntry("t'es sur quoi ? (rapport, statut, bilan) : le téléphone lit les pastilles", false, lines = 3) { })
                add(PickerEntry("stop · arrête Claude · arrête-toi : demande à Claude de s'arrêter (il le lit à sa prochaine étape)", false, lines = 3) { })
                add(PickerEntry("Tout s'applique à « c'est bon vas-y » ; ce qui n'est pas une commande reste du texte pour Claude.", false, lines = 3) { })

                add(PickerEntry("Écoute permanente : les mots de réveil", false, header = true) { })
                add(PickerEntry("« Mon agent » : il t'écoute, puis tu donnes tes instructions. Seul un changement de dépôt est relu et attend « c'est bon vas-y » ; le reste part comme tu l'as dit.", false, lines = 5) { })
                add(PickerEntry("« Instruction » : tu donnes directement tes instructions (même relecture).", false, lines = 3) { })
                add(PickerEntry("« Stop Claude » ou « arrête Claude » : arrête Claude tout de suite, sans confirmation.", false, lines = 3) { })
            }
            PickerSheet(entries) { finish() }
        }
    }

    override fun onResume() {
        super.onResume()
        WakeWordService.instance?.let { AiwaRepository.update { s -> s.copy(listening = true) } }
    }
}

/**
 * "État d'Aiwa": what works and what is missing, each line saying what to do about it. Opened
 * from the widget's status line when Claude's cloud environment does not reach the relay (the
 * alerts "Claude attend ta réponse"), and from the app.
 *
 * The one thing Aiwa cannot do for the user: the relay (ntfy.sh) must be allowed in the network
 * access of the cloud ENVIRONMENT, a setting that only claude.ai/code can change — there is no
 * API or CLI for it. So the entry copies "ntfy.sh", opens claude.ai/code and says what to click.
 */
class HealthActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            val state by AiwaRepository.state.collectAsState()
            LaunchedEffect(Unit) {
                val bridge = LocalClaudeBridge()
                ensureBackend(applicationContext, bridge)
                BackendSync.refresh(bridge)
            }
            val entries = buildList {
                add(PickerEntry("État d'Aiwa", false, header = true) { })
                add(
                    PickerEntry(
                        if (state.backend == "up") "✓  Backend (Termux) : en marche"
                        else if (state.backend == "missing") "⚠  Backend " + (if (state.backendMissing == "termux") ": Termux n'est pas installé" else "non installé dans Termux") + " — toucher pour copier la ligne d'installation"
                        else "⚠  Backend " + (if (state.backend == "starting") "en démarrage…" else "arrêté") + " — toucher pour relancer",
                        state.backend == "up",
                    ) { if (state.backend == "missing") installBackend() else restartBackend() },
                )
                when (state.claudeLogin) {
                    "ok" -> add(PickerEntry("✓  Claude : connecté", true) { })
                    "needed" -> add(PickerEntry("⚠  Claude n'est pas connecté — connecter…", false) { openLogin() })
                    else -> add(PickerEntry("…  Claude : connexion pas vérifiée — vérifier ou connecter…", false) { openLogin() })
                }
                when (state.relayCloud) {
                    "ok" -> add(PickerEntry("✓  Alertes du cloud : le relais ntfy.sh répond", true) { })
                    "pending" -> add(PickerEntry("⏳  Alertes du cloud : test en cours (la réponse de Claude peut prendre une minute)…", false, lines = 3) { })
                    "missing" -> {
                        add(PickerEntry("⚠  Alertes du cloud bloquées : le cloud ne joint pas ntfy.sh — autoriser…", false, lines = 3) { allowRelay() })
                        add(
                            PickerEntry(
                                "Sur claude.ai/code : ton environnement (la roue crantée) → Accès réseau « Custom » → Domaines autorisés : " +
                                    "ajoute ntfy.sh (et coche « Also include default list » pour garder les autres). « ntfy.sh » est copié quand tu touches la ligne du dessus ; ensuite, « Retester ».",
                                false, lines = 7,
                            ) { },
                        )
                        add(PickerEntry("Retester maintenant (demandé à la session en cours)", false) { retest() })
                    }
                    else -> {
                        add(PickerEntry("…  Alertes du cloud : pas encore testées (le test part avec le premier message d'une nouvelle session)", false, lines = 3) { })
                        add(PickerEntry("Tester maintenant (demandé à la session en cours)", false) { retest() })
                    }
                }
                // What the Actions button is about: which GitHub run the backend follows.
                state.ciDetail?.let { detail ->
                    add(PickerEntry("Dernier résultat GitHub suivi : " + detail, false, lines = 4) { })
                }
                add(PickerEntry("Permissions Termux et notifications…", false) { go(SetupActivity::class.java) })
                add(PickerEntry("Choisir la session…", false) { go(SessionPickerActivity::class.java) })
            }
            PickerSheet(entries) { finish() }
        }
    }

    private fun go(target: Class<*>) {
        startActivity(Intent(this, target))
        finish()
    }

    private fun openLogin() = go(ClaudeLoginActivity::class.java)

    private fun installBackend() {
        startActivity(Intent(this, InstallHelpActivity::class.java))
        finish()
    }

    private fun restartBackend() {
        startAiwaBackendViaTermux(applicationContext, forceRestart = true)
        toastOnMain(this, "Relance du backend demandée à Termux (10 à 20 s).")
        finish()
    }

    private fun allowRelay() {
        copyToClipboard(this, "ntfy.sh")
        if (!openUrl(applicationContext, "https://claude.ai/code")) toastOnMain(this, "Impossible d'ouvrir le navigateur : va sur claude.ai/code.")
        else toastOnMain(this, "« ntfy.sh » est copié : colle-le dans les domaines autorisés de ton environnement.")
        finish()
    }

    private fun retest() {
        val app = applicationContext
        finish()
        CoroutineScope(Dispatchers.Default).launch {
            val bridge = LocalClaudeBridge()
            try {
                bridge.relayRetest()
                toastOnMain(app, "Test demandé à la session : la réponse de Claude peut prendre une minute.")
            } catch (err: Exception) {
                toastOnMain(app, "Test non envoyé : ${err.message}")
            }
            BackendSync.refresh(bridge)
            AiwaWidget().updateAll(app)
        }
    }
}

/**
 * What Claude is asked to produce with the work: the widget's Deploy chip opens this list (it used
 * to cycle through the four states at each tap). Told to Claude with the next message.
 */
class DeployPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            val state by AiwaRepository.state.collectAsState()
            val entries = buildList {
                add(PickerEntry("Que doit produire Claude ? (dès le prochain message)", false, header = true) { })
                DEPLOY_CHOICES.forEach { choice ->
                    add(PickerEntry(choice.label, choice.id == state.deploy, lines = 3) { pick(choice.id) })
                }
            }
            PickerSheet(entries) { finish() }
        }
    }

    private fun pick(mode: String?) {
        val app = applicationContext
        finish()
        CoroutineScope(Dispatchers.Default).launch { switchOptions(app, LocalClaudeBridge(), deploy = mode) }
    }
}
