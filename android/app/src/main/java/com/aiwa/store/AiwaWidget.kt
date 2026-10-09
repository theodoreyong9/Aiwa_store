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
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.*
import com.aiwa.bridge.LocalClaudeBridge
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
private fun micAction(needsRepo: Boolean, backendMissing: Boolean): Action = when {
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
    else -> "Deploy ▾"
}

// GitHub Actions only matter when the work is published from GitHub.
private fun publishesFromGithub(deploy: String) = deploy == "pages" || deploy == "android"


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
) {
    if (!badge) {
        ChipFace(text, background, color, action, modifier.height(height), bold, alignStart, height)
        return
    }
    // Only a chip whose width is given (a weighted one) can carry the dot: the face then fills the box the dot sits on.
    Box(modifier = modifier.height(height), contentAlignment = Alignment.TopEnd) {
        ChipFace(text, background, color, action, GlanceModifier.fillMaxSize(), bold, alignStart, height)
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
) {
    Box(
        modifier = modifier.background(background).cornerRadius(height / 2).padding(horizontal = 10.dp).clickable(action),
        contentAlignment = if (alignStart) Alignment.CenterStart else Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(color = color, fontSize = 12.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium),
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
    if (LocalSize.current.height >= 150.dp) FullContent(state) else CompactContent(state)
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
    val bands = if (hasRepo) 4 else 3
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
    val status: String
    val statusColor: ColorProvider
    var statusBold = false
    when {
        needsSetup -> { status = "Touche ici pour autoriser Aiwa (une seule fois)"; statusColor = alertText; statusBold = true }
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
        state.ask != null -> { status = "❓ Claude te pose une question — touche ici"; statusColor = alertText; statusBold = true }
        state.waiting -> { status = "● Claude attend ta réponse"; statusColor = alertText; statusBold = true }
        storeReady -> {
            status = "App prête : touche ▦ pour l'ouvrir dans le Store, </> montre son code"
            statusColor = fg
            statusBold = true
        }
        // The last send did not go: its reason was only a toast, and "Prêt" made it look as if nothing was wrong.
        state.status == AiwaState.Status.ERROR && state.notice != null -> { status = "⚠ Dernier envoi échoué — touche ici"; statusColor = alertText; statusBold = true }
        // Claude's GitHub app is not on this repository: a session started on it has an empty folder, and it will again.
        state.repoAccessMissing != null && state.repoAccessMissing == state.repo -> { status = "⚠ Claude n'a pas accès à ce dépôt — touche ici"; statusColor = alertText; statusBold = true }
        // Every session starts on a repository: none chosen, nothing to send yet.
        state.needsRepo -> { status = "Choisis un dépôt (⎇) pour commencer"; statusColor = warm; statusBold = true }
        // A session with no repository (added by a branch that exists in several of them: none is guessed): it is told which when one is chosen.
        state.repo == null && state.cloudSessionId != null -> { status = "Choisis le dépôt de cette session (⎇)"; statusColor = warm; statusBold = true }
        else -> { status = "Prêt"; statusColor = subtle }
    }
    val updateRoom = if (state.storeUpdateReady) avatar + 8f else 0f
    val titleRoom = avail - avatar - 8f - updateRoom
    val title = when {
        needsSetup -> "Autoriser Aiwa ▸"
        state.backend == "missing" -> "Installer le backend ▸"
        needsLogin -> "Connecter Claude ▸"
        else -> fitLabel(state.session, titleRoom - textWidth(" ▾", fontScale * 1.25f), fontScale * 1.25f) + " ▾"
    }
    val statusText = fitLabel(status, titleRoom, fontScale * 0.92f)
    // What a tap on the name and the status line opens: the fix for what is wrong, else the sessions.
    val bandAction: Action = when {
        needsSetup -> actionStartActivity<SetupActivity>()
        state.backend == "missing" -> actionStartActivity<InstallHelpActivity>()
        needsLogin -> actionStartActivity<ClaudeLoginActivity>()
        state.ask != null && state.status != AiwaState.Status.WORKING -> actionStartActivity<AskActivity>()
        state.status == AiwaState.Status.ERROR && state.notice != null -> actionStartActivity<MainActivity>()
        state.repoAccessMissing != null && state.repoAccessMissing == state.repo -> actionStartIntent(Intent(Intent.ACTION_VIEW, Uri.parse(state.githubAppUrl ?: "https://github.com/apps/claude/installations/new")))
        state.needsRepo || state.repo == null -> actionStartActivity<RepoPickerActivity>()
        else -> lockable(locked, actionStartActivity<SessionPickerActivity>())
    }

    // ---- band 2: repository and model, half the width each -----------------
    // The push chip sits at the end of this band, as short as it can be ("main" / "branche"); the repository and the model share the rest.
    val pushText = if (state.pushMain) "main" else "branche"
    val pushRoom = if (hasRepo) GAP + chipWidth(pushText, fontScale) else 0f
    val half = (avail - GAP - pushRoom) / 2f - 20f
    val repoName = state.repo?.substringAfter('/')
    // "+2": two more repositories Claude may work on (see the repository picker).
    val extraSuffix = if (state.extraRepos.isNotEmpty()) " +${state.extraRepos.size}" else ""
    val repoText = "⎇ " + fitLabel(repoName ?: "Choisir un dépôt", half - textWidth("⎇  ▾", fontScale) - textWidth(extraSuffix, fontScale), fontScale) + extraSuffix + " ▾"
    val modelText = fitLabel(modelLabel(state.model), half - textWidth(" ▾", fontScale), fontScale) + " ▾"

    // ---- band 3: Push, Deploy, and the site / Actions ---------------------
    val site = state.siteUrl
    val live = state.siteState == "live"
    // The address is known as soon as a repository is chosen
    // (https://<owner>.github.io/<repo>/): the globe is there once deployment is
    // asked for, or as soon as the address answers.
    val showSite = hasRepo && site != null && (state.deploy != "none" || live)
    val showStore = hasRepo && storeMode
    // In the Store mode nothing is published through GitHub: the round "GitHub Actions" button (the build of this
    // repository) has no meaning there, and gives way to a button that shows the code Claude sent.
    val showCode = showStore
    val showActions = (hasRepo && !storeMode && (publishesFromGithub(state.deploy) || state.ciState != null)) || state.deploy == "aiwa"
    val fixed = (if (showSite) GAP + chipH else 0f) + (if (showStore) GAP + chipH else 0f) + (if (showCode) GAP + chipH else 0f) + (if (showActions) GAP + chipH else 0f) + (if (hasRepo) GAP + chipH else 0f)
    val each = avail - fixed - 20f
    val deployText = fitLabel(deployLabel(state.deploy), each, fontScale)

    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(ImageProvider(if (state.waiting) R.drawable.widget_bg_alert else R.drawable.widget_bg))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
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
            // A newer release of the Store's page is downloaded: this button applies it (it replaces a toast that was gone before it was read).
            if (state.storeUpdateReady) {
                Spacer(GlanceModifier.width(8.dp))
                RoundButton(
                    icon = R.drawable.ic_update,
                    description = "Mise à jour du Store prête : touche pour l'appliquer",
                    background = green,
                    action = actionStartActivity<UpdateStoreActivity>(),
                    diameter = avatar.dp,
                )
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
            Chip(repoText, if (locked) PILL_DIM else pill, if (locked) TEXT_DIM else fg, lockable(locked, actionStartActivity<RepoPickerActivity>()), GlanceModifier.defaultWeight(), alignStart = true, height = chipH.dp, badge = state.repo == null)
            Spacer(GlanceModifier.width(GAP.dp))
            Chip(modelText, if (locked) PILL_DIM else pill, if (locked) TEXT_DIM else fg, lockable(locked, actionStartActivity<ModelPickerActivity>()), GlanceModifier.defaultWeight(), alignStart = true, height = chipH.dp)
            if (hasRepo) {
                Spacer(GlanceModifier.width(GAP.dp))
                // Green = push straight to the main branch; grey = on a branch. A tap switches.
                Chip(pushText, if (locked) (if (state.pushMain) GREEN_DIM else PILL_DIM) else if (state.pushMain) green else pill, if (locked) TEXT_DIM else fg, lockable(locked, actionRunCallback<TogglePushMainCallback>()), GlanceModifier.width(chipWidth(pushText, fontScale).dp), alignStart = true, height = chipH.dp)
            }
        }
        // The buttons of this band appear once a repository is chosen. They are
        // instructions integrated into the conversation — Claude Code does the
        // work itself.
        if (hasRepo) {
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                Chip(deployText, if (locked) (if (state.deploy != "none") GREEN_DIM else PILL_DIM) else if (state.deploy != "none") green else pill, if (locked) TEXT_DIM else fg, lockable(locked, actionStartActivity<DeployPickerActivity>()), GlanceModifier.defaultWeight(), alignStart = true, height = chipH.dp)
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
                // The promotional videos of the creation (VideosActivity): list them, or ask Claude for a new one.
                Spacer(GlanceModifier.width(GAP.dp))
                RoundButton(
                    icon = R.drawable.ic_video,
                    description = "Vidéos promotionnelles de la création",
                    background = pill,
                    action = actionStartActivity<VideosActivity>(),
                    diameter = chipH.dp,
                )
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
            // Before the mic: what can be said (the voice commands), and the switch of the always-on listening. Green while it listens.
            RoundButton(
                icon = R.drawable.ic_voice,
                description = if (state.listening) "Commandes vocales : l'écoute permanente est allumée" else "Commandes vocales et écoute permanente",
                background = if (state.listening) green else pill,
                action = actionStartActivity<VoiceHelpActivity>(),
                diameter = micH.dp,
            )
            Spacer(GlanceModifier.width(GAP.dp))
            Box(
                modifier = GlanceModifier.defaultWeight().height(micH.dp)
                    .background(if (locked) MIC_DIM else micGrey)
                    .cornerRadius((micH / 2).dp)
                    .clickable(lockable(locked, micAction(state.needsRepo, state.backend == "missing"))),
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
                description = if (state.waiting) "Claude attend une réponse : ouvrir la conversation" else if (hasSession) "Ouvrir la conversation dans Claude" else "Ouvrir Claude",
                background = if (state.waiting) alertRed else claudeOrange,
                action = actionStartActivity<OpenClaudeActivity>(),
                diameter = micH.dp,
            )
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
        state.backend == "missing" -> "⚠ À installer"
        state.backend == "starting" -> "⏳ Démarrage"
        state.backend == "down" -> "⚠ Arrêté"
        needsLogin -> "Connecter Claude"
        state.status == AiwaState.Status.WORKING -> "Envoi…"
        else -> state.session
    }
    val modelText = modelLabel(state.model).replace(" · ", "·").take(12) + " ▾"
    val pillText = "● Claude ↗"
    val updateRoom = if (state.storeUpdateReady) 36f + GAP else 0f
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
                    state.backend == "missing" -> actionStartActivity<InstallHelpActivity>()
                    needsLogin -> actionStartActivity<ClaudeLoginActivity>()
                    else -> lockable(locked, actionStartActivity<SessionPickerActivity>())
                },
                GlanceModifier.defaultWeight(), bold = true, alignStart = true,
            )
            Spacer(GlanceModifier.width(GAP.dp))
            RoundButton(
                icon = R.drawable.ic_voice,
                description = if (state.listening) "Commandes vocales : l'écoute permanente est allumée" else "Commandes vocales et écoute permanente",
                background = if (state.listening) green else pill,
                action = actionStartActivity<VoiceHelpActivity>(),
                diameter = 36.dp,
            )
            Spacer(GlanceModifier.width(GAP.dp))
            Box(
                modifier = GlanceModifier.size(40.dp)
                    .background(if (locked) MIC_DIM else micGrey)
                    .cornerRadius(20.dp)
                    .clickable(lockable(locked, micAction(state.needsRepo, state.backend == "missing"))),
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
            if (state.storeUpdateReady) {
                Spacer(GlanceModifier.width(GAP.dp))
                RoundButton(
                    icon = R.drawable.ic_update,
                    description = "Mise à jour du Store prête : touche pour l'appliquer",
                    background = green,
                    action = actionStartActivity<UpdateStoreActivity>(),
                    diameter = 36.dp,
                )
            }
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
            val showSite = hasRepo && site != null && (state.deploy != "none" || live)
            val showStore = hasRepo && storeMode
            // In the Store mode nothing is published through GitHub: the round "GitHub Actions" button (the build of this
            // repository) has no meaning there, and gives way to a button that shows the code Claude sent.
            val showCode = showStore
            val showActions = (hasRepo && !storeMode && (publishesFromGithub(state.deploy) || state.ciState != null)) || state.deploy == "aiwa"
            var others = 0f
            if (hasRepo) others += GAP + chipWidth(pushText, fontScale) + GAP + chipWidth(deployText, fontScale)
            if (showSite) others += GAP + 34f
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
                    Spacer(GlanceModifier.width(GAP.dp))
                    Chip(deployText, if (locked) (if (state.deploy != "none") GREEN_DIM else PILL_DIM) else if (state.deploy != "none") green else pill, if (locked) TEXT_DIM else fg, lockable(locked, actionStartActivity<DeployPickerActivity>()))
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

