package com.aiwa.store
import kotlinx.coroutines.launch
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
// The Intent overload of actionStartActivity lives in the appwidget module.
import androidx.glance.appwidget.action.actionStartActivity as actionStartIntent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.updateAll
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.*
import com.aiwa.bridge.LocalClaudeBridge
import com.aiwa.bridge.STORE_APP_URL
import com.aiwa.bridge.storeTabUrl
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample

// ONE shape, not resizable (dictation_widget_info.xml): four bands sharing the whole
// height on a dark gradient card — the session (the logo that opens the
// app, the name, what is going on, the round Claude button), repository and model, Push / Deploy / site / Actions,
// and the big grey dictation button with its red dot. FullContent below. What
// follows is the older two-row layout, kept as CompactContent for a launcher
// whose cells are too small for four bands. Two rows on a dark card. Conversation: [the logo: opens the Aiwa app] [session ▾]
// [grey mic with a red recording dot] [model ▾] and, once there is a session,
// the Claude button (a round orange spark; a red pill with a dot, and a red edge
// on the card, while Claude waits for an answer). Project, when the widget is
// tall enough: [repository ▾] [Push main / branche] [Deploy] and the round
// buttons for the site (globe) and the GitHub Actions (their colour is how the
// last run went), the badge on the dictation button (a spinning circle while work is under way, a green tick once there
// is a new green run to look at).
// EVERYTHING IS ALWAYS THERE at any width the widget can be resized to: the
// weighted chips (session, repository) give way, their text cut to what fits,
// nothing is dropped. No conversation text: a cloud session's replies can't be
// read back by a program, so they live in the Claude app. Each ▾ button opens a
// small floating picker window (Pickers.kt): a widget cannot draw an overlay
// dropdown.
class AiwaWidget : GlanceAppWidget() {
    /**
     * The widget on the screen is the one in plain views (FastWidget), no longer a Glance one. Every `AiwaWidget().updateAll(context)` of the app lands here (a member wins over the
     * extension of the same name) and draws the fast widget: Glance would have drawn its old layout back over it, with the slow switch of tabs.
     */
    suspend fun updateAll(context: Context) = FastWidget.render(context)

    // Exact: the composition learns the real size (LocalSize), so the
    // GitHub row only shows when there is room for it.
    override val sizeMode: SizeMode = SizeMode.Exact

    @OptIn(FlowPreview::class)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Placing the widget or the launcher redrawing it wakes the keep-alive
        // service too (and so the backend) — no need to open the app first.
        wakeAiwa(context)
        val bridge = LocalClaudeBridge()
        provideContent {
            // Observed, NOT captured: Glance keeps this composition alive
            // for a while and answers updateAll() by recomposing it, so a
            // value read once here would stay stale — which is why the
            // header used to lag behind the app until some later,
            // unrelated tap forced a brand-new composition.
            val flow = remember { AiwaRepository.state.sample(300) }
            val state by flow.collectAsState(initial = AiwaRepository.state.value)
            LaunchedEffect(Unit) { BackendSync.refresh(bridge) }
            LaunchedEffect(state.repo) { Productions.refresh(context.applicationContext, state.repo) }
            Content(state)
        }
    }
}

// Glance's ColorProvider(Int) overload takes a @ColorRes RESOURCE id,
// not a packed color: passing android.graphics.Color.rgb(...) straight
// in compiles but crashes at real render time on device. Wrapping in
// Compose's own Color(Int) first is the real fix (confirmed live).
private fun rgb(colorInt: Int) = ColorProvider(androidx.compose.ui.graphics.Color(colorInt))

private const val GAP = 6f

// Text widths are estimates (12 sp: about 6.8 dp per plain character and 12 dp
// per symbol, scaled by the user's font size): enough to cut a name to what fits
// before the widget clips it in the middle of a letter.
private fun textWidth(text: String, fontScale: Float): Float {
    var width = 0f
    for (c in text) width += if (c.code < 0x250) 6.8f else 12f
    return width * fontScale
}


private fun fitLabel(text: String, room: Float, fontScale: Float): String {
    if (textWidth(text, fontScale) <= room) return text
    var out = text
    while (out.isNotEmpty() && textWidth("$out…", fontScale) > room) out = out.dropLast(1)
    return if (out.isEmpty()) "…" else "$out…"
}


// While a message is on its way the settings and the mic are locked and dimmed (they were still tappable, which was pointless and
// confusing): a tap says why. What only looks (the site, the app, the code, Actions, Claude) stays alive.
private fun lockable(locked: Boolean, action: Action): Action = if (locked) actionRunCallback<BusyTapCallback>() else action
private val PILL_DIM = rgb(android.graphics.Color.rgb(34, 34, 42))
private val MIC_DIM = rgb(android.graphics.Color.rgb(58, 58, 66))
private val TEXT_DIM = rgb(android.graphics.Color.rgb(130, 130, 144))

// The globe / the download: the site of the repository. What it opens depends on the state at the moment of the tap (SiteButtonActivity), and a tap
// is also looking at the news the red dot was about.
private val siteAction: Action get() = actionStartActivity<SiteButtonActivity>()


// The mic records; with no repository chosen there is nothing to send to yet, so it opens the repository list instead.
// The backend is not installed (in Termux): the voice cannot be sent anywhere. The writing screen opens instead, with the steps to install it.
private fun micAction(needsRepo: Boolean, backendMissing: Boolean, noAccess: Boolean = false): Action = when {
    noAccess -> actionStartActivity<AccessActivity>()
    backendMissing -> actionStartActivity<MainActivity>()
    needsRepo -> actionStartActivity<RepoPickerActivity>()
    else -> actionStartActivity<DictateActivity>()
}




// Every button of the widget is one of these two shapes (34 dp high in the
// compact layout, sized to the room in the full one).
// badge: a small red dot on the corner: something is asked of the person here (the repository to choose).
@Composable
private fun Chip(
    text: String,
    background: ColorProvider,
    color: ColorProvider,
    action: Action,
    modifier: GlanceModifier = GlanceModifier,
    bold: Boolean = false,
    alignStart: Boolean = false,
    height: Dp = 34.dp,
    badge: Boolean = false,
    textSize: TextUnit = 12.sp,
    hPad: Dp = 10.dp,
) {
    if (!badge) {
        ChipFace(text, background, color, action, modifier.height(height), bold, alignStart, height, textSize, hPad)
        return
    }
    // Only a chip whose width is given (a weighted one) can carry the dot: the face then fills the box the dot sits on.
    Box(modifier = modifier.height(height), contentAlignment = Alignment.TopEnd) {
        ChipFace(text, background, color, action, GlanceModifier.fillMaxSize(), bold, alignStart, height, textSize, hPad)
        Box(modifier = GlanceModifier.padding(top = 1.dp, end = 6.dp)) {
            Box(modifier = GlanceModifier.size(11.dp).background(rgb(android.graphics.Color.rgb(255, 59, 48))).cornerRadius(6.dp).clickable(action)) { }
        }
    }
}

@Composable
private fun ChipFace(
    text: String,
    background: ColorProvider,
    color: ColorProvider,
    action: Action,
    modifier: GlanceModifier,
    bold: Boolean,
    alignStart: Boolean,
    height: Dp,
    textSize: TextUnit,
    hPad: Dp,
) {
    Box(
        modifier = modifier.background(background).cornerRadius(height / 2).padding(horizontal = hPad).clickable(action),
        contentAlignment = if (alignStart) Alignment.CenterStart else Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(color = color, fontSize = textSize, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium),
            maxLines = 1,
        )
    }
}

// badge: a small red dot on the corner: something new happened that this button leads to (a deployment, a build).
@Composable
private fun RoundButton(icon: Int, description: String, background: ColorProvider, action: Action, diameter: Dp = 34.dp, badge: Boolean = false) {
    Box(modifier = GlanceModifier.size(diameter), contentAlignment = Alignment.TopEnd) {
        Box(
            modifier = GlanceModifier.size(diameter).background(background).cornerRadius(diameter / 2).clickable(action),
            contentAlignment = Alignment.Center,
        ) {
            Image(provider = ImageProvider(icon), contentDescription = description, modifier = GlanceModifier.size(18.dp))
        }
        if (badge) {
            Box(modifier = GlanceModifier.size(11.dp).background(rgb(android.graphics.Color.rgb(255, 59, 48))).cornerRadius(6.dp).clickable(action)) { }
        }
    }
}

/** The spinner: a workflow is awaited (a production was asked and no new green run has come). A tap goes and looks at the workflow. */
@Composable
private fun WorkflowSpinner(diameter: Dp) {
    Box(
        modifier = GlanceModifier.size(diameter).background(rgb(android.graphics.Color.rgb(204, 120, 92))).cornerRadius(diameter / 2).clickable(actionStartActivity<OpenActionsActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        Text("⟳", style = TextStyle(color = rgb(android.graphics.Color.rgb(255, 255, 255)), fontSize = (diameter.value * 0.7f).sp, fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

@Composable
private fun Content(state: AiwaState) {
    LightContent(state)
}

// What the top of the widget says (the session and what is going on) and what a tap on it opens: the fix for what is wrong, else the sessions. Shared by both shapes.
internal class BandInfo(val title: String, val statusText: String, val statusColor: ColorProvider, val statusBold: Boolean, val action: Action)

// The four rows have a name, top to bottom: Activité, Domaine, Agence, Spécialiste. The name is in a small column of its own at the left of the row, NOT in the chips.
private const val ROW_NAME_W = 56f
private const val ROW_ACTIVITY = "Activité"
private const val ROW_DOMAIN = "Domaine"
private const val ROW_AGENCY = "Agence"
private const val ROW_SPECIALIST = "Spécialiste"


@Composable
internal fun bandInfo(state: AiwaState, titleRoom: Float): BandInfo {
    val fg = rgb(android.graphics.Color.rgb(240, 240, 245))
    val subtle = rgb(android.graphics.Color.rgb(160, 160, 174))
    val warm = rgb(android.graphics.Color.rgb(232, 150, 124))
    val alertText = rgb(android.graphics.Color.rgb(255, 138, 133))
    val locked = state.status == AiwaState.Status.WORKING
    val fontScale = LocalContext.current.resources.configuration.fontScale
    val needsLogin = state.claudeLogin == "needed"
    val storeMode = deliversApp(state.deploy)
    val storeReady = storeMode && state.sentApp?.seen == false
    val status: String
    val statusColor: ColorProvider
    var statusBold = false
    var batteryLine = false
    when {
        accessNotice(state, 1) != null -> { status = "⚠ Accès réservé — touche ici"; statusColor = alertText; statusBold = true }
        // Not "starting": it is not there. The tap copies the one line that installs it (InstallHelpActivity).
        state.backend == "missing" -> {
            status = if (state.backendMissing == "termux") "⚠ Termux manquant — touche ici" else "⚠ Installation manquante — touche ici"
            statusColor = alertText; statusBold = true
        }
        state.backend == "starting" -> { status = "⏳ Démarrage du backend…"; statusColor = warm }
        state.backend == "down" -> { status = "⚠ Backend arrêté — relance en cours"; statusColor = alertText; statusBold = true }
        needsLogin -> { status = "⚠ Claude n'est pas connecté — touche ici"; statusColor = alertText; statusBold = true }
        // A second message was just refused because this one is still on its way: said here, not only in a toast.
        state.status == AiwaState.Status.WORKING -> {
            val refused = System.currentTimeMillis() - state.busyNoticeAt < BUSY_NOTICE_MS
            val seconds = if (state.sendingSince > 0) (System.currentTimeMillis() - state.sendingSince) / 1000 else 0L
            val note = state.sendingNote
            // What it is doing, when the backend says (the CLI's own line: an upload's progress, for instance), after the seconds.
            status = when {
                refused -> "⏳ Envoi en cours : attends"
                note != null -> (if (seconds >= 5) "$seconds s · " else "") + note
                else -> "Envoi en cours…" + (if (seconds >= 5) " $seconds s" else "")
            }
            statusColor = if (refused) warm else fg
            statusBold = refused
        }
        state.ciHintTitle != null -> { status = "⚠ GitHub : un réglage à faire — touche ⚠"; statusColor = alertText; statusBold = true }
        state.waiting -> { status = "● Claude attend ta réponse"; statusColor = alertText; statusBold = true }
        // Nothing came back by any road for a while after a message: only « go and look » can be said (the answer cannot be read from here)
        state.silenceMinutes > 0 -> { status = "⏳ Rien de Claude depuis ${state.silenceMinutes} min — touche ici"; statusColor = warm; statusBold = true }
        // A loop between two sessions is running: where it is
        state.loopPair.isNotEmpty() -> { status = "🔁 Boucle ${state.loopPair} : tour ${state.loopRounds + 1}/${state.loopLimit}, au tour du ${if (state.loopTurn == "critic") "critique" else "builder"}"; statusColor = fg; statusBold = false }
        storeReady -> {
            status = "App prête : touche ▦ pour l'ouvrir dans le Store, </> montre son code"
            statusColor = fg
            statusBold = true
        }
        // The last send did not go: its reason was only a toast, and "Prêt" made it look as if nothing was wrong.
        state.status == AiwaState.Status.ERROR && state.notice != null -> { status = "⚠ Dernier envoi échoué — touche ici"; statusColor = alertText; statusBold = true }
        // Claude's GitHub app is not on this repository: a session started on it has an empty folder, and it will again.
        state.repoAccessMissing != null && state.repoAccessMissing == state.repo -> { status = "⚠ Claude n'a pas accès à ce dépôt"; statusColor = alertText; statusBold = true }
        // Every session starts on a repository: none chosen, nothing to send yet.
        state.needsRepo -> { status = "Choisis un dépôt (⎇) pour commencer"; statusColor = warm; statusBold = true }
        // A session with no repository (added by a branch that exists in several of them: none is guessed): it is told which when one is chosen.
        state.repo == null && state.cloudSessionId != null -> { status = "Choisis le dépôt de cette session (⎇)"; statusColor = warm; statusBold = true }
        // Android may stop Aiwa in the background: said, and one tap shows its own question
        state.batteryRestricted -> { status = "⚠ Autorise Aiwa en arrière-plan — touche ici"; statusColor = warm; statusBold = true; batteryLine = true }
        else -> { status = "Prêt"; statusColor = subtle }
    }
    val title = when {
        accessNotice(state, 1) != null -> "Accès réservé ▸"
        state.backend == "missing" -> "Installer le backend ▸"
        needsLogin -> "Connecter Claude ▸"
        else -> fitLabel(state.session, titleRoom - textWidth(" ▾", fontScale * 1.25f), fontScale * 1.25f) + " ▾"
    }
    val statusText = fitLabel(status, titleRoom, fontScale * 0.92f)
    // What a tap on the name and the status line opens: the fix for what is wrong, else the sessions.
    val bandAction: Action = when {
        batteryLine -> actionStartActivity<BatteryActivity>()
        accessNotice(state, 1) != null -> actionStartActivity<AccessActivity>()
        state.backend == "missing" -> actionStartActivity<InstallHelpActivity>()
        needsLogin -> actionStartActivity<ClaudeLoginActivity>()
        state.silenceMinutes > 0 && state.status != AiwaState.Status.WORKING -> actionStartActivity<OpenClaudeActivity>()
        state.status == AiwaState.Status.ERROR && state.notice != null -> actionStartActivity<MainActivity>()
        state.needsRepo || state.repo == null -> actionStartActivity<RepoPickerActivity>()
        else -> lockable(locked, actionStartActivity<SessionPickerActivity>())
    }
    return BandInfo(title, statusText, statusColor, statusBold, bandAction)
}


// ================================================================================================
// The third shape, « Aiwa »: the modes in a row at the top (Média · Page · App · Store · Aiwa; Média holds Vidéo, Campagne and Documents); what the chosen mode needs; the mic, Claude, the question,
// the model, the push and the state of the workflow; and a bottom row (loop, the Aiwa app, the update when there is one, the settings). Session and repository
// are in the list of the « contexte » button. A tap on a mode IS the deploy selector (Média and Vidéo only change what is shown).
// ================================================================================================

private val TAB_KEY = ActionParameters.Key<String>("aiwa-tab")

/** Which tab of the widget is open (domain, files or build): kept by the phone, changed by a tap on a tab and by the super agent when it touches a button of another tab. */
object WidgetTab {
    private const val FILE = "aiwa_widget_tab"
    fun get(context: Context): String = try { context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("tab", "build") ?: "build" } catch (err: Exception) { "build" }
    fun set(context: Context, tab: String) { try { context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("tab", tab).apply() } catch (err: Exception) { } }
    /** The tab that holds the button the super agent touched (null: the button is on no tab). */
    fun of(button: String): String? = when (button) {
        "settings", "wallet", "campaign" -> "domain"
        "documents", "video", "pages", "android", "aiwa", "store", "project", "loops" -> "files"
        "claude", "agent", "plus", "listen" -> "build"
        "voir" -> "voir"
        else -> null
    }
}

class PickTabCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetTab.set(context, parameters[TAB_KEY] ?: return)
        AiwaWidget().updateAll(context)
    }
}

private val MODE_KEY = ActionParameters.Key<String>("aiwa-mode")

/** The modes of the selector that belong to the Média tab (the Vidéo one is no mode: only a view). */
private val MEDIA_DEPLOYS = setOf("campaign", "documents")

/** The tab shown: Média while Vidéo is the view or while the selector is on Campagne or Documents; else the mode of the selector. */
internal fun aiwaTab(state: AiwaState): String = if (state.videoView || state.deploy in MEDIA_DEPLOYS) "media" else state.deploy

/** In the Média tab, what is shown: "video" (the view) or the mode of the selector (campaign, documents). */
internal fun mediaSub(state: AiwaState): String = if (state.videoView) "video" else state.deploy

/**
 * A choice of the tabs or of the Média list: the widget shows it AT ONCE (the backend takes its time; what is held in the state is what a recomposition reads), and the
 * selector follows. "media" and "video" only change the view (Vidéo is no mode); every other id is a mode of the selector.
 */
suspend fun selectAiwaMode(context: Context, mode: String) {
    val app = context.applicationContext
    val before = AiwaRepository.state.value
    if (mode == "media" || mode == "video") {
        // Média alone leaves what the selector is on when it already belongs to it; else it shows the videos
        if (mode == "video" || before.deploy !in MEDIA_DEPLOYS) {
            WidgetStyle.setVideoView(app, true)
            AiwaRepository.update { it.copy(videoView = true) }
        }
        AiwaWidget().updateAll(app)
        return
    }
    WidgetStyle.setVideoView(app, false)
    AiwaRepository.update { it.copy(videoView = false, deploy = mode) }
    AiwaWidget().updateAll(app)
    if (before.deploy != mode) switchOptions(app, LocalClaudeBridge(), deploy = mode)
}



// ================================================================================================
// The fourth shape, « Light »: three rows and nothing else. (1) the mode as ONE dropdown (Média, Page, App, Store, Aiwa — the same for every mode) and the targets of the
// mode, which are the person's own: Prod, Code, Workflow. (2) the settings, the update (when one is ready), the microphone, Claude and the question (when there is one).
// (3) the loop, the Aiwa app, the super agent and the campaign; the settings hold everything else (session, repository, model, push…).
// ================================================================================================

/** One of the five small buttons above the session and the repository: Docs, Vidéo, Page, App, Aiwa. */
internal class LightKind(val mode: String, val word: String, val found: Boolean, val current: Boolean)

internal fun lightKinds(state: AiwaState, context: Context): List<LightKind> {
    val hasSite = state.repo != null && state.siteUrl != null
    val tab = aiwaTab(state)
    val sub = mediaSub(state)
    if (Projects.isAll(context)) {
        // « Tous »: every repository is read when a kind is opened, so all five are ready
        return listOf("documents" to "Docs", "video" to "Vidéo", "pages" to "Page", "android" to "App", "aiwa" to "Aiwa").map { (mode, word) -> LightKind(mode, word, true, false) }
    }
    return listOf(
        LightKind("documents", "Docs", Productions.has(context, state.repo, "documents"), tab == "media" && sub == "documents"),
        LightKind("video", "Média", Productions.has(context, state.repo, "video"), tab == "media" && sub == "video"),
        LightKind("pages", "Page", Productions.has(context, state.repo, "page") || hasSite && state.siteKind != "apk" && state.siteState == "live", tab == "pages"),
        LightKind("android", "App", Productions.has(context, state.repo, "apk") || hasSite && state.siteKind == "apk" && state.siteState == "live", tab == "android"),
        LightKind("aiwa", "Aiwa", Productions.has(context, state.repo, "aiwa"), tab == "aiwa"),
    )
}

private val KIND_FOUND_KEY = ActionParameters.Key<String>("aiwa-kind-found")

/** A tap on one of the five: the mode is chosen (with its own session) and, when something was produced, it opens. */
class PickKindCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val mode = parameters[MODE_KEY] ?: return
        if (Projects.isAll(context)) {
            val kind = if (mode == "pages") "pages" else mode
            context.startActivity(Intent(context, AllActivity::class.java).putExtra("kind", kind).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        openProduction(context.applicationContext, mode, parameters[KIND_FOUND_KEY] == "1")
    }
}

/** Opens what the repository produced of one kind (« documents », « video », « pages », « android », « aiwa »): the same for the tap on one of the five and for the super agent (« ouvre ma vidéo »). */
/** The other buttons of the widget, for the super agent: the very screen a tap on the button opens (the toggles of the ear and of the robot too). A Wallet payment is never validated from here: the screen only opens. */
fun openWidgetButton(app: Context, name: String): Boolean {
    WidgetTab.of(name)?.let { WidgetTab.set(app, it); kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch { AiwaWidget().updateAll(app) } }
    val repo = AiwaRepository.state.value.repo
    val intent = when (name) {
        "wallet", "store" -> storePageIntent(app, name, repo)
        "settings" -> Intent(app, SettingsActivity::class.java)
        "loops" -> Intent(app, LoopsActivity::class.java)
        "campaign" -> Intent(app, ProposalsActivity::class.java)
        "question" -> Intent(app, AskActivity::class.java)
        "claude" -> Intent(app, OpenClaudeActivity::class.java)
        "plus" -> Intent(app, PlusMenuActivity::class.java)
        "update" -> Intent(app, InstallUpdateActivity::class.java)
        "project" -> Intent(app, ProjectPickerActivity::class.java)
        "voir" -> FastWidget.voirIntent(app, AiwaRepository.state.value, lightKinds(AiwaRepository.state.value, app))
        "listen" -> Intent(app, ListenToggleActivity::class.java)
        "agent" -> Intent(app, AgentToggleActivity::class.java)
        else -> return false
    }
    app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    return true
}

suspend fun openProduction(app: Context, mode: String, found: Boolean? = null) {
    if (openWidgetButton(app, mode)) return
    WidgetTab.of(mode)?.let { WidgetTab.set(app, it); runCatching { AiwaWidget().updateAll(app) } }
    val state = AiwaRepository.state.value
    val repo = state.repo
    val kind = if (mode == "pages") "page" else if (mode == "android") "apk" else mode
    val view = { url: String -> app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    // What is known (the widget's own grey / white) decides at once; the network is asked only when it says "nothing", and then only to be sure
    var has = found ?: Productions.has(app, repo, kind)
    // Vidéo and Docs open even when grey (the window to ask for one; the folder or a word that there is none yet); the others only when something was produced
    if (!has && mode != "video" && mode != "documents") {
        Productions.refresh(app, repo, force = true)
        has = Productions.has(app, repo, kind)
        if (!has) { toastOnMain(app, "Rien à ouvrir pour l'instant dans ce dépôt."); return }
    }
    // The window opens first: choosing the mode (which redraws the widget) and the check of the repository come after, off the way
    when (mode) {
        "documents" -> app.startActivity(Intent(app, DocumentsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        "video" -> app.startActivity(Intent(app, MediaActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        "aiwa" -> app.startActivity(Intent(app, OpenStoreActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        "pages" -> if (repo != null) view("https://${repo.substringBefore('/')}.github.io/${repo.substringAfter('/')}/")
        else -> if (repo != null) view("https://github.com/$repo/releases")
    }
    selectAiwaMode(app, mode)
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { runCatching { Productions.refresh(app, repo, force = true) } }
}

@Composable
private fun LightContent(state: AiwaState) {
    val fg = rgb(android.graphics.Color.rgb(240, 240, 245))
    val pill = rgb(android.graphics.Color.rgb(44, 44, 54))
    val micGrey = rgb(android.graphics.Color.rgb(84, 84, 94))
    val claudeOrange = rgb(android.graphics.Color.rgb(204, 120, 92))
    val alertRed = rgb(android.graphics.Color.rgb(214, 69, 65))
    val green = rgb(android.graphics.Color.rgb(46, 125, 90))
    val locked = state.status == AiwaState.Status.WORKING
    val context = LocalContext.current
    val size = LocalSize.current
    val avail = size.width.value - 20f
    val tab = aiwaTab(state)
    // Thin: three rows in one cell of the home screen (it can be pulled taller); the mode buttons are thinner still
    val rowH = ((size.height.value - 8f) / 3f).coerceIn(18f, 40f)
    val chipH = (rowH - 2f).coerceAtLeast(18f)
    val modeH = (chipH * 0.8f).coerceAtLeast(16f)
    val blue = rgb(android.graphics.Color.rgb(58, 110, 205))
    val loopOn = state.loopPair.isNotEmpty()
    val allMode = Projects.isAll(context)
    val fs = context.resources.configuration.fontScale
    val agentOn = state.agent?.enabled == true
    val agentNeeds = state.agent?.let { it.say != null || it.proposal != null || it.command != null } == true
    val asking = state.ask != null       // a question of Claude waits: its button is at the bottom right (row 3)
    // Until Aiwa has been allowed to start its backend (once), the robot is the red « Autoriser Aiwa » button
    val needsSetup = !hasTermuxPermission(context)

    // Three rows: (1) Wallet · Store · settings · loop · project (blue), (2) campaign · docs · video · page · app, (3) ear · + · robot · Claude · question · update (the last two, side by side at the right, only when there is one)
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(ImageProvider(if (state.waiting) R.drawable.widget_bg_alert else R.drawable.widget_bg))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        // ---- the first third: the tabs (Domaine · Files · Build) and, only when there is one, the question of Claude and the update; the two thirds below are the buttons of the chosen tab ----
        val tabNow = WidgetTab.get(context)
        val permanentOn = state.listening && !needsSetup
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            // a tab whose button needs something is red, one whose button works is green (a dot), so that nothing hides behind a tab
            val domainLabel = "Domaine" + (if (loopOn) " ●" else "")
            val buildBusy = agentOn || permanentOn
            val buildLabel = "Build" + (if (buildBusy) " ●" else "")
            val domainColor = if (tabNow == "domain") blue else if (loopOn) green else pill
            val filesColor = if (tabNow == "files") blue else pill
            val buildColor = if (tabNow == "build") blue else if (state.waiting || state.silenceMinutes > 0 || agentNeeds || needsSetup) alertRed else if (buildBusy) green else pill
            Chip(domainLabel, domainColor, fg, actionRunCallback<PickTabCallback>(actionParametersOf(TAB_KEY to "domain")), GlanceModifier.defaultWeight(), bold = tabNow == "domain", height = chipH.dp, textSize = 11.sp, hPad = 4.dp)
            Spacer(GlanceModifier.width(GAP.dp))
            Chip("Files", filesColor, fg, actionRunCallback<PickTabCallback>(actionParametersOf(TAB_KEY to "files")), GlanceModifier.defaultWeight(), bold = tabNow == "files", height = chipH.dp, textSize = 11.sp, hPad = 4.dp)
            Spacer(GlanceModifier.width(GAP.dp))
            Chip(buildLabel, buildColor, fg, actionRunCallback<PickTabCallback>(actionParametersOf(TAB_KEY to "build")), GlanceModifier.defaultWeight(), bold = tabNow == "build", height = chipH.dp, textSize = 11.sp, hPad = 4.dp)
            // always in view, whatever the tab: the question of Claude (red, a tap answers it) and the update (only while a newer build is downloaded: a tap opens the installer)
            if (asking) {
                Spacer(GlanceModifier.width(GAP.dp))
                Chip("Question ▸", alertRed, fg, actionStartActivity<AskActivity>(), GlanceModifier.width(76.dp), height = chipH.dp, textSize = 11.sp, hPad = 4.dp)
            }
            if (state.appUpdateReady) {
                Spacer(GlanceModifier.width(GAP.dp))
                RoundButton(icon = R.drawable.ic_update, description = "Mise à jour d'Aiwa prête : installer", background = green, action = actionStartActivity<InstallUpdateActivity>(), diameter = chipH.dp)
            }
        }
        when (tabNow) {
            "domain" -> {
                // the project (blue; red when the session or the repository is missing or needs something), then the loops, the settings and the wallet
                Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    val projectBad = !allMode && (state.repo == null || bandInfo(state, 200f).title.endsWith("▸"))
                    Chip(
                        if (allMode) "Tous" else fitLabel(Projects.nameOf(context, state.repo, state.cloudSessionId) ?: "Projet", avail - 30f, fs) + " ▾",
                        if (allMode) green else if (locked) PILL_DIM else if (projectBad) alertRed else blue, if (locked) TEXT_DIM else fg,
                        lockable(locked, actionStartActivity<ProjectPickerActivity>()),
                        GlanceModifier.defaultWeight(), height = chipH.dp, textSize = 11.sp, hPad = 6.dp,
                    )
                }
                Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    Chip(if (loopOn) "Boucle ${state.loopPair}" else "Boucles", if (loopOn) green else pill, fg, actionStartActivity<LoopsActivity>(), GlanceModifier.defaultWeight(), height = chipH.dp, textSize = 11.sp, hPad = 4.dp)
                    Spacer(GlanceModifier.width(GAP.dp))
                    Chip("Réglages", pill, fg, actionStartActivity<SettingsActivity>(), GlanceModifier.defaultWeight(), height = chipH.dp, textSize = 11.sp, hPad = 4.dp)
                    Spacer(GlanceModifier.width(GAP.dp))
                    Chip("Wallet", pill, fg, actionStartIntent(storePageIntent(context, "wallet", state.repo)), GlanceModifier.defaultWeight(), height = chipH.dp, textSize = 11.sp, hPad = 4.dp)
                }
            }
            "files" -> {
                // the four kinds (grey until the repository holds one, coloured once it does), then the campaign and the store
                Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    lightKinds(state, context).filter { it.mode != "aiwa" }.forEachIndexed { i, kind ->
                        if (i > 0) Spacer(GlanceModifier.width(4.dp))
                        Chip(
                            kind.word, if (kind.found) green else PILL_DIM, if (kind.found) fg else TEXT_DIM,
                            actionRunCallback<PickKindCallback>(actionParametersOf(MODE_KEY to kind.mode, KIND_FOUND_KEY to (if (kind.found) "1" else "0"))),
                            GlanceModifier.defaultWeight(), bold = kind.current, height = modeH.dp, textSize = 10.sp, hPad = 2.dp,
                        )
                    }
                }
                Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    Chip("Campagne", blue, fg, actionStartActivity<ProposalsActivity>(), GlanceModifier.defaultWeight(), height = chipH.dp, textSize = 11.sp, hPad = 4.dp)
                    Spacer(GlanceModifier.width(GAP.dp))
                    Chip("Store", pill, fg, actionStartIntent(storePageIntent(context, "store", state.repo)), GlanceModifier.defaultWeight(), height = chipH.dp, textSize = 11.sp, hPad = 4.dp)
                }
            }
            else -> {
                // the robot (the super agent) and Claude; below, the ear and the +
                Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    // One listening at a time on screen: the permanent listening on hides the robot (the wake word opens the agent, the switch stays to close everything); the robot open alone hides the switch.
                    if (permanentOn) Box(
                        modifier = GlanceModifier.defaultWeight().height((rowH - 2f).dp).background(green).cornerRadius(((rowH - 2f) / 2).dp).clickable(actionStartActivity<ListenToggleActivity>()),
                        contentAlignment = Alignment.Center,
                    ) { Image(provider = ImageProvider(R.drawable.ic_ear), contentDescription = "Écoute permanente : active, toucher pour la couper", modifier = GlanceModifier.size(20.dp)) } else
                    Box(
                        modifier = GlanceModifier.defaultWeight().height((rowH - 2f).dp).background(if (needsSetup || agentNeeds) alertRed else if (agentOn) green else pill).cornerRadius(((rowH - 2f) / 2).dp)
                            .clickable(if (needsSetup) actionStartActivity<SetupActivity>() else actionStartActivity<AgentToggleActivity>()),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (needsSetup) Text("Autoriser Aiwa", style = TextStyle(color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        else Image(provider = ImageProvider(R.drawable.ic_agent), contentDescription = if (agentOn) "Super agent : il écoute, fermer" else "Parler au super agent", modifier = GlanceModifier.size(20.dp))
                    }
                    // Only once there is a conversation to open: with « new session » chosen, the first message (said or typed) makes it, and the button appears
                    if (state.cloudSessionId != null) {
                        Spacer(GlanceModifier.width(GAP.dp))
                        RoundButton(
                            icon = R.drawable.ic_claude,
                            description = if (state.waiting) "Claude attend ta réponse : ouvrir" else "Ouvrir la conversation dans Claude",
                            background = if (state.waiting || state.silenceMinutes > 0) alertRed else claudeOrange,
                            action = actionStartActivity<OpenClaudeActivity>(),
                            diameter = chipH.dp,
                        )
                    }
                }
                Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    // The switch of the permanent listening: green when it listens, grey when it is closed (it is closed at each installation and update). Hidden while the robot is open or while the permanent listening is on (the ear then takes the place of the robot)
                    if (!permanentOn && !agentOn) {
                        RoundButton(
                            icon = R.drawable.ic_ear,
                            description = if (state.listening) "Écoute permanente : active, toucher pour la couper" else "Écoute permanente : coupée, toucher pour l'activer",
                            background = if (state.listening) green else pill, action = actionStartActivity<ListenToggleActivity>(), diameter = chipH.dp,
                        )
                        Spacer(GlanceModifier.width(GAP.dp))
                    }
            RoundButton(icon = R.drawable.ic_plus, description = if (state.attachments > 0) "${state.attachments} pièce(s) jointe(s) en attente : ajouter ou retirer" else "Ajouter : un document ou un média", background = if (state.attachments > 0) green else pill, action = actionStartActivity<PlusMenuActivity>(), diameter = chipH.dp)
                }
            }
        }
    }
}

// The two-row layout (conversation row + project row): a safety net for a
// launcher whose cells are so small that the widget is under 150 dp high.

// The microphone button while the super agent listens: it is grey, and a tap says why.
class AgentMicTapCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        toastOnMain(context, "Le super agent écoute : parle-lui (le micro est grisé tant qu'il est ouvert). « Éteins » ou son bouton le ferme.")
    }
}

// A tap on something locked while a message is on its way: says why, in the widget's own line and in a toast.
class BusyTapCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        tellBusy(context, toast = true)
    }
}




/** A page of the web app alone (page=wallet or page=store): the Store's activity, with no tabs and no browser bar. The repository goes with it (the Wallet's Push publishes its page). */
fun storePageIntent(context: Context, page: String, repo: String?): Intent =
    Intent(context, StoreActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .putExtra(StoreActivity.EXTRA_URL, "$STORE_APP_URL?page=$page" + (if (repo != null) "&repo=" + android.net.Uri.encode(repo) else ""))
