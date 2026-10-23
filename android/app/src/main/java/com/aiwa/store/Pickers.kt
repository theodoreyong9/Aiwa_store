package com.aiwa.store
import androidx.compose.runtime.rememberCoroutineScope
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import com.aiwa.bridge.PillReference
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
class PickerEntry(val label: String, val active: Boolean, val header: Boolean = false, val lines: Int = 2, val side: String? = null, val onSide: () -> Unit = {}, val side2: String? = null, val onSide2: () -> Unit = {}, val side3: String? = null, val onSide3: () -> Unit = {}, val button: Boolean = false, val sub: String? = null, val chevron: Boolean = false, val toggle: Boolean? = null, val onClick: () -> Unit)

// The look of the sheets: dark whatever the phone's theme (it opens over the home screen, like the widget), compact rows, one accent.
// The glass look of the widget (blue to violet, translucent chips, a fine light edge) for every menu, list and window
internal val SheetBg = Color(0xFF1F2352)
internal val SheetBrush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF1D2F66), Color(0xFF3A2275), Color(0xFF6A2C8A)))
internal val SheetLine = Color(0x47FFFFFF)
internal val SheetText = Color(0xFFF4F4FF)
internal val SheetDim = Color(0xFFC3C6F0)
internal val SheetChip = Color(0x2EFFFFFF)
/** The glass of a card: a body lighter at the top, and an edge brighter at the top than at the bottom. */
internal val GlassBody = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0x42FFFFFF), Color(0x14FFFFFF)))
internal val GlassEdge = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0x99FFFFFF), Color(0x1FFFFFFF)))
internal val GlassSheen = androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color(0x2EFFFFFF), 0.35f to Color(0x00FFFFFF))
internal val Accent = Color(0xFFB8C4FF)

/** The sheets are dark whatever the phone's theme. */
@Composable
internal fun DarkSheetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        typography = androidx.compose.material3.Typography(
            titleMedium = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            titleSmall = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 18.sp),
            bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 17.sp),
            bodySmall = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, lineHeight = 15.sp),
            labelLarge = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
            labelMedium = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, letterSpacing = 1.sp),
        ),
        colorScheme = androidx.compose.material3.darkColorScheme(
            primary = Accent, onPrimary = Color(0xFF1C2250), background = SheetBg, onBackground = SheetText, surface = SheetBg, onSurface = SheetText, surfaceVariant = SheetChip, onSurfaceVariant = SheetDim,
            outline = Color(0x55FFFFFF), primaryContainer = Color(0x33FFFFFF), onPrimaryContainer = Color.White,
        ),
        content = content,
    )
}

/** The words of a row: a legacy label « ✓  Titre : état — détail » becomes a title, a small line under it and, for a switch, its state. */
private class RowText(val title: String, val sub: String?, val toggle: Boolean?)

private fun rowText(entry: PickerEntry): RowText {
    if (entry.sub != null || entry.toggle != null) return RowText(entry.label, entry.sub, entry.toggle)
    val raw = entry.label
    val on = raw.startsWith("✓")
    val text = raw.removePrefix("✓").trim()
    val off = Regex("^(.*?) : (coupée?|désactivée?)(.*)$").find(text)
    if (on || off != null) {
        val title = (off?.groupValues?.get(1) ?: text.substringBefore(" : ")).trim()
        val rest = (off?.groupValues?.get(3) ?: text.substringAfter(" : ", "")).trim()
        val detail = rest.removePrefix("active").removePrefix("actif").trim().trimStart('—', '-', '(').trimEnd(')').trim()
        return RowText(title, detail.ifBlank { null }, on)
    }
    val cut = Regex(" : | — ").find(text)
    return if (cut != null && entry.lines < 3) RowText(text.substring(0, cut.range.first).trim(), text.substring(cut.range.last + 1).trim().ifBlank { null }, null) else RowText(text, null, null)
}

@Composable
private fun SheetSwitch(on: Boolean) {
    Box(
        Modifier.size(width = 42.dp, height = 24.dp).background(if (on) Color(0xFF2E9E6D) else Color(0x33FFFFFF), RoundedCornerShape(12.dp)).border(1.dp, if (on) Color(0xFF52B089) else SheetLine, RoundedCornerShape(12.dp)).padding(2.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) { Box(Modifier.size(18.dp).background(Color.White, RoundedCornerShape(9.dp))) }
}

@Composable
internal fun PickerSheet(entries: List<PickerEntry>, query: String? = null, onQuery: (String) -> Unit = {}, title: String? = null, onBack: (() -> Unit)? = null, onDismiss: () -> Unit) {
    // The glass: what is behind the window (the home screen) is blurred (Android 12 and later, where the phone allows it)
    val hostView = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            var c: android.content.Context? = hostView.context
            while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
            (c as? android.app.Activity)?.window?.let { w ->
                w.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val a = w.attributes; a.blurBehindRadius = 40; w.attributes = a
            }
        }
    }
    DarkSheetTheme {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(1.dp, SheetLine),
                modifier = Modifier.padding(horizontal = 14.dp).fillMaxWidth().heightIn(max = 600.dp).background(SheetBrush, RoundedCornerShape(24.dp)).background(GlassSheen, RoundedCornerShape(24.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            ) {
                Column {
                    // the head: a back arrow when the menu is a step of another one, the title, and the close
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (onBack != null) Text("‹", color = SheetText, fontSize = 20.sp, modifier = Modifier.padding(end = 8.dp).background(SheetChip, RoundedCornerShape(14.dp)).border(1.dp, SheetLine, RoundedCornerShape(14.dp)).clickable { onBack() }.padding(horizontal = 12.dp, vertical = 2.dp))
                        Text(title ?: "", color = SheetText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = if (onBack == null) 4.dp else 0.dp))
                        Text("✕", color = SheetText, fontSize = 13.sp, modifier = Modifier.background(SheetChip, RoundedCornerShape(14.dp)).border(1.dp, SheetLine, RoundedCornerShape(14.dp)).clickable { onDismiss() }.padding(horizontal = 11.dp, vertical = 6.dp))
                    }
                    if (query != null) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).background(SheetChip, RoundedCornerShape(14.dp)).border(1.dp, SheetLine, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("⌕", color = SheetDim, fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                            androidx.compose.foundation.text.BasicTextField(
                                value = query, onValueChange = onQuery, singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(color = SheetText, fontSize = 14.sp),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(Accent),
                                modifier = Modifier.weight(1f),
                                decorationBox = { inner -> if (query.isEmpty()) Text("Rechercher, ou coller propriétaire/nom", color = SheetDim, fontSize = 14.sp); inner() },
                            )
                        }
                    }
                    LazyColumn(Modifier.padding(horizontal = 12.dp).padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(entries) { entry ->
                            if (entry.header) {
                                Text(
                                    text = entry.label.uppercase(),
                                    color = SheetDim,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.6.sp,
                                    modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 12.dp, bottom = 0.dp),
                                )
                                return@items
                            }
                            if (entry.button) {
                                // An ACTION (not an item of the list around it): a filled glass button
                                Box(
                                    Modifier.fillMaxWidth()
                                        .background(Color(0x40FFFFFF), RoundedCornerShape(16.dp))
                                        .border(1.dp, Color(0x80FFFFFF), RoundedCornerShape(16.dp))
                                        .clickable { entry.onClick() }.padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text(entry.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                                return@items
                            }
                            val text = rowText(entry)
                            val info = !entry.active && entry.side == null && entry.lines >= 3 && text.toggle == null && !entry.chevron      // a sentence that explains, not a choice
                            if (info) {
                                Text(entry.label, color = SheetDim, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp))
                                return@items
                            }
                            val shape = RoundedCornerShape(16.dp)
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(GlassBody, shape).background(if (entry.active) Color(0x33FFFFFF) else Color.Transparent, shape)
                                    .border(1.dp, GlassEdge, shape)
                                    .clickable { entry.onClick() }.padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = text.title,
                                        fontSize = 14.sp, lineHeight = 18.sp,
                                        color = SheetText,
                                        fontWeight = if (entry.active) FontWeight.SemiBold else FontWeight.Medium,
                                        maxLines = entry.lines,
                                    )
                                    if (text.sub != null) Text(text.sub, color = SheetDim, fontSize = 11.5.sp, lineHeight = 15.sp, maxLines = 3, modifier = Modifier.padding(top = 2.dp))
                                }
                                if (entry.side3 != null) Text(entry.side3, color = SheetText, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp).background(SheetChip, RoundedCornerShape(10.dp)).border(1.dp, SheetLine, RoundedCornerShape(10.dp)).clickable { entry.onSide3() }.padding(horizontal = 10.dp, vertical = 5.dp))
                                if (entry.side2 != null) Text(entry.side2, color = SheetText, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp).background(SheetChip, RoundedCornerShape(10.dp)).border(1.dp, SheetLine, RoundedCornerShape(10.dp)).clickable { entry.onSide2() }.padding(horizontal = 10.dp, vertical = 5.dp))
                                if (entry.side != null) Text(entry.side, color = SheetText, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp).background(SheetChip, RoundedCornerShape(10.dp)).border(1.dp, SheetLine, RoundedCornerShape(10.dp)).clickable { entry.onSide() }.padding(horizontal = 10.dp, vertical = 5.dp))
                                if (text.toggle != null) Box(Modifier.padding(start = 10.dp)) { SheetSwitch(text.toggle) }
                                else if (entry.chevron) Text("›", color = SheetDim, fontSize = 20.sp, modifier = Modifier.padding(start = 10.dp))
                                else if (entry.active) Text("●", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(start = 10.dp))
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
                // At the top: « + » (a new session) and « Coller » (a session made elsewhere, by the name of its branch or its link, copied)
                add(PickerEntry("＋", false, button = true) { pickCloud("new") })
                if (adding) add(PickerEntry("⏳  Recherche de la session… (quelques secondes)", false, lines = 2) { })
                else add(PickerEntry("Coller", false, button = true) { addFromClipboard() })
                // Why it did not work, in full: a toast is gone before it can be read.
                problem?.let { add(PickerEntry("⚠  $it", false, lines = 10) { }) }
                // The branch was found but no commit names the session (it has not committed yet): the link of the session completes it.
                branchRead?.let { add(PickerEntry("⎘  Coller maintenant le lien de la session (la branche $it est déjà lue)", false, lines = 3) { addFromClipboard(withBranch = it) }) }
                state.cloudSessions.forEach { c ->
                    add(PickerEntry(c.title.take(60), c.id == state.cloudSessionId, side = "✎", onSide = { renameSession(c.id, c.title) }) { pickCloud(c.id) })
                }
            }
            PickerSheet(entries, title = "Sessions") { finish() }
        }
    }

    private fun renameSession(id: String, title: String) {
        startActivity(Intent(this, SessionRenameActivity::class.java).putExtra("id", id).putExtra("title", title))
        finish()
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
            PickerSheet(entries, title = "Modèle et effort") { finish() }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            val state by AiwaRepository.state.collectAsState()
            var repos by remember { mutableStateOf<List<RepoInfo>?>(null) }
            var starting by remember { mutableStateOf(false) }
            var reload by remember { mutableStateOf(0) }
            val scope = rememberCoroutineScope()
            val note by problem
            LaunchedEffect(reload) {
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
                add(PickerEntry("＋ Créer un dépôt GitHub", false, button = true) { openNewRepoPage() })
                note?.let { add(PickerEntry("⚠  $it", false, lines = 3) { problem.value = null }) }
                val list = repos
                if (list == null) {
                    add(PickerEntry(if (starting) "Démarrage du backend (Termux)… quelques secondes" else "Chargement des dépôts…", false) { })
                } else {
                    val shown = list
                    shown.forEach { r ->
                        add(PickerEntry(r.name + if (r.isPrivate) "  (privé)" else "", r.name == state.repo, lines = 1,
                            side2 = "↗", onSide2 = { openRepoOnGithub(r.name) }) { pickRepo(r.name) })
                    }
                    if (shown.isEmpty()) add(PickerEntry("Aucun dépôt trouvé", false, lines = 1) { })
                }
                // Claude Code reaches GitHub with ITS OWN connection, made in Claude's
                // settings: Aiwa never logs in to GitHub and cannot tell whether it is
                // connected. So ONE entry, which opens the page of Claude's connectors —
                // it shows the real state and offers Connect or Disconnect accordingly.
                add(PickerEntry("🔗  Connexion GitHub de Claude : connecter, changer, déconnecter ↗", false) { openPage(CLAUDE_CONNECTORS_URL) })
            }
            PickerSheet(entries, title = "Dépôts") { finish() }
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
 * The « Contexte » button of the Aiwa-shaped widget (at the left of the microphone): the four things the next message depends on, in one list — the session, the
 * repository, the model (and its effort) and the push. Each opens its own list; the push changes at once.
 */
class ContextActivity : ComponentActivity() {
    private fun open(screen: Class<*>) {
        finish()
        startActivity(Intent(this, screen))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            val state by AiwaRepository.state.collectAsState()
            val entries = buildList {
                add(PickerEntry("Contexte du prochain message", false, header = true) { })
                add(PickerEntry("Session : ${state.session} ▾", false, lines = 2) { open(SessionPickerActivity::class.java) })
                add(PickerEntry("Dépôt : ${state.repo ?: "à choisir"}${if (state.extraRepos.isNotEmpty()) " +${state.extraRepos.size}" else ""} ▾", false, lines = 2) { open(RepoPickerActivity::class.java) })
                add(PickerEntry("Modèle : ${modelLabel(state.model)} · effort : ${PillReference.effortLabel(state.effort)} ▾", false, lines = 2) { open(ModelPickerActivity::class.java) })
                if (state.repo != null) {
                    add(PickerEntry(if (state.pushMain) "Push : direct sur main — toucher pour passer sur une branche" else "Push : sur une branche — toucher pour passer sur main", false, lines = 2) {
                        val app = applicationContext
                        val direct = state.pushMain
                        finish()
                        CoroutineScope(Dispatchers.Default).launch { switchOptions(app, LocalClaudeBridge(), pushMain = !direct) }
                    })
                }
            }
            PickerSheet(entries) { finish() }
        }
    }
}

/**
 * The widget's voice button (before the mic): the voice commands, written out, and the switch of the always-on listening. The list is the one
 * place the commands are given in the app; VoiceCommands.kt (the bridge) is what understands them.
 */
class VoiceCommandsHelpActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            val entries = buildList {
                add(PickerEntry("Le micro du widget (le mode agent)", false, header = true) { })
                add(PickerEntry("Parle : tout ce qui est dit part comme commande à la session de la pastille (ce que Claude doit faire : une page, une vidéo, des documents…). « C'est bon vas-y » envoie.", false, lines = 4) { })
                add(PickerEntry("« Non, c'est pas bon, arrête » ou « annule », à la fin : tout est abandonné.", false, lines = 3) { })

                add(PickerEntry("« Instruction » : les pastilles session et dépôt", false, header = true) { })
                add(PickerEntry("Après « instruction », le parseur ne reconnaît QUE la session et le dépôt : « instruction session Widget du matin », « instruction dépôt Aiwa store » (ou « mets-toi sur le dépôt test »). Il cherche les noms dans tes listes, relit les DEUX pastilles et attend « c'est bon vas-y » (validation) ou une correction (« non, le dépôt … »). Puis la session reçoit toujours où elle en est.", false, lines = 7) { })
                add(PickerEntry("Modèle, effort, push, qualité et déploiement ne se disent plus : ils se règlent à la main, dans les listes.", false, lines = 3) { })
                add(PickerEntry("Avec une clé Anthropic (Réglages), Claude lit toute phrase dite au micro, même sans « instruction » : « mets-toi sur le dépôt test et fais une page » règle le dépôt puis envoie le reste.", false, lines = 4) { })

                add(PickerEntry("Écoute permanente (écran verrouillé compris)", false, header = true) { })
                add(PickerEntry("« Mon agent » (réveil) : tu dictes, relecture des pastilles puis validation. « Instruction » : directement les pastilles. Tous ces mots se changent dans Réglages → Mes mots.", false, lines = 4) { })
                add(PickerEntry("« Stop » (stop Claude, arrête Claude) : arrête Claude tout de suite, et demande à la dernière session touchée par le super agent d'oublier son dernier commentaire.", false, lines = 4) { })
                add(PickerEntry("« Éteins » : éteint l'écoute permanente et le super agent. Le bouton du super agent la rallume.", false, lines = 3) { })
                add(PickerEntry("« T'es où là » : le téléphone lit les pastilles du widget (dépôt, session, modèle, push).", false, lines = 2) { })
                add(PickerEntry("« Fais ton rapport » (au super agent ouvert) : son rapport d'activité et une idée.", false, lines = 2) { })
                add(PickerEntry("« Renvoie les instructions » : la session reçoit de nouveau les instructions générales et celles des pastilles.", false, lines = 3) { })

                add(PickerEntry("Le super agent : la deuxième écoute", false, header = true) { })
                add(PickerEntry("L'ouvrir : dis « mon super agent » ou touche son bouton (le premier appui allume l'écoute). Le bouton micro est grisé tant qu'il est ouvert ; tout ce que tu dis va à lui, et il te répond à voix haute.", false, lines = 5) { })
                add(PickerEntry("Il est un intermédiaire : il ne fait rien lui-même. Il reconnaît la production demandée (documents, vidéo, page, app, contrat Aiwa), la propose à une ou plusieurs sessions, et avant que quoi que ce soit parte il en dit le résumé : le téléphone relit, tu valides (« vas-y, dis-lui ») ou tu corriges.", false, lines = 6) { })
                add(PickerEntry("Le Cast : « envoie la vidéo sur la télé du salon » (la dernière vidéo) ou, avec une production demandée, la vidéo qui va venir : elle part dès qu'elle existe.", false, lines = 4) { })
                add(PickerEntry("« Vas-y, fais-le » : pastilles session et dépôt, ou cast, relus puis validés. « Repasse en manuel » ou « ferme l'agent » : il se ferme. « Dis à l'agent … » en dictée normale lui envoie ta phrase.", false, lines = 4) { })
                add(PickerEntry("Sa vitesse : avec ta clé Anthropic il répond en une ou deux secondes ; sans clé il passe par une session cloud (10 à 30 secondes). S'il est paramétré mais l'écoute coupée, il demande d'abord s'il est disponible (notification « Oui ») avant de te faire un rapport et de proposer une idée.", false, lines = 6) { })
            }
            PickerSheet(entries) { finish() }
        }
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
                // Level 1 (AccessGate) has GitHub Pages and the Android APK only.
                DEPLOY_CHOICES.filter { state.accessLevel >= 2 || !deliversApp(it.id.orEmpty()) }.forEach { choice ->
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
        // the campaign mode simply asks for a file: its window opens at once
        if (mode == "campaign") startActivity(Intent(app, FileCreationActivity::class.java).putExtra("campaign", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}


/**
 * The settings button of the widget: ONE list. What used to be four buttons (the model, the push, the state of the workflow) AND the whole menu d'Aiwa (the wake word, the state
 * of Aiwa, the updates, the always-on listening), then ONE button for everything that can be said (the dictation instructions: VoiceCommandsHelpActivity), then the shape of
 * the widget. The pill on the widget says the state in a few words; this is where it is changed.
 */
class SettingsActivity : ComponentActivity() {
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

    private fun open(screen: Class<*>) {
        finish()
        startActivity(Intent(this, screen))
    }

    private fun togglePush(direct: Boolean) {
        val app = applicationContext
        finish()
        CoroutineScope(Dispatchers.Default).launch { switchOptions(app, LocalClaudeBridge(), pushMain = !direct) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            val state by AiwaRepository.state.collectAsState()
            val ci = when (state.ciState) { "success" -> "réussi ✓"; "failure" -> "échec ✗"; "running" -> "en cours …"; else -> "état inconnu" }
            // the switch is the person's wish; "listening" is whether the service is running (after a restart of the phone it is not, until the app is opened)
            val wanted = ListenSettings.enabled(this) || state.listening
            val entries = buildList {
                add(PickerEntry("Voix et écoute", false, header = true) { })
                run {
                    // ONE switch, on by default: the natural voice and the fluid conversation go together
                    val voiceState = state.agent?.voice ?: "none"
                    val on = NaturalVoice.enabled(applicationContext) && ConversationSettings.fluid(applicationContext)
                    val note = if (!on) " (voix du téléphone, conversation par tours)" else when (voiceState) {
                        "ready" -> ""
                        "installing" -> " — voix en cours d'installation (environ 80 Mo)"
                        "error" -> " — installation de la voix échouée : ${state.agent?.voiceError?.take(110) ?: "?"} (toucher pour réessayer)"
                        else -> " — voix pas encore installée, voix du téléphone en attendant (toucher pour installer)"
                    }
                    add(PickerEntry((if (on) "✓  Conversation naturelle : active" else "Conversation naturelle : coupée") + note, false, lines = 4) {
                        if (on && (voiceState == "error" || voiceState == "none")) {
                            CoroutineScope(Dispatchers.IO).launch { try { LocalClaudeBridge().voiceSetup() } catch (err: Exception) { } }
                            toastOnMain(applicationContext, "Installation de la voix relancée")
                        } else {
                            NaturalVoice.setEnabled(applicationContext, !on); ConversationSettings.setFluid(applicationContext, !on)
                        }
                        finish()
                    })
                }
                run {
                    val creation = CreationSettings.enabled(applicationContext)
                    add(PickerEntry(if (creation) "✓  Création à la volée : active — les ordres partent sans validation, l'avancement est raconté" else "Création à la volée : coupée (tu parles au super agent, qui relit et attend ta validation)", false, lines = 3) {
                        CreationSettings.set(applicationContext, !creation); finish()
                    })
                }
                add(PickerEntry(if (wanted) "✓  Écoute du réveil : active" else "Écoute du réveil : coupée", false, lines = 1) { toggle(); finish() })
                add(PickerEntry("Envoi", false, header = true) { })
                add(PickerEntry("Modèle : ${modelLabel(state.model)} · effort : ${PillReference.effortLabel(state.effort)}", false, lines = 2, chevron = true) { open(ModelPickerActivity::class.java) })
                if (state.repo != null) {
                    add(PickerEntry(if (state.pushMain) "Push : direct sur main" else "Push : sur une branche", false, lines = 1) { togglePush(state.pushMain) })
                }
                add(PickerEntry("Agent et IA", false, header = true) { })
                add(PickerEntry("Super agent", false, lines = 1, chevron = true) { open(AgentSessionsActivity::class.java) })
                add(PickerEntry("Clé Anthropic : ${when (state.agent?.llm) { "ready" -> "en place"; "installing" -> "installation…"; "error" -> "problème"; else -> "absente" }}", false, lines = 1, chevron = true) { open(LlmKeyActivity::class.java) })
                add(PickerEntry("Connecteurs : ${ConnectorSettings.KINDS.filter { ConnectorSettings.enabled(applicationContext, it.first) }.joinToString(", ") { it.second }.ifEmpty { "aucun" }}", false, lines = 1, chevron = true) { open(ConnectorsActivity::class.java) })
                add(PickerEntry("Contenus", false, header = true) { })
                add(PickerEntry("Film de moi : clé fal, ma voix, plafond", false, lines = 1, chevron = true) { open(FilmSettingsActivity::class.java) })
                add(PickerEntry("Banques de vidéos libres : clés Pexels et Pixabay, recherche", false, lines = 2, chevron = true) { open(StockSettingsActivity::class.java) })
                add(PickerEntry("Agents externes : une porte pour ChatGPT et d'autres (MCP)", false, lines = 2, chevron = true) { open(ExternalActivity::class.java) })
                add(PickerEntry("Parole et scénarios", false, header = true) { })
                add(PickerEntry("Scénarios : un mot lance un double de moi, ou le rapport", false, lines = 2, chevron = true) { open(ScenariosActivity::class.java) })
                add(PickerEntry("Ma façon de parler : l'agent me répond comme je parle", false, lines = 2, chevron = true) { open(StyleActivity::class.java) })
                add(PickerEntry("Mes mots", false, lines = 1, chevron = true) { open(RecognitionsActivity::class.java) })
                add(PickerEntry("Aiwa", false, header = true) { })
                add(PickerEntry("État d'Aiwa", false, lines = 1, chevron = true) { open(HealthActivity::class.java) })
                // Which build and which brain run: the numbers to compare with what GitHub says is the latest (the update entries are gone from this list)
                add(PickerEntry("Version : appli ${AppUpdater.installedVersion(applicationContext) ?: "?"} · cerveau ${AiwaRepository.state.value.backendVersion.takeIf { it > 0 } ?: "?"}${AiwaRepository.state.value.backendUpdatedAt.takeIf { it > 0 }?.let { " (mis à jour le " + java.text.SimpleDateFormat("dd/MM à HH:mm", java.util.Locale.FRANCE).format(java.util.Date(it * 1000)) + ")" } ?: ""}${if (AppUpdater.readyFile(applicationContext) != null) " · mise à jour téléchargée" else ""}", false, lines = 2) { })
                add(PickerEntry(if (ReadySettings.enabled(applicationContext)) "✓  « C'est prêt » à voix haute" else "« C'est prêt » à voix haute : coupé", false, lines = 1) {
                    ReadySettings.set(applicationContext, !ReadySettings.enabled(applicationContext)); finish()
                })
            }
            PickerSheet(entries, title = "Réglages") { finish() }
        }
    }

    override fun onResume() {
        super.onResume()
        if (WakeWordService.instance != null && ListenSettings.enabled(this)) AiwaRepository.update { s -> s.copy(listening = true) }
    }
}

/** The connectors (mail, agenda, SMS, call): one line each, off until turned on. The super agent only prepares; the app opens filled in and the person taps send/call/save. */
class ConnectorsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            var tick by remember { mutableStateOf(0) }       // the list stays open and shows the change at once
            val entries = buildList {
                add(PickerEntry("Disponibles pour les campagnes : des messages pré-remplis. L'agent prépare, tu relis et tu confirmes, puis c'est toi qui envoies depuis l'appli.", false, header = true, lines = 4) { })
                ConnectorSettings.KINDS.forEach { (kind, label) ->
                    val on = tick >= 0 && ConnectorSettings.enabled(applicationContext, kind)
                    add(PickerEntry(if (on) "✓  $label : disponible" else "$label : coupé", false, lines = 1) { ConnectorSettings.set(applicationContext, kind, !on); tick++ })
                }
            }
            PickerSheet(entries) { finish() }
        }
    }
}

/** The mode menu of the Light-shaped widget: Vidéos, Documents, Page, App, Store, Aiwa. Picking one is what a tap on the tabs of the Aiwa shape does. */
class ModePickerActivity : ComponentActivity() {
    /** A tap on the mode: the mode is chosen (and with it its own session, see ModeSessions). */
    private fun pick(mode: String) {
        val app = applicationContext
        finish()
        CoroutineScope(Dispatchers.Default).launch { selectAiwaMode(app, mode) }
    }

    /** The side button of a mode: what the mode has produced. */
    private fun open(screen: Class<*>) {
        finish()
        startActivity(Intent(applicationContext, screen).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun site(state: AiwaState): Class<*> = if (state.repo != null && state.siteUrl != null) SiteButtonActivity::class.java else RepoPickerActivity::class.java

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            val state by AiwaRepository.state.collectAsState()
            val tab = aiwaTab(state)
            val sub = mediaSub(state)
            val entries = buildList {
                add(PickerEntry("Mode · le bouton à droite ouvre sa production", false, header = true) { })
                add(PickerEntry("🎬 Vidéos", tab == "media" && sub == "video", lines = 1, side = "Ouvrir", onSide = { open(VideosActivity::class.java) }) { pick("video") })
                add(PickerEntry("📄 Documents", tab == "media" && sub == "documents", lines = 1, side = "Ouvrir", onSide = { open(DocumentsActivity::class.java) }) { pick("documents") })
                add(PickerEntry("Page", tab == "pages", lines = 1, side = "Ouvrir", onSide = { open(site(state)) }) { pick("pages") })
                add(PickerEntry("App", tab == "android", lines = 1, side = "Ouvrir", onSide = { open(site(state)) }) { pick("android") })
                if (state.accessLevel >= 2) {
                    add(PickerEntry("Aiwa", tab == "aiwa", lines = 1, side = "Ouvrir", onSide = { open(OpenStoreActivity::class.java) }) { pick("aiwa") })
                }
            }
            PickerSheet(entries) { finish() }
        }
    }
}

/**
 * The Média pill of the Aiwa-shaped widget: what to see and open — the videos, the campaign (its proposals) or the documents. Campagne and Documents are modes of the selector,
 * so picking them also sets the mode; Vidéos is only a view. The creation of a campaign from a file stays one entry away.
 */
class MediaPickerActivity : ComponentActivity() {
    private fun go(mode: String, screen: Class<*>, campaignFile: Boolean = false) {
        val app = applicationContext
        finish()
        CoroutineScope(Dispatchers.Default).launch { selectAiwaMode(app, mode) }
        app.startActivity(
            Intent(app, screen).apply { if (campaignFile) putExtra("campaign", true) }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            val state by AiwaRepository.state.collectAsState()
            val entries = buildList {
                add(PickerEntry("Média : voir et ouvrir", false, header = true) { })
                add(PickerEntry("🎬 Vidéos — la liste, ou en demander une", state.videoView, lines = 2) { go("video", VideosActivity::class.java) })
                add(PickerEntry("✉ Campagne — les propositions à valider", !state.videoView && state.deploy == "campaign", lines = 2) { go("campaign", ProposalsActivity::class.java) })
                add(PickerEntry("📄 Documents — ceux que Claude a créés", !state.videoView && state.deploy == "documents", lines = 2) { go("documents", DocumentsActivity::class.java) })
                add(PickerEntry("Nouvelle campagne à partir d'un fichier", false, lines = 2) { go("campaign", FileCreationActivity::class.java, campaignFile = true) })
            }
            PickerSheet(entries) { finish() }
        }
    }
}


/** A pair as a line: the session, then the repository. */
private fun pairLine(state: AiwaState, repo: String?, session: String): String {
    val title = if (session == "new") "Nouvelle session" else (state.cloudSessions.firstOrNull { it.id == session }?.title?.take(28) ?: session.take(8))
    return "$title · ${repo?.substringAfter('/') ?: "aucun dépôt"}"
}

/** The « Projet » button: the names given to pairs (session, repository), each removable; and the naming of the pair now on the pills when it has none. */
class ProjectPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            val state by AiwaRepository.state.collectAsState()
            var projects by remember { mutableStateOf(Projects.all(applicationContext)) }
            val session = state.cloudSessionId ?: "new"
            var chosen by remember { mutableStateOf(false) }      // the first choice: a project or a scenario
            val entries = buildList {
                if (!chosen) {
                    add(PickerEntry("Projet", false, lines = 1, button = true) { chosen = true })
                    add(PickerEntry("Scénario", false, lines = 1, button = true) { startActivity(Intent(this@ProjectPickerActivity, ScenariosActivity::class.java)); finish() })
                    return@buildList
                }
                val all = Projects.isAll(applicationContext)
                // The session and the repository are chosen here (the two pills left the widget)
                if (!all) {
                    add(PickerEntry("Session : ${state.session} ▾", false, lines = 1, button = true) { startActivity(Intent(this@ProjectPickerActivity, SessionPickerActivity::class.java)); finish() })
                    add(PickerEntry("⎇ Dépôt : ${state.repo?.substringAfter('/') ?: "aucun"} ▾", false, lines = 1, button = true) { startActivity(Intent(this@ProjectPickerActivity, RepoPickerActivity::class.java)); finish() })
                }
                // At the top, two buttons: name the pair now on the pills, and « Tous »
                if (!all && state.repo != null) add(PickerEntry("＋ Nommer la paire", false, button = true) {
                    startActivity(Intent(this@ProjectPickerActivity, ProjectNameActivity::class.java)); finish()
                })
                add(PickerEntry(if (all) "Tous : activé — toucher pour revenir" else "Tous", false, button = true) {
                    Projects.setAll(applicationContext, !all)
                    val app = applicationContext
                    finish()
                    CoroutineScope(Dispatchers.Default).launch { AiwaWidget().updateAll(app) }
                })
                projects.forEach { p ->
                    add(PickerEntry(p.name, p.repo == state.repo && p.session == session, lines = 1, side = "🗑",
                        onSide = { Projects.remove(applicationContext, p.name); projects = Projects.all(applicationContext) }) { pick(p) })
                }
            }
            PickerSheet(entries, title = if (chosen) "Projet" else "Que veux-tu lancer ?", onBack = if (chosen) ({ chosen = false }) else null) { finish() }
        }
    }

    private fun pick(project: Project) {
        val appContext = applicationContext
        finish()
        CoroutineScope(Dispatchers.Default).launch { applyProject(appContext, LocalClaudeBridge(), project) }
    }
}

/** The name of the pair now on the pills (the repository's name to begin with). */
class ProjectNameActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val state = AiwaRepository.state.value
        val repo = state.repo
        val session = state.cloudSessionId ?: "new"
        if (repo == null) { finish(); return }
        setContent {
            var text by remember { mutableStateOf(repo.substringAfter('/')) }
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
                            Text("Nommer cette paire", style = MaterialTheme.typography.titleMedium)
                            Text(pairLine(state, repo, session), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                            androidx.compose.material3.OutlinedTextField(value = text, onValueChange = { text = it.take(40) }, singleLine = true, label = { Text("Nom du projet") }, modifier = Modifier.fillMaxWidth())
                            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                                androidx.compose.material3.TextButton(onClick = { finish() }) { Text("Annuler") }
                                androidx.compose.material3.Button(enabled = text.isNotBlank(), onClick = { Projects.add(applicationContext, Project(text.trim(), repo, session)); val app = applicationContext; CoroutineScope(Dispatchers.Default).launch { AiwaWidget().updateAll(app) }; finish() }) { Text("Enregistrer") }
                            }
                        }
                    }
                }
            }
        }
    }
}


/** The repository of the super agent, chosen when it is created: its own, where it keeps its registry (the folder aiwa-agent/). */
class AgentRepoPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            val state by AiwaRepository.state.collectAsState()
            var repos by remember { mutableStateOf<List<RepoInfo>?>(null) }
            LaunchedEffect(Unit) {
                val bridge = LocalClaudeBridge()
                ensureBackend(applicationContext, bridge)
                BackendSync.refresh(bridge)
                repos = try { bridge.githubRepos() } catch (err: Exception) { emptyList() }
            }
            val entries = buildList {
                add(PickerEntry("Le dépôt de ton agent : il y écrit son registre (dossier aiwa-agent/), et nulle part ailleurs. Un dépôt à lui, privé de préférence.", false, lines = 4) { })
                add(PickerEntry("＋  Créer un dépôt GitHub pour l'agent ↗ (puis reviens ici)", false) {
                    if (!openUrl(applicationContext, "https://github.com/new?name=aiwa-agent&visibility=private&description=Registre%20de%20mon%20super%20agent%20Aiwa")) toastOnMain(applicationContext, "Impossible d'ouvrir le navigateur.")
                    finish()
                })
                val list = repos
                if (list == null) add(PickerEntry("Chargement des dépôts…", false) { })
                else {
                    list.forEach { r -> add(PickerEntry(r.name + if (r.isPrivate) "  (privé)" else "", r.name == state.agent?.repo) { create(r.name) }) }
                    if (list.isEmpty()) add(PickerEntry("Aucun dépôt trouvé", false) { })
                }
                state.repo?.let { add(PickerEntry("Utiliser le dépôt en cours ($it)", false) { create(it) }) }
            }
            PickerSheet(entries) { finish() }
        }
    }

    private fun create(repo: String) {
        val appContext = applicationContext
        finish()
        CoroutineScope(Dispatchers.Default).launch {
            val bridge = LocalClaudeBridge()
            val error = try { bridge.agent("create", mapOf("repo" to repo)) } catch (err: Exception) { err.message ?: "?" }
            if (error != null) toastOnMain(appContext, error) else toastOnMain(appContext, "Création de l'agent sur $repo… quelques secondes.")
            try { BackendSync.refresh(bridge) } catch (err: Exception) { }
            AiwaWidget().updateAll(appContext)
        }
    }
}
