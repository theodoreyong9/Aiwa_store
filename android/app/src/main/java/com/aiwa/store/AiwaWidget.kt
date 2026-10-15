package com.aiwa.store
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
    // Two shapes: Light (the default) and Glasses (the same buttons, in a pair of glasses with the Store on the right lens). Chosen in the settings.
    if (WidgetStyle.current(LocalContext.current) == WidgetStyle.GLASSES) GlassesContent(state) else LightContent(state)
}

// What the top of the widget says (the session and what is going on) and what a tap on it opens: the fix for what is wrong, else the sessions. Shared by both shapes.
private class BandInfo(val title: String, val statusText: String, val statusColor: ColorProvider, val statusBold: Boolean, val action: Action)

// The four rows have a name, top to bottom: Activité, Domaine, Agence, Spécialiste. The name is in a small column of its own at the left of the row, NOT in the chips.
private const val ROW_NAME_W = 56f
private const val ROW_ACTIVITY = "Activité"
private const val ROW_DOMAIN = "Domaine"
private const val ROW_AGENCY = "Agence"
private const val ROW_SPECIALIST = "Spécialiste"


@Composable
private fun bandInfo(state: AiwaState, titleRoom: Float): BandInfo {
    val fg = rgb(android.graphics.Color.rgb(240, 240, 245))
    val subtle = rgb(android.graphics.Color.rgb(160, 160, 174))
    val warm = rgb(android.graphics.Color.rgb(232, 150, 124))
    val alertText = rgb(android.graphics.Color.rgb(255, 138, 133))
    val locked = state.status == AiwaState.Status.WORKING
    val fontScale = LocalContext.current.resources.configuration.fontScale
    val needsSetup = !hasTermuxPermission(LocalContext.current)
    val needsLogin = state.claudeLogin == "needed"
    val storeMode = deliversApp(state.deploy)
    val storeReady = storeMode && state.sentApp?.seen == false
    val status: String
    val statusColor: ColorProvider
    var statusBold = false
    var batteryLine = false
    when {
        needsSetup -> { status = "Touche ici pour autoriser Aiwa (une seule fois)"; statusColor = alertText; statusBold = true }
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
        needsSetup -> "Autoriser Aiwa ▸"
        accessNotice(state, 1) != null -> "Accès réservé ▸"
        state.backend == "missing" -> "Installer le backend ▸"
        needsLogin -> "Connecter Claude ▸"
        else -> fitLabel(state.session, titleRoom - textWidth(" ▾", fontScale * 1.25f), fontScale * 1.25f) + " ▾"
    }
    val statusText = fitLabel(status, titleRoom, fontScale * 0.92f)
    // What a tap on the name and the status line opens: the fix for what is wrong, else the sessions.
    val bandAction: Action = when {
        batteryLine -> actionStartActivity<BatteryActivity>()
        needsSetup -> actionStartActivity<SetupActivity>()
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


/**
 * The other shape of the widget: a pair of glasses. A drawn frame (two rounded lenses, the bridge, the start of the temples: glasses_frame) stretched over the card, and the
 * things placed INSIDE the lenses. Left lens: the context (the session and what is going on, the repository, the settings, the mode, Claude). Right lens: the application (the
 * logo, which opens it) and the microphone side by side, then what the work gives (the site or the proposals, ▦ and `</>`, the videos, the GitHub guide). Only what exists is shown.
 * Chosen in the settings list (WidgetStyle).
 */

// ================================================================================================
// The third shape, « Aiwa »: the modes in a row at the top (Média · Page · App · Store · Aiwa; Média holds Vidéo, Campagne and Documents); what the chosen mode needs; the mic, Claude, the question,
// the model, the push and the state of the workflow; and a bottom row (loop, the Aiwa app, the update when there is one, the settings). Session and repository
// are in the list of the « contexte » button. A tap on a mode IS the deploy selector (Média and Vidéo only change what is shown).
// ================================================================================================

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
private class LightKind(val mode: String, val word: String, val found: Boolean, val current: Boolean)

private fun lightKinds(state: AiwaState, context: Context): List<LightKind> {
    val hasSite = state.repo != null && state.siteUrl != null
    val tab = aiwaTab(state)
    val sub = mediaSub(state)
    return listOf(
        LightKind("documents", "Docs", Productions.has(context, state.repo, "documents"), tab == "media" && sub == "documents"),
        LightKind("video", "Vidéo", Productions.has(context, state.repo, "video"), tab == "media" && sub == "video"),
        LightKind("pages", "Page", Productions.has(context, state.repo, "page") || hasSite && state.siteKind != "apk" && state.siteState == "live", tab == "pages"),
        LightKind("android", "App", Productions.has(context, state.repo, "apk") || hasSite && state.siteKind == "apk", tab == "android"),
        LightKind("aiwa", "Aiwa", Productions.has(context, state.repo, "aiwa"), tab == "aiwa"),
    )
}

private val KIND_FOUND_KEY = ActionParameters.Key<String>("aiwa-kind-found")

/** A tap on one of the five: the mode is chosen (with its own session) and, when something was produced, it opens. */
class PickKindCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val mode = parameters[MODE_KEY] ?: return
        val found = parameters[KIND_FOUND_KEY] == "1"
        val app = context.applicationContext
        val state = AiwaRepository.state.value
        selectAiwaMode(app, mode)
        Productions.refresh(app, state.repo, force = true)
        // Vidéo and Docs open even when grey (the window to ask for one; the folder or a word that there is none yet); the others only when something was produced
        if (!found && mode != "video" && mode != "documents") return
        val repo = state.repo
        val view = { url: String -> app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        when (mode) {
            "documents" -> app.startActivity(Intent(app, DocumentsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "video" -> app.startActivity(Intent(app, VideosActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "aiwa" -> app.startActivity(Intent(app, OpenStoreActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "pages" -> if (repo != null) view("https://${repo.substringBefore('/')}.github.io/${repo.substringAfter('/')}/")
            else -> if (repo != null) view("https://github.com/$repo/releases")
        }
    }
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
    val rowH = ((size.height.value - 16f - 24f) / 3f).coerceIn(26f, 46f)
    val chipH = (rowH - 4f).coerceAtLeast(24f)
    val loopOn = state.loopPair.isNotEmpty()
    val agent = state.agent
    val bottomCount = 5
    val bottomD = minOf(chipH, avail / bottomCount - 4f).coerceAtLeast(20f)

    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(ImageProvider(if (state.waiting) R.drawable.widget_bg_alert else R.drawable.widget_bg))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        // ---- the session and the repository, ----
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            val fs = context.resources.configuration.fontScale
            val rounds = 0
            // the room each of the two pills really has: the row, less the round buttons and the gaps, shared by two
            val each = (avail - rounds * (chipH + GAP) - GAP) / 2f - 12f
            val sessionTitle = bandInfo(state, each).title
            val sessionBad = sessionTitle.endsWith("▸")
            Chip(
                if (sessionBad) sessionTitle else fitLabel(state.session, each - textWidth(" ▾", fs), fs) + " ▾",
                if (locked) PILL_DIM else if (sessionBad) alertRed else pill, if (locked) TEXT_DIM else fg,
                if (sessionBad) bandInfo(state, each).action else lockable(locked, actionStartActivity<SessionPickerActivity>()),
                GlanceModifier.defaultWeight(), alignStart = true, height = chipH.dp, textSize = 11.sp, hPad = 6.dp,
            )
            Spacer(GlanceModifier.width(GAP.dp))
            // the spinner sits on the repository pill while a workflow is awaited; a tap on it goes to the workflow, a tap on the pill opens the list
            val pending = workflowPending(state)
            Box(modifier = GlanceModifier.defaultWeight().height(chipH.dp), contentAlignment = Alignment.CenterEnd) {
                Chip(
                    "⎇ " + fitLabel(state.repo?.substringAfter('/') ?: "Dépôt", each - textWidth("⎇  ▾", fs) - (if (pending) chipH - 8f else 0f), fs) + " ▾",
                    if (locked) PILL_DIM else if (state.repo == null) alertRed else pill, if (locked) TEXT_DIM else fg,
                    lockable(locked, actionStartActivity<RepoPickerActivity>()), GlanceModifier.fillMaxSize(), alignStart = true, height = chipH.dp, textSize = 11.sp, hPad = 6.dp,
                )
                if (pending) Box(modifier = GlanceModifier.padding(end = 3.dp)) { WorkflowSpinner((chipH - 6f).dp) }
            }
        }
        // ---- the five kinds: grey until the repository holds one, coloured once it does ----
        Row(modifier = GlanceModifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            lightKinds(state, context).forEachIndexed { i, kind ->
                if (i > 0) Spacer(GlanceModifier.width(4.dp))
                Chip(
                    kind.word, if (kind.found) green else PILL_DIM, if (kind.found) fg else TEXT_DIM,
                    actionRunCallback<PickKindCallback>(actionParametersOf(MODE_KEY to kind.mode, KIND_FOUND_KEY to (if (kind.found) "1" else "0"))),
                    GlanceModifier.defaultWeight(), bold = kind.current, height = 22.dp, textSize = 10.sp, hPad = 2.dp,
                )
            }
        }
        // ---- the microphone, Claude, the question, the update ----
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            val agentOn = state.agent?.enabled == true
            Box(
                modifier = GlanceModifier.defaultWeight().height((rowH - 2f).dp).background(if (locked || agentOn) MIC_DIM else micGrey).cornerRadius(((rowH - 2f) / 2).dp)
                    .clickable(if (agentOn) actionRunCallback<AgentMicTapCallback>() else lockable(locked, micAction(state.needsRepo, state.backend == "missing", accessNotice(state, 1) != null))),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(provider = ImageProvider(R.drawable.rec_dot), contentDescription = "Dicter un message", modifier = GlanceModifier.size(16.dp))
                    Spacer(GlanceModifier.width(8.dp))
                    Text("Dicter un message", style = TextStyle(color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.width(GAP.dp))
            RoundButton(
                icon = R.drawable.ic_claude,
                description = if (state.waiting) "Claude attend ta réponse : ouvrir" else "Ouvrir la conversation dans Claude",
                background = if (state.waiting || state.silenceMinutes > 0) alertRed else claudeOrange,
                action = actionStartActivity<OpenClaudeActivity>(),
                diameter = chipH.dp,
            )
            if (state.ask != null) {
                Spacer(GlanceModifier.width(GAP.dp))
                RoundButton(icon = R.drawable.ic_question, description = "Claude te pose une question : répondre", background = alertRed, action = actionStartActivity<AskActivity>(), diameter = chipH.dp)
            }
            // only while a newer build is downloaded (a tap opens the installer)
            if (state.appUpdateReady) {
                Spacer(GlanceModifier.width(GAP.dp))
                RoundButton(icon = R.drawable.ic_update, description = "Mise à jour d'Aiwa prête : installer", background = green, action = actionStartActivity<InstallUpdateActivity>(), diameter = chipH.dp)
            }
        }
        // ---- the loop, the Aiwa app, the super agent, the comment, the settings: five equal slots ----
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            @Composable fun Slot(content: @Composable () -> Unit) {
                Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) { content() }
            }
            Slot {
                RoundButton(
                    icon = R.drawable.ic_loop,
                    description = if (loopOn) "Boucle ${state.loopPair} en cours : voir, arrêter ou revenir en arrière" else "Boucles builder / critique",
                    background = if (loopOn) green else pill, action = actionStartActivity<LoopsActivity>(), diameter = bottomD.dp,
                )
            }
            Slot { RoundButton(icon = R.drawable.ic_aiwa, description = "L'application Aiwa", background = pill, action = actionStartActivity<StoreActivity>(), diameter = bottomD.dp) }
            Slot {
                RoundButton(
                    icon = R.drawable.ic_agent,
                    description = "Super agent : ouvrir ou fermer (l'écoute de réveil attend pendant ce temps)",
                    background = if (agent != null && (agent.say != null || agent.proposal != null || agent.command != null)) alertRed else if (agent?.enabled == true) green else pill,
                    action = actionStartActivity<AgentToggleActivity>(), diameter = bottomD.dp,
                )
            }
            Slot { RoundButton(icon = R.drawable.ic_proposals, description = "Campagne : les propositions à valider", background = pill, action = actionStartActivity<ProposalsActivity>(), diameter = bottomD.dp) }
            Slot {
                RoundButton(
                    icon = R.drawable.ic_settings,
                    description = "Réglages : session, dépôt, modèle, push, voix, commentaire, mises à jour…",
                    background = pill, action = actionStartActivity<SettingsActivity>(), diameter = bottomD.dp,
                )
            }
        }
    }
}

// The two-row layout (conversation row + project row): a safety net for a
// launcher whose cells are so small that the widget is under 150 dp high.

@Composable
private fun GlassesContent(state: AiwaState) {
    val fg = rgb(android.graphics.Color.rgb(240, 240, 245))
    val pill = rgb(android.graphics.Color.rgb(52, 52, 64))
    val micGrey = rgb(android.graphics.Color.rgb(84, 84, 94))
    val alertRed = rgb(android.graphics.Color.rgb(214, 69, 65))
    val green = rgb(android.graphics.Color.rgb(46, 125, 90))
    val locked = state.status == AiwaState.Status.WORKING
    val size = LocalSize.current
    val context = LocalContext.current
    val fontScale = context.resources.configuration.fontScale

    // The frame is drawn on a 200 x 100 grid stretched to the widget: the lenses are 8..96 and 104..192 wide and 10..90 high, the things go inside them.
    val w = size.width.value
    val h = size.height.value
    val outer = w * 0.085f
    val inner = w * 0.355f
    val middle = w - 2 * outer - 2 * inner
    val top = h * 0.17f
    val micH = (h * 0.20f).coerceIn(20f, 28f)
    // the left lens: a column of six small buttons (the five kinds and the settings) and, under them, the dictation row
    val itemH = ((h * 0.83f - 26f - micH) / 7f).coerceIn(9f, 13f)
    val colW = 40f
    val band = bandInfo(state, inner - colW - 24f)
    val agentOn = state.agent?.enabled == true
    val micAct = if (agentOn) actionRunCallback<AgentMicTapCallback>() else lockable(locked, micAction(state.needsRepo, state.backend == "missing", accessNotice(state, 1) != null))
    fun storeTab(tab: String): Action = actionStartIntent(
        Intent(context, StoreActivity::class.java).setData(Uri.parse("aiwa://store/$tab"))
            .putExtra(StoreActivity.EXTRA_URL, storeTabUrl(tab) ?: STORE_APP_URL),
    )

    // No card behind: the widget IS the glasses, and the home screen shows between and around the lenses. Red rims while Claude waits.
    Box(modifier = GlanceModifier.fillMaxSize()) {
        Box(modifier = GlanceModifier.fillMaxSize().background(ImageProvider(if (state.waiting) R.drawable.glasses_frame_alert else R.drawable.glasses_frame))) {
            Row(modifier = GlanceModifier.fillMaxSize().padding(top = top.dp, bottom = (h * 0.07f).dp)) {
                Spacer(GlanceModifier.width(outer.dp))
                // ---- the left lens: the buttons of the Light widget ----
                Column(modifier = GlanceModifier.width(inner.dp).fillMaxHeight().padding(start = 10.dp, end = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
                    Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                        // the column: Docs, Vidéo, Page, App, Aiwa (grey until the repository holds one), then the settings, which hold everything else
                        Column(modifier = GlanceModifier.width(colW.dp).fillMaxHeight(), verticalAlignment = Alignment.Top) {
                            lightKinds(state, context).forEach { kind ->
                                Chip(
                                    kind.word, if (kind.found) green else PILL_DIM, if (kind.found) fg else TEXT_DIM,
                                    actionRunCallback<PickKindCallback>(actionParametersOf(MODE_KEY to kind.mode, KIND_FOUND_KEY to (if (kind.found) "1" else "0"))),
                                    GlanceModifier.fillMaxWidth(), bold = kind.current, height = itemH.dp, textSize = 7.sp, hPad = 1.dp,
                                )
                            }
                            // above the settings, one row: the workflow (a spinner while one is awaited, red when the last run failed), the loops, the campaign
                            Row(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                val d = minOf(itemH + 1f, 12f).dp
                                if (workflowPending(state)) WorkflowSpinner(d)
                                else RoundButton(icon = R.drawable.ic_actions, description = "Le workflow du dépôt", background = if (workflowFailed(state)) alertRed else pill, action = actionStartActivity<OpenActionsActivity>(), diameter = d)
                                Spacer(GlanceModifier.width(2.dp))
                                RoundButton(icon = R.drawable.ic_loop, description = "Boucles builder / critique", background = if (state.loopPair.isNotEmpty()) green else pill, action = actionStartActivity<LoopsActivity>(), diameter = d)
                                Spacer(GlanceModifier.width(2.dp))
                                RoundButton(icon = R.drawable.ic_proposals, description = "Campagne : les propositions à valider", background = pill, action = actionStartActivity<ProposalsActivity>(), diameter = d)
                            }
                            RoundButton(
                                icon = R.drawable.ic_settings,
                                description = "Réglages",
                                background = pill, action = actionStartActivity<SettingsActivity>(), diameter = (itemH + 1f).dp,
                            )
                        }
                        Spacer(GlanceModifier.width(4.dp))
                        // beside it: only what is going on, when it is not « Prêt » (nothing to tap here)
                        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight(), verticalAlignment = Alignment.Top) {
                            if (band.statusText != "Prêt") Text(band.statusText, style = TextStyle(color = band.statusColor, fontSize = 8.sp, fontWeight = if (band.statusBold) FontWeight.Bold else FontWeight.Normal), maxLines = 3)
                        }
                    }
                    // the bottom: dictate, the question when Claude asks one, the update when one is ready
                    Row(modifier = GlanceModifier.fillMaxWidth().height(micH.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = GlanceModifier.defaultWeight().height(micH.dp).background(if (locked || agentOn) MIC_DIM else micGrey).cornerRadius((micH / 2).dp).clickable(micAct),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(provider = ImageProvider(R.drawable.rec_dot), contentDescription = "Dicter un message", modifier = GlanceModifier.size(14.dp))
                                Spacer(GlanceModifier.width(5.dp))
                                Text("Dicter", style = TextStyle(color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                            }
                        }
                        if (state.ask != null) {
                            Spacer(GlanceModifier.width(GAP.dp))
                            RoundButton(icon = R.drawable.ic_question, description = "Claude te pose une question : répondre", background = alertRed, action = actionStartActivity<AskActivity>(), diameter = (micH - 2f).dp)
                        }
                        if (state.appUpdateReady) {
                            Spacer(GlanceModifier.width(GAP.dp))
                            RoundButton(icon = R.drawable.ic_update, description = "Mise à jour d'Aiwa prête : installer", background = green, action = actionStartActivity<InstallUpdateActivity>(), diameter = (micH - 2f).dp)
                        }
                    }
                }
                // ---- the bridge: nothing on it, it is the frame ----
                Spacer(GlanceModifier.width(middle.dp))
                // ---- the right lens: Store and Wallet at the top, a silhouette in the middle, own, fave and inviter at the bottom ----
                Column(modifier = GlanceModifier.width(inner.dp).fillMaxHeight().padding(start = 10.dp, end = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.Top, horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(modifier = GlanceModifier.fillMaxWidth().height((itemH + 1f).dp)) {
                        Chip("Store", pill, fg, actionStartActivity<StoreActivity>(), GlanceModifier.defaultWeight(), height = (itemH + 1f).dp, textSize = 7.sp, hPad = 1.dp)
                        Spacer(GlanceModifier.width(GAP.dp))
                        Chip("Wallet", pill, fg, storeTab("wallet"), GlanceModifier.defaultWeight(), height = (itemH + 1f).dp, textSize = 7.sp, hPad = 1.dp)
                    }
                    // the silhouette: a person; a tap looks for an id or an account (a widget cannot hold a text field, the real one opens)
                    Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
                        // a real button (a round one like the others): a tap on a bare picture did nothing
                        val d = (h * 0.24f).coerceIn(20f, 32f)
                        Box(
                            modifier = GlanceModifier.size(d.dp).background(pill).cornerRadius((d / 2).dp).clickable(actionStartActivity<SearchActivity>()),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(provider = ImageProvider(R.drawable.ic_silhouette), contentDescription = "Chercher un identifiant ou un compte", modifier = GlanceModifier.size((d * 0.7f).dp))
                        }
                    }
                    Row(modifier = GlanceModifier.fillMaxWidth().height((itemH + 1f).dp)) {
                        Chip("own", pill, fg, storeTab("mine"), GlanceModifier.defaultWeight(), height = (itemH + 1f).dp, textSize = 7.sp, hPad = 1.dp)
                        Spacer(GlanceModifier.width(3.dp))
                        Chip("fave", pill, fg, storeTab("favorites"), GlanceModifier.defaultWeight(), height = (itemH + 1f).dp, textSize = 7.sp, hPad = 1.dp)
                        Spacer(GlanceModifier.width(3.dp))
                        Chip("inviter", pill, fg, actionStartActivity<InviteActivity>(), GlanceModifier.defaultWeight(), height = (itemH + 1f).dp, textSize = 7.sp, hPad = 1.dp)
                    }
                }
                Spacer(GlanceModifier.width(outer.dp))
            }
        }
    }
}

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


