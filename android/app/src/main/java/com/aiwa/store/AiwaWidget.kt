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

// A chip is its text plus 10 dp of padding on each side.
private fun chipWidth(text: String, fontScale: Float): Float = 20f + textWidth(text, fontScale)

private fun fitLabel(text: String, room: Float, fontScale: Float): String {
    if (textWidth(text, fontScale) <= room) return text
    var out = text
    while (out.isNotEmpty() && textWidth("$out…", fontScale) > room) out = out.dropLast(1)
    return if (out.isEmpty()) "…" else "$out…"
}

/** The settings pill, in a few words: model, push, and how the last workflow went (✓ ✗ …); a microphone first while the always-on listening is on. */
private fun settingsSummary(state: AiwaState): String {
    val ci = if (!workflowStateKnown(state)) "" else when (state.ciState) { "success" -> " ✓"; "failure" -> " ✗"; "running" -> " …"; else -> "" }
    return (if (state.listening) "🎙 " else "") + modelLabel(state.model) + (if (state.repo != null) " · " + (if (state.pushMain) "main" else "branche") else "") + ci
}

// While a message is on its way the settings and the mic are locked and dimmed (they were still tappable, which was pointless and
// confusing): a tap says why. What only looks (the site, the app, the code, Actions, Claude) stays alive.
private fun lockable(locked: Boolean, action: Action): Action = if (locked) actionRunCallback<BusyTapCallback>() else action
private val PILL_DIM = rgb(android.graphics.Color.rgb(34, 34, 42))
private val GREEN_DIM = rgb(android.graphics.Color.rgb(34, 66, 52))
private val MIC_DIM = rgb(android.graphics.Color.rgb(58, 58, 66))
private val TEXT_DIM = rgb(android.graphics.Color.rgb(130, 130, 144))

// The globe / the download: the site of the repository. What it opens depends on the state at the moment of the tap (SiteButtonActivity), and a tap
// is also looking at the news the red dot was about.
private val siteAction: Action get() = actionStartActivity<SiteButtonActivity>()

// The code of the app, as it was PUBLISHED (the version the registry lists, checked again by the Store before it is shown). The draft, before
// publishing, is read in the publish sheet (the Store button). OpenCodeActivity opens the Store on it.
private val codeAction: Action get() = actionStartActivity<OpenCodeActivity>()

// The mic records; with no repository chosen there is nothing to send to yet, so it opens the repository list instead.
// The backend is not installed (in Termux): the voice cannot be sent anywhere. The writing screen opens instead, with the steps to install it.
private fun micAction(needsRepo: Boolean, backendMissing: Boolean, noAccess: Boolean = false): Action = when {
    noAccess -> actionStartActivity<AccessActivity>()
    backendMissing -> actionStartActivity<MainActivity>()
    needsRepo -> actionStartActivity<RepoPickerActivity>()
    else -> actionStartActivity<DictateActivity>()
}

// The Deploy chip opens a list of what Claude is asked to produce (DeployPickerActivity).
private fun deployLabel(mode: String) = when (mode) {
    "pages" -> "Pages ▾"
    "android" -> "Android ▾"
    "store" -> "Store ▾"
    "aiwa" -> "Aiwa ▾"
    "campaign" -> "Campagne ▾"
    "documents" -> "Documents ▾"
    else -> "Deploy ▾"
}

// GitHub Actions only matter when the work is published from GitHub.
private fun publishesFromGithub(deploy: String) = deploy == "pages" || deploy == "android" || deploy == "campaign"


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

@Composable
private fun Content(state: AiwaState) {
    // The widget has ONE shape (not resizable, see dictation_widget_info.xml): four bands
    // that share the whole height, so there is never an empty strip. The compact
    // two-row layout below is only a safety net for a launcher whose cells are so
    // small that four bands would not fit.
    val shape = WidgetStyle.current(LocalContext.current)
    val glasses = shape == WidgetStyle.GLASSES && LocalSize.current.height >= 100.dp
    if (glasses) GlassesContent(state)
    else if (shape == WidgetStyle.LIGHT && LocalSize.current.height >= 100.dp) LightContent(state)
    else if (shape == WidgetStyle.AIWA && LocalSize.current.height >= 150.dp) AiwaContent(state)
    else if (LocalSize.current.height >= 150.dp) FullContent(state)
    else CompactContent(state)
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
private fun RowName(text: String) {
    Text(
        text,
        style = TextStyle(color = rgb(android.graphics.Color.rgb(150, 150, 166)), fontSize = 9.sp),
        maxLines = 1,
        modifier = GlanceModifier.width(ROW_NAME_W.dp),
    )
}

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

@Composable
private fun FullContent(state: AiwaState) {
    val fg = rgb(android.graphics.Color.rgb(240, 240, 245))
    val subtle = rgb(android.graphics.Color.rgb(160, 160, 174))
    val warm = rgb(android.graphics.Color.rgb(232, 150, 124))
    val alertText = rgb(android.graphics.Color.rgb(255, 138, 133))
    val pill = rgb(android.graphics.Color.rgb(44, 44, 54))
    val micGrey = rgb(android.graphics.Color.rgb(84, 84, 94))
    val claudeOrange = rgb(android.graphics.Color.rgb(204, 120, 92))
    val alertRed = rgb(android.graphics.Color.rgb(214, 69, 65))
    val green = rgb(android.graphics.Color.rgb(46, 125, 90))
    val locked = state.status == AiwaState.Status.WORKING
    val size = LocalSize.current
    val fontScale = LocalContext.current.resources.configuration.fontScale
    // The card's own padding takes 10 dp on each side and 8 dp above and below.
    val avail = size.width.value - 20f
    val hasSession = state.cloudSessionId != null || state.lastSessionId != null
    val hasRepo = state.repo != null

    // The bands share the height equally; the buttons in them grow with the band
    // up to a comfortable size, so a taller widget gets roomier, not emptier.
    val bands = 4     // the project type is chosen before any repository: the Store, Aiwa and campaign modes need none
    val band = (size.height.value - 16f) / bands
    val chipH = (band - 6f).coerceIn(30f, 44f)
    val micH = (band - 4f).coerceIn(34f, 48f)
    val avatar = (band - 2f).coerceIn(34f, 44f)

    // ---- band 1: which conversation, and what is going on ------------------
    // The backend's own state comes first: with it down or starting, nothing else
    // on the widget can be trusted to work, and a widget that just sits there looks
    // broken. While a message is on its way (creating a cloud session takes a few
    // seconds) it says so; while Claude waits for an answer (it pinged the relay)
    // it says that in red, the Claude button turns red and the card gets a red
    // edge: the alert is the widget itself, not a notification.
    // Before anything else: Aiwa may not start the backend yet (its permission is
    // asked once, by the set-up window the widget opens).
    val needsSetup = !hasTermuxPermission(LocalContext.current)
    // The CLI is not logged in to a Claude account: nothing can be sent until it is.
    val needsLogin = state.claudeLogin == "needed"
    // The "Store" and "Aiwa" modes end with an app Claude sends to the phone: when it has come and was not opened yet, it is waiting.
    val storeMode = deliversApp(state.deploy)
    val storeReady = storeMode && state.sentApp?.seen == false
    val band1 = bandInfo(state, avail - ROW_NAME_W - 2 * avatar - 16f)
    val title = band1.title
    val statusText = band1.statusText
    val statusColor = band1.statusColor
    val statusBold = band1.statusBold
    val bandAction = band1.action

    // ---- band 2: repository and model, half the width each -----------------
    // The push chip sits at the end of this band, as short as it can be ("main" / "branche"); the repository and the model share the rest.
    val pushText = if (state.pushMain) "main" else "branche"
    val half = (avail - ROW_NAME_W - GAP) / 2f - 20f
    val repoName = state.repo?.substringAfter('/')
    // "+2": two more repositories Claude may work on (see the repository picker).
    val extraSuffix = if (state.extraRepos.isNotEmpty()) " +${state.extraRepos.size}" else ""
    val repoText = "⎇ " + fitLabel(repoName ?: "Choisir un dépôt", half - textWidth("⎇  ▾", fontScale) - textWidth(extraSuffix, fontScale), fontScale) + extraSuffix + " ▾"
    // Model, effort, push, the state of the workflow and the menu of Aiwa are ONE list: this pill says them in a few words and opens it.
    val settingsText = fitLabel(settingsSummary(state), half - textWidth(" ▾", fontScale), fontScale) + " ▾"

    // ---- band 3: Push, Deploy, and the site / Actions ---------------------
    val site = state.siteUrl
    val live = state.siteState == "live"
    // The address is known as soon as a repository is chosen
    // (https://<owner>.github.io/<repo>/): the globe is there once deployment is
    // asked for, or as soon as the address answers.
    val campaignMode = state.deploy == "campaign"
    val showSite = hasRepo && site != null && (state.deploy != "none" || live) && !campaignMode
    val showProposals = campaignMode      // in the campaign mode the site button gives way to « Propositions et gestion »
    val showStore = storeMode
    // In the Store mode the repository carries a small workflow that checks the app (aiwa-apps/*.app.html); the round "GitHub Actions" button
    // follows it, as it follows the build in the Android mode, next to the button that shows the code Claude sent.
    val showCode = showStore
    val showActions = false      // the state of the workflow is in the settings pill and its list
    val fixed = (if (showSite || showProposals) GAP + chipH else 0f) + (if (showStore) GAP + chipH else 0f) + (if (showCode) GAP + chipH else 0f) + (if (showActions) GAP + chipH else 0f) + (if (hasRepo) GAP + chipH else 0f)
    val each = avail - ROW_NAME_W - fixed - 20f
    val deployText = fitLabel(deployLabel(state.deploy), each, fontScale)

    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(ImageProvider(if (state.waiting) R.drawable.widget_bg_alert else R.drawable.widget_bg))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            RowName(ROW_ACTIVITY)
            // The loop between two sessions (builder and critic), at the left of the list of sessions: green while one is running
            RoundButton(
                icon = R.drawable.ic_loop,
                description = if (state.loopPair.isNotEmpty()) "Boucle ${state.loopPair} en cours : voir, arrêter ou revenir en arrière" else "Boucles builder / critique",
                background = if (state.loopPair.isNotEmpty()) green else pill,
                action = actionStartActivity<LoopsActivity>(),
                diameter = avatar.dp,
            )
            Spacer(GlanceModifier.width(8.dp))
            Column(
                modifier = GlanceModifier.defaultWeight().clickable(bandAction),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = TextStyle(color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Text(
                    statusText,
                    style = TextStyle(color = statusColor, fontSize = 11.sp, fontWeight = if (statusBold) FontWeight.Bold else FontWeight.Normal),
                    maxLines = 1,
                )
            }
            // The question Claude asks: a button of its own, at the right of the session, so that the session's name keeps opening the list of sessions
            if (state.ask != null) {
                Spacer(GlanceModifier.width(8.dp))
                RoundButton(icon = R.drawable.ic_question, description = "Claude te pose une question : répondre", background = alertRed, action = actionStartActivity<AskActivity>(), diameter = avatar.dp)
            }
            // The logo (a round PNG with real transparency), on the right: opens the app, the Store.
            Spacer(GlanceModifier.width(8.dp))
            Image(
                provider = ImageProvider(R.drawable.logo_round),
                contentDescription = "Ouvrir Aiwa",
                modifier = GlanceModifier.size(avatar.dp).clickable(actionStartActivity<StoreActivity>()),
            )
        }
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            RowName(ROW_DOMAIN)
            Chip(repoText, if (locked) PILL_DIM else pill, if (locked) TEXT_DIM else fg, lockable(locked, actionStartActivity<RepoPickerActivity>()), GlanceModifier.defaultWeight(), alignStart = true, height = chipH.dp, badge = state.repo == null)
            Spacer(GlanceModifier.width(GAP.dp))
            Chip(settingsText, if (locked) PILL_DIM else if (workflowFailed(state)) alertRed else pill, if (locked) TEXT_DIM else fg, lockable(locked, actionStartActivity<SettingsActivity>()), GlanceModifier.defaultWeight(), alignStart = true, height = chipH.dp)
        }
        // The buttons of this band appear once a repository is chosen. They are
        // instructions integrated into the conversation — Claude Code does the
        // work itself.
        run {
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                RowName(ROW_AGENCY)
                if (hasRepo && state.ciHintTitle != null) {
                    // A setting of GitHub to change by hand: its own button (the guide), instead of changing what the other buttons do.
                    RoundButton(
                        icon = R.drawable.ic_warning,
                        description = "Un réglage de GitHub à faire : le guide",
                        background = alertRed,
                        action = actionStartActivity<PagesHelpActivity>(),
                        diameter = chipH.dp,
                    )
                    Spacer(GlanceModifier.width(GAP.dp))
                }
                // The promotional videos of the creation (VideosActivity): list them, or ask Claude for a new one. At the left of the mode chip.
                if (hasRepo) {
                    RoundButton(
                        icon = R.drawable.ic_video,
                        description = "Vidéos promotionnelles de la création",
                        background = pill,
                        action = actionStartActivity<VideosActivity>(),
                        diameter = chipH.dp,
                    )
                    Spacer(GlanceModifier.width(GAP.dp))
                }
                Chip(deployText, if (locked) (if (state.deploy != "none") GREEN_DIM else PILL_DIM) else if (state.deploy != "none") green else pill, if (locked) TEXT_DIM else fg, lockable(locked, actionStartActivity<DeployPickerActivity>()), GlanceModifier.defaultWeight(), alignStart = true, height = chipH.dp)
                if (showProposals) {
                    Spacer(GlanceModifier.width(GAP.dp))
                    RoundButton(
                        icon = R.drawable.ic_proposals,
                        description = "Propositions et gestion de la campagne",
                        background = claudeOrange,
                        action = actionStartActivity<ProposalsActivity>(),
                        diameter = chipH.dp,
                    )
                }
                if (showSite && site != null) {
                    Spacer(GlanceModifier.width(GAP.dp))
                    // Orange = the address answers; grey = not (yet) — it still opens.
                    RoundButton(
                        icon = if (state.siteKind == "apk") R.drawable.ic_download else R.drawable.ic_globe,
                        description = if (state.siteKind == "apk") (if (state.siteTermux != null) "Installer : copie la ligne Termux et ouvre Termux" else "Télécharger l'APK Android") else "Ouvrir le site",
                        background = if (live) claudeOrange else pill,
                        action = siteAction,
                        badge = state.ciFresh,
                        diameter = chipH.dp,
                    )
                }
                if (showStore) {
                    Spacer(GlanceModifier.width(GAP.dp))
                    // Orange = an app Claude sent is waiting; grey = none (yet). It opens the Store on
                    // the publish sheet with the app in it (OpenStoreActivity).
                    RoundButton(
                        icon = R.drawable.ic_store,
                        description = "Ouvrir l'app dans le Store",
                        background = if (storeReady) claudeOrange else pill,
                        action = actionStartActivity<OpenStoreActivity>(),
                        diameter = chipH.dp,
                    )
                }
                if (showCode) {
                    Spacer(GlanceModifier.width(GAP.dp))
                    // Orange = something was received and not opened yet; grey = nothing (yet). It opens the code, read-only.
                    RoundButton(
                        icon = R.drawable.ic_code,
                        description = if (state.sentApp?.github != null) "Voir le code sur GitHub" else "Voir le code reçu de Claude",
                        background = if (storeReady) claudeOrange else pill,
                        action = codeAction,
                        diameter = chipH.dp,
                    )
                }
                if (showActions) {
                    Spacer(GlanceModifier.width(GAP.dp))
                    // The colour is how the last run went: green, red, grey (running or unknown).
                    RoundButton(
                        icon = R.drawable.ic_actions,
                        description = if (state.deploy == "aiwa") "Workflow du registre : la vérification de ta publication" else "GitHub Actions : " + when (state.ciState) {
                            "success" -> "réussi"
                            "failure" -> "échec"
                            "running" -> "en cours"
                            else -> "état inconnu"
                        },
                        background = if (state.deploy == "aiwa") pill else when (state.ciState) { "success" -> green; "failure" -> alertRed; else -> pill },
                        action = actionStartActivity<OpenActionsActivity>(),
                        badge = state.ciFresh && !(showSite && site != null),
                        diameter = chipH.dp,
                    )
                }
            }
        }
        // The main action, big: dictate. The grey button with its red recording dot.
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            RowName(ROW_SPECIALIST)
            // A newer build is downloaded: one tap opens the installer
            if (state.appUpdateReady) {
                RoundButton(icon = R.drawable.ic_update, description = "Mise à jour d'Aiwa prête : installer", background = green, action = actionStartActivity<InstallUpdateActivity>(), diameter = 34.dp)
                Spacer(GlanceModifier.width(GAP.dp))
            }
            Box(
                modifier = GlanceModifier.defaultWeight().height(micH.dp)
                    .background(if (locked) MIC_DIM else micGrey)
                    .cornerRadius((micH / 2).dp)
                    .clickable(lockable(locked, micAction(state.needsRepo, state.backend == "missing", accessNotice(state, 1) != null))),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        provider = ImageProvider(R.drawable.rec_dot),
                        contentDescription = "Dicter un message",
                        modifier = GlanceModifier.size(16.dp),
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    Text("Dicter un message", style = TextStyle(color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
            }
            // To the right of the mic. Always there: with a session it opens that conversation in Claude, without one it opens Claude's
            // Code tab, where a session can be made or found. Red while Claude waits for an answer.
            Spacer(GlanceModifier.width(GAP.dp))
            RoundButton(
                icon = R.drawable.ic_claude,
                description = if (state.waiting) "Claude attend une réponse : ouvrir la conversation" else if (state.silenceMinutes > 0) "Rien reçu de Claude depuis un moment : ouvrir la conversation" else if (hasSession) "Ouvrir la conversation dans Claude" else "Ouvrir Claude",
                background = if (state.waiting || state.silenceMinutes > 0) alertRed else claudeOrange,
                action = actionStartActivity<OpenClaudeActivity>(),
                diameter = micH.dp,
            )
        }
    }
}

/**
 * The other shape of the widget: a pair of glasses. A drawn frame (two rounded lenses, the bridge, the start of the temples: glasses_frame) stretched over the card, and the
 * things placed INSIDE the lenses. Left lens: the context (the session and what is going on, the repository, the settings, the mode, Claude). Right lens: the application (the
 * logo, which opens it) and the microphone side by side, then what the work gives (the site or the proposals, ▦ and `</>`, the videos, the GitHub guide). Only what exists is shown.
 * Chosen in the settings list (WidgetStyle).
 */
@Composable
private fun GlassesContent(state: AiwaState) {
    val fg = rgb(android.graphics.Color.rgb(240, 240, 245))
    val pill = rgb(android.graphics.Color.rgb(52, 52, 64))
    val micGrey = rgb(android.graphics.Color.rgb(84, 84, 94))
    val claudeOrange = rgb(android.graphics.Color.rgb(204, 120, 92))
    val alertRed = rgb(android.graphics.Color.rgb(214, 69, 65))
    val locked = state.status == AiwaState.Status.WORKING
    val size = LocalSize.current
    val fontScale = LocalContext.current.resources.configuration.fontScale
    val hasRepo = state.repo != null
    val hasSession = state.cloudSessionId != null || state.lastSessionId != null
    val storeMode = deliversApp(state.deploy)
    val storeReady = storeMode && state.sentApp?.seen == false
    val campaignMode = state.deploy == "campaign"
    val site = state.siteUrl
    val live = state.siteState == "live"
    val showSite = hasRepo && site != null && (state.deploy != "none" || live) && !campaignMode

    // The frame is drawn on a 200 x 100 grid stretched to the widget: the lenses are 8..96 and 104..192 wide and 10..90 high, the things go inside them.
    val w = size.width.value
    val h = size.height.value
    val outer = w * 0.085f
    val inner = w * 0.355f
    val middle = w - 2 * outer - 2 * inner
    val top = h * 0.17f
    // Every chip is VERY small (a thin pill, small text, only as wide as its text); the microphone is the one big thing. All of them live in the left lens
    // (two chips to a row); the right lens holds the search field and the picture that opens the application.
    val micH = (h * 0.25f).coerceIn(26f, 36f)
    val itemH = ((h * 0.83f - 8f - micH) / 5f).coerceIn(11f, 16f)
    val textRoom = inner - 12f
    val half = (textRoom - GAP) / 2f
    val band = bandInfo(state, textRoom - itemH - GAP)
    val repoName = state.repo?.substringAfter('/')
    val repoText = "⎇ " + fitLabel(repoName ?: "Dépôt", half - textWidth("⎇  ▾", fontScale), fontScale) + " ▾"
    val settingsText = fitLabel(settingsSummary(state), half - textWidth(" ▾", fontScale), fontScale) + " ▾"
    val modeText = fitLabel(deployLabel(state.deploy), half, fontScale)
    val claudeText = if (state.waiting) "● attend" else if (state.silenceMinutes > 0) "⏳ ${state.silenceMinutes} min" else "Claude ↗"
    val micAct = lockable(locked, micAction(state.needsRepo, state.backend == "missing", accessNotice(state, 1) != null))

    // No card behind: the widget IS the glasses, and the home screen shows between and around the lenses. Red rims while Claude waits.
    Box(
        modifier = GlanceModifier.fillMaxSize(),
    ) {
        Box(modifier = GlanceModifier.fillMaxSize().background(ImageProvider(if (state.waiting) R.drawable.glasses_frame_alert else R.drawable.glasses_frame))) {
            Row(modifier = GlanceModifier.fillMaxSize().padding(top = top.dp, bottom = 4.dp)) {
                Spacer(GlanceModifier.width(outer.dp))
                // ---- the left lens: every button and pill, very small, two to a row -----
                Column(modifier = GlanceModifier.width(inner.dp).fillMaxHeight(), verticalAlignment = Alignment.Top) {
                    Row(modifier = GlanceModifier.fillMaxWidth().height((itemH + 4f).dp), verticalAlignment = Alignment.CenterVertically) {
                        // The loop between two sessions, at the left of the session: green while one runs
                        RoundButton(
                            icon = R.drawable.ic_loop,
                            description = if (state.loopPair.isNotEmpty()) "Boucle ${state.loopPair} en cours" else "Boucles builder / critique",
                            background = if (state.loopPair.isNotEmpty()) rgb(android.graphics.Color.rgb(46, 125, 90)) else pill,
                            action = actionStartActivity<LoopsActivity>(),
                            diameter = (itemH + 4f).dp,
                        )
                        Spacer(GlanceModifier.width(GAP.dp))
                        Column(modifier = GlanceModifier.defaultWeight().clickable(band.action), verticalAlignment = Alignment.CenterVertically) {
                            Text(band.title, style = TextStyle(color = fg, fontSize = 9.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                            Text(band.statusText, style = TextStyle(color = band.statusColor, fontSize = 8.sp, fontWeight = if (band.statusBold) FontWeight.Bold else FontWeight.Normal), maxLines = 1)
                        }
                        // The question Claude asks has a button of its own, at the right of the session (the session keeps opening the list of sessions)
                        if (state.ask != null) {
                            Spacer(GlanceModifier.width(GAP.dp))
                            RoundButton(icon = R.drawable.ic_question, description = "Claude te pose une question : répondre", background = alertRed, action = actionStartActivity<AskActivity>(), diameter = (itemH + 4f).dp)
                        }
                    }
                    Row(modifier = GlanceModifier.fillMaxWidth().height(itemH.dp)) {
                        Chip(repoText, if (locked) PILL_DIM else if (!hasRepo) alertRed else pill, if (locked) TEXT_DIM else fg, lockable(locked, actionStartActivity<RepoPickerActivity>()), GlanceModifier, height = itemH.dp, textSize = 8.sp, hPad = 5.dp)
                        Spacer(GlanceModifier.width(GAP.dp))
                        Chip(settingsText, if (locked) PILL_DIM else if (workflowFailed(state)) alertRed else pill, if (locked) TEXT_DIM else fg, lockable(locked, actionStartActivity<SettingsActivity>()), GlanceModifier, height = itemH.dp, textSize = 8.sp, hPad = 5.dp)
                    }
                    Row(modifier = GlanceModifier.fillMaxWidth().height(itemH.dp)) {
                        Chip(modeText, if (locked) (if (state.deploy != "none") GREEN_DIM else PILL_DIM) else pill, if (locked) TEXT_DIM else fg, lockable(locked, actionStartActivity<DeployPickerActivity>()), GlanceModifier, height = itemH.dp, textSize = 8.sp, hPad = 5.dp)
                        Spacer(GlanceModifier.width(GAP.dp))
                        Chip(claudeText, if (state.waiting || state.silenceMinutes > 0) alertRed else claudeOrange, fg, actionStartActivity<OpenClaudeActivity>(), GlanceModifier, height = itemH.dp, textSize = 8.sp, hPad = 5.dp)
                    }
                    // The one big thing: the microphone; at its left the update, when a newer build is downloaded (one tap opens the installer)
                    Row(modifier = GlanceModifier.fillMaxWidth().height(micH.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (state.appUpdateReady) {
                            RoundButton(icon = R.drawable.ic_update, description = "Mise à jour d'Aiwa prête : installer", background = rgb(android.graphics.Color.rgb(46, 125, 90)), action = actionStartActivity<InstallUpdateActivity>(), diameter = (itemH + 6f).dp)
                            Spacer(GlanceModifier.width(GAP.dp))
                        }
                        Box(
                            modifier = GlanceModifier.defaultWeight().height(micH.dp).background(if (locked) MIC_DIM else micGrey).cornerRadius((micH / 2).dp).clickable(micAct),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(provider = ImageProvider(R.drawable.rec_dot), contentDescription = "Dicter un message", modifier = GlanceModifier.size(18.dp))
                                Spacer(GlanceModifier.width(6.dp))
                                Text("Dicter", style = TextStyle(color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                            }
                        }
                    }
                    Row(modifier = GlanceModifier.fillMaxWidth().height(itemH.dp)) {
                        if (campaignMode) Chip("✉ Propositions", claudeOrange, fg, actionStartActivity<ProposalsActivity>(), GlanceModifier, height = itemH.dp, textSize = 8.sp, hPad = 5.dp)
                        else if (showSite && site != null) Chip(if (state.siteKind == "apk") "⇩ APK" else "🌐 Site", if (live) claudeOrange else pill, fg, siteAction, GlanceModifier, height = itemH.dp, badge = false, textSize = 8.sp, hPad = 5.dp)
                        if (storeMode) {
                            Spacer(GlanceModifier.width(GAP.dp))
                            Chip("▦", if (storeReady) claudeOrange else pill, fg, actionStartActivity<OpenStoreActivity>(), GlanceModifier, height = itemH.dp, textSize = 8.sp, hPad = 5.dp)
                            Spacer(GlanceModifier.width(GAP.dp))
                            Chip("</>", if (storeReady) claudeOrange else pill, fg, codeAction, GlanceModifier, height = itemH.dp, textSize = 8.sp, hPad = 5.dp)
                        }
                    }
                    Row(modifier = GlanceModifier.fillMaxWidth().height(itemH.dp)) {
                        if (hasRepo) Chip("🎬 Vidéos", pill, fg, actionStartActivity<VideosActivity>(), GlanceModifier, height = itemH.dp, textSize = 8.sp, hPad = 5.dp)
                        if (hasRepo && state.ciHintTitle != null) {
                            Spacer(GlanceModifier.width(GAP.dp))
                            Chip("⚠ GitHub", alertRed, fg, actionStartActivity<PagesHelpActivity>(), GlanceModifier, height = itemH.dp, textSize = 8.sp, hPad = 5.dp)
                        }
                    }
                }
                // ---- the bridge: nothing on it, it is the frame ------------------------
                Spacer(GlanceModifier.width(middle.dp))
                // ---- the right lens: the search field, above the picture that opens the application ----
                Column(modifier = GlanceModifier.width(inner.dp).fillMaxHeight(), verticalAlignment = Alignment.Top, horizontalAlignment = Alignment.CenterHorizontally) {
                    // A widget cannot hold a text field: this one looks like it and opens the real one.
                    Box(
                        modifier = GlanceModifier.fillMaxWidth().height((itemH + 6f).dp).background(pill).cornerRadius(((itemH + 6f) / 2).dp).clickable(actionStartActivity<SearchActivity>()),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text("  🔍 id ou compte", style = TextStyle(color = rgb(android.graphics.Color.rgb(160, 160, 174)), fontSize = 8.sp), maxLines = 1)
                    }
                    Spacer(GlanceModifier.height(4.dp))
                    // « own »: the apps published with this wallet; « faves »: the starred ones. Each opens that tab of the Store.
                    val context = LocalContext.current
                    fun storeTab(tab: String): Action = actionStartIntent(
                        Intent(context, StoreActivity::class.java).setData(Uri.parse("aiwa://store/$tab"))
                            .putExtra(StoreActivity.EXTRA_URL, storeTabUrl(tab) ?: STORE_APP_URL),
                    )
                    Row(modifier = GlanceModifier.fillMaxWidth().height(itemH.dp)) {
                        Chip("own", pill, fg, storeTab("mine"), GlanceModifier, height = itemH.dp, textSize = 8.sp, hPad = 5.dp)
                        Spacer(GlanceModifier.width(GAP.dp))
                        Chip("★ faves", pill, fg, storeTab("favorites"), GlanceModifier, height = itemH.dp, textSize = 8.sp, hPad = 5.dp)
                    }
                    Row(modifier = GlanceModifier.fillMaxWidth().height(itemH.dp)) {
                        Chip("Store", pill, fg, actionStartActivity<StoreActivity>(), GlanceModifier, height = itemH.dp, textSize = 8.sp, hPad = 5.dp)
                        Spacer(GlanceModifier.width(GAP.dp))
                        Chip("Wallet", pill, fg, storeTab("wallet"), GlanceModifier, height = itemH.dp, textSize = 8.sp, hPad = 5.dp)
                    }
                    Chip("✉ inviter", pill, fg, actionStartActivity<InviteActivity>(), GlanceModifier, height = itemH.dp, textSize = 8.sp, hPad = 5.dp)
                }
                Spacer(GlanceModifier.width(outer.dp))
            }
        }
    }
}

// ================================================================================================
// The third shape, « Aiwa »: the modes in a row at the top (Média · Page · App · Store · Aiwa; Média holds Vidéo, Campagne and Documents); what the chosen mode needs; the mic, Claude, the question,
// the model, the push and the state of the workflow; and a bottom row (loop, the Aiwa app, the update when there is one, the settings). Session and repository
// are in the list of the « contexte » button. A tap on a mode IS the deploy selector (Média and Vidéo only change what is shown).
// ================================================================================================

private val MODE_KEY = ActionParameters.Key<String>("aiwa-mode")

/** A tab of the top row: the deploy selector's value (or "media": the group of Vidéo, Campagne and Documents), and its word. */
private class AiwaMode(val id: String, val word: String)
private val AIWA_MODES = listOf(
    AiwaMode("media", "Média"), AiwaMode("pages", "Page"), AiwaMode("android", "App"), AiwaMode("store", "Store"), AiwaMode("aiwa", "Aiwa"),
)
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

class PickModeCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val mode = parameters[MODE_KEY] ?: return
        selectAiwaMode(context, mode)
        // the campaign mode simply asks for a file: its window opens at once (as in the list of the selector)
        if (mode == "campaign") context.applicationContext.startActivity(Intent(context, FileCreationActivity::class.java).putExtra("campaign", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun AiwaContent(state: AiwaState) {
    val fg = rgb(android.graphics.Color.rgb(240, 240, 245))
    val pill = rgb(android.graphics.Color.rgb(44, 44, 54))
    val micGrey = rgb(android.graphics.Color.rgb(84, 84, 94))
    val claudeOrange = rgb(android.graphics.Color.rgb(204, 120, 92))
    val alertRed = rgb(android.graphics.Color.rgb(214, 69, 65))
    val green = rgb(android.graphics.Color.rgb(46, 125, 90))
    val locked = state.status == AiwaState.Status.WORKING
    val context = LocalContext.current
    val size = LocalSize.current
    val fontScale = context.resources.configuration.fontScale
    val avail = size.width.value - 20f
    val hasRepo = state.repo != null
    val tab = aiwaTab(state)
    val sub = mediaSub(state)
    val storeReady = deliversApp(state.deploy) && state.sentApp?.seen == false
    val rows = 5
    val rowH = ((size.height.value - 16f) / rows).coerceIn(26f, 42f)
    val chipH = (rowH - 4f).coerceAtLeast(22f)
    val band = bandInfo(state, avail - 20f)
    val appContext = LocalContext.current
    fun storeDoor(tab: String?): Action = actionStartIntent(
        Intent(appContext, StoreActivity::class.java).setData(Uri.parse("aiwa://store/" + (tab ?: "home")))
            .putExtra(StoreActivity.EXTRA_URL, (tab?.let { storeTabUrl(it) }) ?: STORE_APP_URL),
    )
    val loopOn = state.loopPair.isNotEmpty()

    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(ImageProvider(if (state.waiting) R.drawable.widget_bg_alert else R.drawable.widget_bg))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        // What is going on, only when it is not « Prêt » (sending, the silence of Claude, what to fix…)
        if (band.statusText != "Prêt") {
            // its tap is what the line is about (the fix for what is wrong, else the sessions)
            Text(band.statusText, style = TextStyle(color = band.statusColor, fontSize = 10.sp, fontWeight = if (band.statusBold) FontWeight.Bold else FontWeight.Normal), maxLines = 1, modifier = GlanceModifier.fillMaxWidth().clickable(band.action))
        }
        // ---- the modes ----
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            val shown = AIWA_MODES.filter { it.id == "media" || state.accessLevel >= 2 || !deliversApp(it.id) }
            // The width each of them has: the words shrink, and the longest is cut short, before one of the buttons would leave the screen
            val each = (avail - 3f * (shown.size - 1)) / shown.size
            val wordSize = when { each >= 56f -> 11.sp; each >= 48f -> 10.sp; else -> 9.sp }
            shown.forEachIndexed { i, mode ->
                if (i > 0) Spacer(GlanceModifier.width(3.dp))
                val on = mode.id == tab
                Chip(
                    mode.word, if (on) green else pill, fg,
                    actionRunCallback<PickModeCallback>(actionParametersOf(MODE_KEY to mode.id)),
                    GlanceModifier.defaultWeight(), bold = on, height = chipH.dp, textSize = wordSize, hPad = 1.dp,
                    // the red dot of the news (a build, a deployment finished) is on the tab of the mode it is about, whichever tab is shown
                    badge = state.ciFresh && mode.id == (if (state.deploy in MEDIA_DEPLOYS) "media" else state.deploy),
                )
            }
        }
        // ---- what the mode needs ----
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            // The Store, at the left of the repository selector
            RoundButton(icon = R.drawable.ic_store, description = "Le Store", background = pill, action = storeDoor(null), diameter = chipH.dp)
            Spacer(GlanceModifier.width(GAP.dp))
            // The repository selector, at the left of the line of every mode
            val repoName = state.repo?.substringAfter('/')
            Chip(
                "⎇ " + fitLabel(repoName ?: "Dépôt", 54f - textWidth("⎇  ▾", fontScale), fontScale) + " ▾",
                if (locked) PILL_DIM else if (!hasRepo) alertRed else pill, if (locked) TEXT_DIM else fg,
                lockable(locked, actionStartActivity<RepoPickerActivity>()), GlanceModifier.width(76.dp), alignStart = true, height = chipH.dp, textSize = 11.sp, hPad = 6.dp,
            )
            Spacer(GlanceModifier.width(GAP.dp))
            when {
                // Média: ONE pill that says what is shown and lists the kinds (videos, campaign / proposals, documents); picking one opens it
                tab == "media" -> Chip(
                    when (sub) { "campaign" -> "✉ Campagne ▾"; "documents" -> "📄 Documents ▾"; else -> "🎬 Vidéos ▾" },
                    pill, fg, actionStartActivity<MediaPickerActivity>(), GlanceModifier.defaultWeight(), alignStart = true, height = chipH.dp, textSize = 11.sp,
                    badge = state.ciFresh && state.deploy in MEDIA_DEPLOYS,
                )
                deliversApp(tab) -> {
                    Chip("▦ App", if (storeReady) claudeOrange else pill, fg, actionStartActivity<OpenStoreActivity>(), GlanceModifier.defaultWeight(), height = chipH.dp, textSize = 11.sp, badge = state.ciFresh)
                    Spacer(GlanceModifier.width(GAP.dp))
                    Chip("</> Code", if (storeReady) claudeOrange else pill, fg, codeAction, GlanceModifier.defaultWeight(), height = chipH.dp, textSize = 11.sp)
                }
                else -> {
                    val live = state.siteState == "live"
                    if (hasRepo && state.siteUrl != null) Chip(if (state.siteKind == "apk") "⇩ APK" else "🌐 Site", if (live) claudeOrange else pill, fg, siteAction, GlanceModifier.defaultWeight(), height = chipH.dp, textSize = 11.sp, badge = state.ciFresh)
                    else Chip(if (hasRepo) "Pas encore de site" else "Choisis un dépôt (⎇)", pill, fg, actionStartActivity<RepoPickerActivity>(), GlanceModifier.defaultWeight(), height = chipH.dp, textSize = 11.sp)
                }
            }
            if (hasRepo && state.ciHintTitle != null) {
                Spacer(GlanceModifier.width(GAP.dp))
                RoundButton(icon = R.drawable.ic_warning, description = "Un réglage de GitHub à faire : le guide", background = alertRed, action = actionStartActivity<PagesHelpActivity>(), diameter = chipH.dp)
            }
            // The workflow of the mode (the build, the deployment, the check of the app, the registry's check): a button to look at where it is, nothing more.
            // Its success is told by the red dot on the button of what was made.
            if (workflowShown(state)) {
                Spacer(GlanceModifier.width(GAP.dp))
                Chip("⚙ Workflow", pill, fg, actionStartActivity<OpenActionsActivity>(), GlanceModifier, height = chipH.dp, textSize = 11.sp, hPad = 8.dp)
            }
        }
        // ---- the microphone, Claude, the question ----
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            // The loop between two sessions, at the left of the session: green while one runs
            RoundButton(
                icon = R.drawable.ic_loop,
                description = if (loopOn) "Boucle ${state.loopPair} en cours : voir, arrêter ou revenir en arrière" else "Boucles builder / critique",
                background = if (loopOn) green else pill,
                action = actionStartActivity<LoopsActivity>(),
                diameter = (rowH - 2f).dp,
            )
            Spacer(GlanceModifier.width(GAP.dp))
            // ONE button for the session, the repository, the model and the push: its tap opens them as a list. What is wrong (Aiwa not allowed, no backend, Claude not
            // connected…) replaces its word, and its tap is then the fix.
            Chip(
                if (band.title.endsWith("▸")) band.title else fitLabel(state.session, 60f - textWidth(" ▾", fontScale), fontScale) + " ▾",
                if (locked) PILL_DIM else if (band.title.endsWith("▸")) alertRed else pill, if (locked) TEXT_DIM else fg,
                if (band.title.endsWith("▸")) band.action else lockable(locked, actionStartActivity<ContextActivity>()),
                GlanceModifier.width(80.dp), alignStart = true, height = (rowH - 2f).dp, textSize = 11.sp, hPad = 8.dp,
            )
            Spacer(GlanceModifier.width(GAP.dp))
            Box(
                modifier = GlanceModifier.defaultWeight().height((rowH - 2f).dp).background(if (locked) MIC_DIM else micGrey).cornerRadius(((rowH - 2f) / 2).dp)
                    .clickable(lockable(locked, micAction(state.needsRepo, state.backend == "missing", accessNotice(state, 1) != null))),
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
        }
        // ---- the bottom row: the wallet, the papers, the super agent, the update when there is one, the settings ----
        // Every button has its own equal slot of the row (the row is shared out, nothing is measured by hand): none can be pushed out whatever the width of the widget,
        // and the settings, the last slot, is always at the right end.
        val bottomCount = 4 + (if (state.appUpdateReady) 1 else 0)
        val bottomD = minOf(chipH, avail / bottomCount - 4f).coerceAtLeast(18f)
        val agent = state.agent
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            @Composable fun Slot(content: @Composable () -> Unit) {
                Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) { content() }
            }
            Slot { RoundButton(icon = R.drawable.ic_wallet, description = "Le Wallet", background = pill, action = storeDoor("wallet"), diameter = bottomD.dp) }
            Slot { RoundButton(icon = R.drawable.ic_papers, description = "Les papers (l'onglet Papers de l'application)", background = pill, action = storeDoor("papers"), diameter = bottomD.dp) }
            // The super agent: green while it listens and follows, red when it has something to say or a proposal waits for "vas-y dis-lui"
            Slot {
                RoundButton(
                    icon = R.drawable.ic_agent,
                    description = "Super agent : ouvrir ou fermer (l'écoute de réveil attend pendant ce temps)",
                    background = if (agent != null && (agent.say != null || agent.proposal != null || agent.command != null)) alertRed else if (agent?.enabled == true) green else pill,
                    action = actionStartActivity<AgentToggleActivity>(),
                    diameter = bottomD.dp,
                )
            }
            if (state.appUpdateReady) {
                Slot { RoundButton(icon = R.drawable.ic_update, description = "Mise à jour d'Aiwa prête : installer", background = green, action = actionStartActivity<InstallUpdateActivity>(), diameter = bottomD.dp) }
            }
            Slot {
                RoundButton(
                    icon = R.drawable.ic_settings,
                    description = "Réglages : session, dépôt, modèle, voix, commentaire, mises à jour…",
                    background = if (workflowFailed(state)) alertRed else pill,
                    action = actionStartActivity<SettingsActivity>(),
                    diameter = bottomD.dp,
                )
            }
        }
    }
}

// ================================================================================================
// The fourth shape, « Light »: three rows and nothing else. (1) the mode as ONE dropdown (Média, Page, App, Store, Aiwa — the same for every mode) and the targets of the
// mode, which are the person's own: Prod, Code, Workflow. (2) the settings, the update (when one is ready), the microphone, Claude and the question (when there is one).
// (3) the loop, the Aiwa app, the super agent and the campaign; the settings hold everything else (session, repository, model, push…).
// ================================================================================================

/** What the Prod button opens: for Média what the tab shows (videos, proposals, documents); for the modes that make an app, the app; else the site. */
private fun lightProd(state: AiwaState, context: Context): Action = when {
    aiwaTab(state) == "media" -> when (mediaSub(state)) {
        "campaign" -> actionStartActivity<ProposalsActivity>()
        "documents" -> actionStartActivity<DocumentsActivity>()
        else -> actionStartActivity<VideosActivity>()
    }
    deliversApp(state.deploy) -> actionStartActivity<OpenStoreActivity>()
    state.repo != null && state.siteUrl != null -> siteAction
    else -> actionStartActivity<RepoPickerActivity>()
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
    val rowH = ((size.height.value - 16f) / 3f).coerceIn(30f, 46f)
    val chipH = (rowH - 4f).coerceAtLeast(24f)
    val loopOn = state.loopPair.isNotEmpty()
    val agent = state.agent
    val modeWord = when (tab) {
        "media" -> when (mediaSub(state)) { "campaign" -> "Campagne"; "documents" -> "Documents"; else -> "Vidéos" }
        else -> AIWA_MODES.firstOrNull { it.id == tab }?.word ?: "Mode"
    }
    val prodSite = tab != "media" && !deliversApp(state.deploy)
    val prodReady = if (prodSite) state.siteState == "live" else state.sentApp?.seen == false
    val bottomCount = 5
    val bottomD = minOf(chipH, avail / bottomCount - 4f).coerceAtLeast(20f)

    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(ImageProvider(if (state.waiting) R.drawable.widget_bg_alert else R.drawable.widget_bg))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        // ---- the mode, and its targets ----
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            // The Aiwa logo opens the list of the modes and of what each has produced
            RoundButton(icon = R.drawable.ic_aiwa, description = "Les modes et leurs productions : vidéos, documents, page, app, store", background = pill, action = actionStartActivity<ModePickerActivity>(), diameter = chipH.dp, badge = state.ciFresh)
            Spacer(GlanceModifier.width(GAP.dp))
            Chip(
                "⎇ " + fitLabel(state.repo?.substringAfter('/') ?: "Dépôt", 54f - textWidth("⎇  ▾", context.resources.configuration.fontScale), context.resources.configuration.fontScale) + " ▾",
                if (locked) PILL_DIM else if (state.repo == null) alertRed else pill, if (locked) TEXT_DIM else fg,
                lockable(locked, actionStartActivity<RepoPickerActivity>()), GlanceModifier.defaultWeight(), alignStart = true, height = chipH.dp, textSize = 11.sp, hPad = 6.dp,
            )
        }
        // ---- the microphone, Claude, the question, the update ----
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            if (state.appUpdateReady) {
                RoundButton(icon = R.drawable.ic_update, description = "Mise à jour d'Aiwa prête : installer", background = green, action = actionStartActivity<InstallUpdateActivity>(), diameter = chipH.dp)
                Spacer(GlanceModifier.width(GAP.dp))
            }
            Box(
                modifier = GlanceModifier.defaultWeight().height((rowH - 2f).dp).background(if (locked) MIC_DIM else micGrey).cornerRadius(((rowH - 2f) / 2).dp)
                    .clickable(lockable(locked, micAction(state.needsRepo, state.backend == "missing", accessNotice(state, 1) != null))),
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
private fun CompactContent(state: AiwaState) {
    val fg = rgb(android.graphics.Color.rgb(240, 240, 245))
    val pill = rgb(android.graphics.Color.rgb(44, 44, 54))
    val micGrey = rgb(android.graphics.Color.rgb(84, 84, 94))
    val claudeOrange = rgb(android.graphics.Color.rgb(204, 120, 92))
    val alertRed = rgb(android.graphics.Color.rgb(214, 69, 65))
    val green = rgb(android.graphics.Color.rgb(46, 125, 90))
    val locked = state.status == AiwaState.Status.WORKING
    val size = LocalSize.current
    val fontScale = LocalContext.current.resources.configuration.fontScale
    // Room for the second row (two rows of buttons plus the padding).
    val tall = size.height >= 96.dp
    // The card's own padding takes 10 dp on each side.
    val avail = size.width.value - 20f
    val hasSession = state.cloudSessionId != null || state.lastSessionId != null

    // ---- row 1: the conversation -------------------------------------------
    // While a message is on its way (creating a cloud session takes a few
    // seconds) the session chip says so: the widget has no other place to show
    // progress. The backend's own state comes first: with it down or starting,
    // nothing else on the widget can be trusted to work, and a widget that just
    // sits there looks broken.
    val needsSetup = !hasTermuxPermission(LocalContext.current)
    val needsLogin = state.claudeLogin == "needed"
    val storeMode = deliversApp(state.deploy)
    val storeReady = storeMode && state.sentApp?.seen == false
    val sessionLabel = when {
        needsSetup -> "Autoriser Aiwa"
        accessNotice(state, 1) != null -> "Accès réservé"
        state.backend == "missing" -> "⚠ À installer"
        state.backend == "starting" -> "⏳ Démarrage"
        state.backend == "down" -> "⚠ Arrêté"
        needsLogin -> "Connecter Claude"
        state.status == AiwaState.Status.WORKING -> "Envoi…"
        else -> state.session
    }
    val modelText = modelLabel(state.model).replace(" · ", "·").take(12) + " ▾"
    val pillText = "● Claude ↗"
    val updateRoom = 0f
    val fixedLeft = 36f + 36f + 40f + chipWidth(modelText, fontScale) + 4 * GAP + updateRoom // A, the voice button, mic, model, the update button when there is one, and the gaps before them
    // Claude waiting gets its words when there is room for them next to a
    // readable session name; otherwise it stays a round button, still red.
    val claudePill = state.waiting && avail - fixedLeft - (chipWidth(pillText, fontScale) + GAP) >= 96f
    val claudeWidth = when {
        claudePill -> chipWidth(pillText, fontScale) + GAP
        else -> 36f + GAP
    }
    val sessionText = fitLabel(sessionLabel, avail - fixedLeft - claudeWidth - 20f - textWidth(" ▾", fontScale), fontScale) + " ▾"

    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(ImageProvider(if (state.waiting) R.drawable.widget_bg_alert else R.drawable.widget_bg))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(R.drawable.logo_round),
                contentDescription = "Ouvrir Aiwa",
                modifier = GlanceModifier.size(36.dp).clickable(actionStartActivity<StoreActivity>()),
            )
            Spacer(GlanceModifier.width(GAP.dp))
            Chip(
                sessionText, if (locked) PILL_DIM else pill, if (locked) TEXT_DIM else fg,
                when {
                    needsSetup -> actionStartActivity<SetupActivity>()
                    accessNotice(state, 1) != null -> actionStartActivity<AccessActivity>()
                    state.backend == "missing" -> actionStartActivity<InstallHelpActivity>()
                    needsLogin -> actionStartActivity<ClaudeLoginActivity>()
                    else -> lockable(locked, actionStartActivity<SessionPickerActivity>())
                },
                GlanceModifier.defaultWeight(), bold = true, alignStart = true,
            )
            Spacer(GlanceModifier.width(GAP.dp))
            RoundButton(
                icon = R.drawable.ic_voice,
                description = if (state.listening) "Réglages : l'écoute permanente est allumée" else "Réglages : modèle, push, workflow, écoute, mot d'éveil, mises à jour",
                background = if (state.listening) green else pill,
                action = actionStartActivity<SettingsActivity>(),
                diameter = 36.dp,
            )
            Spacer(GlanceModifier.width(GAP.dp))
            if (state.appUpdateReady) {
                RoundButton(icon = R.drawable.ic_update, description = "Mise à jour d'Aiwa prête : installer", background = green, action = actionStartActivity<InstallUpdateActivity>(), diameter = 36.dp)
                Spacer(GlanceModifier.width(GAP.dp))
            }
            Box(
                modifier = GlanceModifier.size(40.dp)
                    .background(if (locked) MIC_DIM else micGrey)
                    .cornerRadius(20.dp)
                    .clickable(lockable(locked, micAction(state.needsRepo, state.backend == "missing", accessNotice(state, 1) != null))),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.rec_dot),
                    contentDescription = "Dicter un message",
                    modifier = GlanceModifier.size(16.dp),
                )
            }
            Spacer(GlanceModifier.width(GAP.dp))
            Chip(modelText, if (locked) PILL_DIM else pill, if (locked) TEXT_DIM else fg, lockable(locked, actionStartActivity<ModelPickerActivity>()))
            // Always there: with a session it opens that conversation, without one Claude's Code tab. Red while Claude waits for
            // an answer (it pinged the relay): the alert is this button and the
            // card's edge, not a notification.
            Spacer(GlanceModifier.width(GAP.dp))
            if (claudePill) {
                Chip(pillText, alertRed, fg, actionStartActivity<OpenClaudeActivity>(), bold = true)
            } else {
                RoundButton(
                    icon = R.drawable.ic_claude,
                    description = if (state.waiting) "Claude attend une réponse : ouvrir la conversation" else if (hasSession) "Ouvrir la conversation dans Claude" else "Ouvrir Claude",
                    background = if (state.waiting) alertRed else claudeOrange,
                    action = actionStartActivity<OpenClaudeActivity>(),
                    diameter = 36.dp,
                )
            }
        }
        if (tall) {
            Spacer(GlanceModifier.height(GAP.dp))
            // ---- row 2: the GitHub project -----------------------------------
            // The buttons appear as you go: the repository picker first, then
            // (once a repository is chosen) push mode and deployment, then the
            // site and the Actions. They are instructions integrated into the
            // conversation — Claude Code does the work itself.
            val hasRepo = state.repo != null
            val pushText = if (state.pushMain) "Push main" else "Push branche"
            val deployText = deployLabel(state.deploy)
            val site = state.siteUrl
            val live = state.siteState == "live"
            // The address is known as soon as a repository is chosen
            // (https://<owner>.github.io/<repo>/): the globe is there once
            // deployment is asked for, or as soon as the address answers.
            val campaignMode = state.deploy == "campaign"
            val showSite = hasRepo && site != null && (state.deploy != "none" || live) && !campaignMode
            val showProposals = campaignMode
            val showStore = storeMode
            // In the Store mode nothing is published through GitHub: the round "GitHub Actions" button (the build of this
            // repository) has no meaning there, and gives way to a button that shows the code Claude sent.
            val showCode = showStore
            val showActions = (hasRepo && (publishesFromGithub(state.deploy) || storeMode || state.ciState != null)) || state.deploy == "aiwa"
            var others = 0f
            others += GAP + chipWidth(deployText, fontScale)
            if (hasRepo) others += GAP + chipWidth(pushText, fontScale)
            if (showSite || showProposals) others += GAP + 34f
            if (showStore) others += GAP + 34f
            if (showCode) others += GAP + 34f
            if (showActions) others += GAP + 34f
            val repoName = state.repo?.substringAfter('/')
            val repoText = if (repoName == null) {
                "⎇ Choisir un dépôt ▾"
            } else {
                val more = if (state.extraRepos.isNotEmpty()) " +${state.extraRepos.size}" else ""
                "⎇ " + fitLabel(repoName, avail - others - 20f - textWidth("⎇  ▾", fontScale) - textWidth(more, fontScale), fontScale) + more + " ▾"
            }
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Chip(repoText, if (locked) PILL_DIM else pill, if (locked) TEXT_DIM else fg, lockable(locked, actionStartActivity<RepoPickerActivity>()), GlanceModifier.defaultWeight(), alignStart = true, badge = state.repo == null)
                if (hasRepo) {
                    Spacer(GlanceModifier.width(GAP.dp))
                    Chip(pushText, if (locked) (if (state.pushMain) GREEN_DIM else PILL_DIM) else if (state.pushMain) green else pill, if (locked) TEXT_DIM else fg, lockable(locked, actionRunCallback<TogglePushMainCallback>()))
                }
                run {
                    Spacer(GlanceModifier.width(GAP.dp))
                    if (hasRepo && state.ciHintTitle != null) {
                        // A setting of GitHub to change by hand: its own button (the guide), instead of changing what the other buttons do.
                        RoundButton(
                            icon = R.drawable.ic_warning,
                            description = "Un réglage de GitHub à faire : le guide",
                            background = alertRed,
                            action = actionStartActivity<PagesHelpActivity>(),
                        )
                        Spacer(GlanceModifier.width(GAP.dp))
                    }
                    Chip(deployText, if (locked) (if (state.deploy != "none") GREEN_DIM else PILL_DIM) else if (state.deploy != "none") green else pill, if (locked) TEXT_DIM else fg, lockable(locked, actionStartActivity<DeployPickerActivity>()))
                    if (showProposals) {
                        Spacer(GlanceModifier.width(GAP.dp))
                        RoundButton(
                            icon = R.drawable.ic_proposals,
                            description = "Propositions et gestion de la campagne",
                            background = claudeOrange,
                            action = actionStartActivity<ProposalsActivity>(),
                        )
                    }
                    if (showSite && site != null) {
                        Spacer(GlanceModifier.width(GAP.dp))
                        // Orange = the address answers; grey = not (yet) — it still opens.
                        RoundButton(
                            icon = if (state.siteKind == "apk") R.drawable.ic_download else R.drawable.ic_globe,
                            description = if (state.siteKind == "apk") (if (state.siteTermux != null) "Installer : copie la ligne Termux et ouvre Termux" else "Télécharger l'APK Android") else "Ouvrir le site",
                            background = if (live) claudeOrange else pill,
                            action = siteAction,
                            badge = state.ciFresh,
                        )
                    }
                    if (showStore) {
                        Spacer(GlanceModifier.width(GAP.dp))
                        RoundButton(
                            icon = R.drawable.ic_store,
                            description = "Ouvrir l'app dans le Store",
                            background = if (storeReady) claudeOrange else pill,
                            action = actionStartActivity<OpenStoreActivity>(),
                        )
                    }
                    if (showCode) {
                        Spacer(GlanceModifier.width(GAP.dp))
                        RoundButton(
                            icon = R.drawable.ic_code,
                            description = if (state.sentApp?.github != null) "Voir le code sur GitHub" else "Voir le code reçu de Claude",
                            background = if (storeReady) claudeOrange else pill,
                            action = codeAction,
                        )
                    }
                    if (showActions) {
                        Spacer(GlanceModifier.width(GAP.dp))
                        // The colour is how the last run went: green, red, grey (running or unknown).
                        RoundButton(
                            icon = R.drawable.ic_actions,
                            description = if (state.deploy == "aiwa") "Workflow du registre : la vérification de ta publication" else "GitHub Actions : " + when (state.ciState) {
                                "success" -> "réussi"
                                "failure" -> "échec"
                                "running" -> "en cours"
                                else -> "état inconnu"
                            },
                            background = if (state.deploy == "aiwa") pill else when (state.ciState) { "success" -> green; "failure" -> alertRed; else -> pill },
                            action = actionStartActivity<OpenActionsActivity>(),
                            badge = state.ciFresh && !(showSite && site != null),
                        )
                    }
                }
            }
        }
    }
}

// A tap on something locked while a message is on its way: says why, in the widget's own line and in a toast.
class BusyTapCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        tellBusy(context, toast = true)
    }
}

// The widget's "Push" (main / branch) button changes an instruction without opening anything ("Deploy" opens a list).
class TogglePushMainCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        switchOptions(context, LocalClaudeBridge(), pushMain = !AiwaRepository.state.value.pushMain)
    }
}

