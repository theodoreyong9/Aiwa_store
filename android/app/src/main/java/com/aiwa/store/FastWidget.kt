package com.aiwa.store

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The widget in plain views (RemoteViews): the same three tabs as the Glance one, but a tap on a tab only turns a ViewFlipper and recolours the tabs, with no recomposition and no
 * trip through Glance, which made the switch slow. The state is read from AiwaRepository and drawn again whenever it changes (see AiwaApp); the buttons open the same screens.
 */
object FastWidget {
    const val ACTION_TAB = "com.aiwa.store.FW_TAB"
    const val ACTION_KIND = "com.aiwa.store.FW_KIND"
    const val ACTION_BUSY = "com.aiwa.store.FW_BUSY"
    const val ACTION_VOIR = "com.aiwa.store.FW_VOIR"
    private var lastSignature = ""
    /** How each tab looks when it is NOT the open one (its text and colour, from the last full drawing): a tap on a tab only needs these, not the state. */
    private val tabText = mutableMapOf("domain" to "Domaine", "files" to "Files", "build" to "Build", "voir" to "Voir")
    private val tabColor = mutableMapOf("domain" to "taboff", "files" to "taboff", "build" to "taboff", "voir" to "taboff")
    private val tabView = mapOf("domain" to R.id.fw_tab_domain, "files" to R.id.fw_tab_files, "build" to R.id.fw_tab_build, "voir" to R.id.fw_tab_voir)
    private val tabPage = mapOf("domain" to 0, "files" to 1, "build" to 2, "voir" to 3)

    /** The tap on a tab: ONLY the page of the flipper and the two tabs concerned change, with a partial update; nothing of the state is read, nothing is rebuilt. */
    fun switchTab(context: Context, tab: String) {
        val before = WidgetTab.get(context)
        WidgetTab.set(context, tab)
        val ids = ids(context)
        if (ids.isEmpty()) return
        val v = RemoteViews(context.packageName, R.layout.fast_widget)
        v.setDisplayedChild(R.id.fw_flipper, tabPage[tab] ?: 2)
        if (before != tab) v.chip(tabView.getValue(before), tabText.getValue(before), tabColor.getValue(before), TAB_OFF_TEXT)
        v.chip(tabView.getValue(tab), tabText.getValue(tab), "tabon", TAB_ON_TEXT, bold = true)
        try { AppWidgetManager.getInstance(context).partiallyUpdateAppWidget(ids, v) } catch (err: Exception) { render(context, force = true) }
        lastSignature = ""      // the next full drawing is not skipped
    }

    private val BG = mapOf("pill" to R.drawable.fw_pill, "blue" to R.drawable.fw_blue, "green" to R.drawable.fw_green, "red" to R.drawable.fw_red, "dim" to R.drawable.fw_dim, "tabon" to R.drawable.fw_tabon, "violet" to R.drawable.fw_violet, "violetdim" to R.drawable.fw_violetdim, "taboff" to R.drawable.fw_taboff, "orange" to R.drawable.fw_orange)
    private const val TEXT = 0xFFF0F0F5.toInt()
    private const val TEXT_DIM = 0xFF828290.toInt()
    private const val TAB_ON_TEXT = 0xFFFFFFFF.toInt()
    private const val TAB_OFF_TEXT = 0xFFCFD3FF.toInt()

    fun ids(context: Context): IntArray = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, AiwaWidgetReceiver::class.java))

    /** Draws the widget again from the current state; `force` also when nothing visible changed (a tap on a tab, a new widget). */
    fun render(context: Context, force: Boolean = false) {
        val ids = ids(context)
        if (ids.isEmpty()) return
        val state = AiwaRepository.state.value
        val tab = WidgetTab.get(context)
        val kinds = lightKinds(state, context).sortedBy { listOf("documents", "video", "android", "pages", "aiwa").indexOf(it.mode) }
        val signature = listOf(tab, state.repo, state.cloudSessionId, state.status, state.waiting, state.silenceMinutes, state.loopPair, state.listening, state.agent?.enabled, state.agent?.say != null, state.agent?.proposal != null,
            state.agent?.command != null, state.ask != null, state.appUpdateReady, state.attachments, Projects.isAll(context), Projects.nameOf(context, state.repo, state.cloudSessionId), hasTermuxPermission(context),
            kinds.map { it.word + it.found + it.current }, VoirSettings.on(context), VoirSettings.radius(context)).joinToString("|")
        if (!force && signature == lastSignature) return
        lastSignature = signature
        val views = build(context, state, tab, kinds)
        try { AppWidgetManager.getInstance(context).updateAppWidget(ids, views) } catch (err: Exception) { }
    }

    private fun activity(context: Context, code: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(context, code, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun activity(context: Context, code: Int, cls: Class<*>): PendingIntent = activity(context, code, Intent(context, cls))

    private fun broadcast(context: Context, code: Int, action: String, vararg extras: Pair<String, String>): PendingIntent {
        // FLAG_RECEIVER_FOREGROUND: the tap goes to the front of the queue of broadcasts, it does not wait behind the others
        val intent = Intent(context, AiwaWidgetReceiver::class.java).setAction(action).addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        extras.forEach { intent.putExtra(it.first, it.second) }
        return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** A button of the widget is a column (icon above, label under): the colour and the tap are on the column, the words on its label. */
    private val LABEL = mapOf(R.id.fw_tab_domain to R.id.fw_tab_domain_t, R.id.fw_tab_files to R.id.fw_tab_files_t, R.id.fw_tab_build to R.id.fw_tab_build_t, R.id.fw_question to R.id.fw_question_t,
        R.id.fw_project to R.id.fw_project_t, R.id.fw_loops to R.id.fw_loops_t, R.id.fw_settings to R.id.fw_settings_t, R.id.fw_wallet to R.id.fw_wallet_t, R.id.fw_tab_voir to R.id.fw_tab_voir_t, R.id.fw_voir_on to R.id.fw_voir_on_t, R.id.fw_v_link to R.id.fw_v_link_t, R.id.fw_v_near to R.id.fw_v_near_t, R.id.fw_v_qr to R.id.fw_v_qr_t, R.id.fw_v_fav to R.id.fw_v_fav_t, R.id.fw_campaign to R.id.fw_campaign_t, R.id.fw_mcp to R.id.fw_mcp_t, R.id.fw_scenario to R.id.fw_scenario_t,
        R.id.fw_k0 to R.id.fw_k0_t, R.id.fw_k1 to R.id.fw_k1_t, R.id.fw_k2 to R.id.fw_k2_t, R.id.fw_k3 to R.id.fw_k3_t, R.id.fw_k4 to R.id.fw_k4_t)

    private fun RemoteViews.chip(id: Int, text: String?, color: String, textColor: Int = TEXT, click: PendingIntent? = null, bold: Boolean = false) {
        val label = LABEL[id] ?: id
        if (text != null) setTextViewText(label, if (bold) android.text.SpannableString(text).also { it.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, it.length, 0) } else text)
        setInt(id, "setBackgroundResource", BG.getValue(color))
        setTextColor(label, textColor)
        if (click != null) setOnClickPendingIntent(id, click)
    }

    private fun RemoteViews.box(id: Int, color: String, click: PendingIntent) {
        setInt(id, "setBackgroundResource", BG.getValue(color))
        setOnClickPendingIntent(id, click)
    }

    private fun build(context: Context, state: AiwaState, tab: String, kinds: List<LightKind>): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.fast_widget)
        val locked = state.status == AiwaState.Status.WORKING
        val loopOn = state.loopPair.isNotEmpty()
        val allMode = Projects.isAll(context)
        val agentOn = state.agent?.enabled == true
        val agentNeeds = state.agent?.let { it.say != null || it.proposal != null || it.command != null } == true
        val needsSetup = !hasTermuxPermission(context)
        val permanentOn = state.listening && !needsSetup
        v.setInt(R.id.fw_root, "setBackgroundResource", if (state.waiting) R.drawable.widget_bg_alert else R.drawable.widget_bg)
        // ---- the tabs; the question and the update stay in view whatever the tab ----
        val buildBusy = agentOn || permanentOn
        tabText["domain"] = "Domaine" + (if (loopOn) " ●" else ""); tabColor["domain"] = if (loopOn) "green" else "taboff"
        tabText["files"] = "Files"; tabColor["files"] = "taboff"
        tabText["voir"] = "Voir" + (if (VoirSettings.on(context)) " ●" else ""); tabColor["voir"] = if (VoirSettings.on(context)) "green" else "taboff"
        tabText["build"] = "Build" + (if (buildBusy) " ●" else ""); tabColor["build"] = if (state.waiting || state.silenceMinutes > 0 || agentNeeds || needsSetup) "red" else if (buildBusy) "green" else "taboff"
        listOf("domain", "files", "build", "voir").forEachIndexed { i, name ->
            v.chip(tabView.getValue(name), tabText.getValue(name), if (tab == name) "tabon" else tabColor.getValue(name), if (tab == name) TAB_ON_TEXT else TAB_OFF_TEXT, click = broadcast(context, 1 + i, ACTION_TAB, "tab" to name), bold = tab == name)
        }
        v.setViewVisibility(R.id.fw_question, if (state.ask != null) android.view.View.VISIBLE else android.view.View.GONE)
        v.chip(R.id.fw_question, null, "red", click = activity(context, 4, AskActivity::class.java))
        v.setViewVisibility(R.id.fw_update, if (state.appUpdateReady) android.view.View.VISIBLE else android.view.View.GONE)
        v.box(R.id.fw_update, "green", activity(context, 5, InstallUpdateActivity::class.java))
        v.setDisplayedChild(R.id.fw_flipper, when (tab) { "domain" -> 0; "files" -> 1; "voir" -> 3; else -> 2 })
        // ---- Domaine ----
        val projectBad = !allMode && (state.repo == null || (accessNotice(state, 1) != null || state.backend == "missing" || state.claudeLogin == "needed"))
        val projectLabel = if (allMode) "Tous" else (Projects.nameOf(context, state.repo, state.cloudSessionId) ?: "Projet") + " ▾"
        v.chip(R.id.fw_project, projectLabel, if (allMode) "green" else if (locked) "dim" else if (projectBad) "red" else "blue", if (locked) TEXT_DIM else TEXT,
            if (locked) broadcast(context, 6, ACTION_BUSY) else activity(context, 6, ProjectPickerActivity::class.java))
        v.chip(R.id.fw_loops, if (loopOn) "Boucle ${state.loopPair}" else "Boucles", if (loopOn) "green" else "pill", click = activity(context, 7, LoopsActivity::class.java))
        v.chip(R.id.fw_settings, "Réglages", "pill", click = activity(context, 8, SettingsActivity::class.java))
        v.chip(R.id.fw_wallet, "Wallet", "pill", click = activity(context, 9, storePageIntent(context, "wallet", state.repo)))
        // ---- Files ----
        val kindIds = intArrayOf(R.id.fw_k0, R.id.fw_k1, R.id.fw_k2, R.id.fw_k3, R.id.fw_k4)
        kindIds.forEachIndexed { i, id ->
            val kind = kinds.getOrNull(i)
            v.setViewVisibility(id, if (kind == null) android.view.View.GONE else android.view.View.VISIBLE)
            if (kind != null) v.setImageViewResource(intArrayOf(R.id.fw_k0_i, R.id.fw_k1_i, R.id.fw_k2_i, R.id.fw_k3_i, R.id.fw_k4_i)[i], when (kind.mode) { "documents" -> R.drawable.fw_i_docs; "video" -> R.drawable.fw_i_media; "pages" -> R.drawable.fw_i_page; "android" -> R.drawable.fw_i_app; else -> R.drawable.fw_i_contract })
            if (kind != null) v.chip(id, if (kind.mode == "aiwa") "Contrat" else kind.word, if (kind.mode == "pages" || kind.mode == "aiwa") (if (kind.found) "violet" else "violetdim") else if (kind.found) "green" else "dim", if (kind.found) TEXT else TEXT_DIM, broadcast(context, 40 + i, ACTION_KIND, "mode" to kind.mode, "found" to (if (kind.found) "1" else "0")), bold = kind.current)
        }
        v.chip(R.id.fw_mcp, "MCP", "pill", click = activity(context, 21, ExternalActivity::class.java))
        v.chip(R.id.fw_campaign, "Campagne", "blue", click = activity(context, 14, ProposalsActivity::class.java))
        v.chip(R.id.fw_scenario, "Scénario", "pill", click = activity(context, 20, ScenariosActivity::class.java))
        // ---- Build: the robot (the permanent listening on puts the ear in its place), Claude, the ear and the + ----
        if (permanentOn) {
            v.box(R.id.fw_robot, "green", activity(context, 16, ListenToggleActivity::class.java))
            v.setImageViewResource(R.id.fw_robot_icon, R.drawable.fw_i_ear)
            v.setTextViewText(R.id.fw_robot_label, "Écoute permanente")
            v.setViewVisibility(R.id.fw_robot_icon, android.view.View.VISIBLE)
            v.setViewVisibility(R.id.fw_robot_text, android.view.View.GONE)
            v.setViewVisibility(R.id.fw_robot_label, android.view.View.VISIBLE)
        } else {
            v.box(R.id.fw_robot, if (needsSetup || agentNeeds) "red" else if (agentOn) "green" else "pill", if (needsSetup) activity(context, 16, SetupActivity::class.java) else activity(context, 16, AgentToggleActivity::class.java))
            v.setImageViewResource(R.id.fw_robot_icon, R.drawable.fw_i_robot)
            v.setTextViewText(R.id.fw_robot_label, if (agentNeeds) "Il a quelque chose à dire" else if (agentOn) "Super agent · à l'écoute" else "Super agent")
            v.setViewVisibility(R.id.fw_robot_icon, if (needsSetup) android.view.View.GONE else android.view.View.VISIBLE)
            v.setViewVisibility(R.id.fw_robot_text, if (needsSetup) android.view.View.VISIBLE else android.view.View.GONE)
            v.setViewVisibility(R.id.fw_robot_label, if (needsSetup) android.view.View.GONE else android.view.View.VISIBLE)
        }
        v.setTextViewText(R.id.fw_ear_label, if (state.listening) "Écoute active" else "Écoute")
        v.setTextViewText(R.id.fw_plus_label, if (state.attachments > 0) "${state.attachments} jointe(s)" else "Ajouter")
        v.setViewVisibility(R.id.fw_claude, if (state.cloudSessionId != null) android.view.View.VISIBLE else android.view.View.GONE)
        v.box(R.id.fw_claude, if (state.waiting || state.silenceMinutes > 0) "red" else "orange", activity(context, 17, OpenClaudeActivity::class.java))
        v.setViewVisibility(R.id.fw_ear, if (!permanentOn && !agentOn) android.view.View.VISIBLE else android.view.View.GONE)
        v.box(R.id.fw_ear, if (state.listening) "green" else "pill", activity(context, 18, ListenToggleActivity::class.java))
        v.box(R.id.fw_plus, if (state.attachments > 0) "green" else "pill", activity(context, 19, PlusMenuActivity::class.java))
        // ---- Voir: visible or not, how far, and the window of the people near ----
        val voirOn = VoirSettings.on(context)
        v.chip(R.id.fw_voir_on, if (voirOn) "Visible" else "Caché", if (voirOn) "green" else "pill", click = broadcast(context, 50, ACTION_VOIR, "do" to "toggle"))
        listOf(R.id.fw_v_link to "lien", R.id.fw_v_near to "proche", R.id.fw_v_qr to "qr", R.id.fw_v_fav to "favoris").forEachIndexed { i, (id, mode) ->
            v.chip(id, null, if (mode == "proche" && voirOn) "green" else "pill", click = activity(context, 51 + i, voirIntent(context, state, kinds, mode)))
        }
        return v
    }

    /** The window « Voir » (a page of the web app): the links of the Files of the current repository, the reach, and whether they are published. */
    internal fun voirIntent(context: Context, state: AiwaState, kinds: List<LightKind>, mode: String = "proche"): Intent {
        val repo = state.repo
        val links = if (repo == null) emptyList() else voirLinks(repo, kinds)
        val json = links.joinToString(",", "[", "]") { (kind, url) -> "{\"kind\":\"$kind\",\"url\":\"$url\"}" }
        val url = com.aiwa.bridge.STORE_APP_URL + "?page=voir&m=" + mode + "&pub=" + (if (VoirSettings.on(context) && links.isNotEmpty()) "1" else "0") +
            "&repo=" + android.net.Uri.encode(repo ?: "") + "&links=" + android.net.Uri.encode(json)
        return Intent(context, StoreActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra(StoreActivity.EXTRA_URL, url)
    }

    /** The public links of the Files that exist (found = something is there, seen without any token, so public): docs, media, app, page, contract. */
    private fun voirLinks(repo: String, kinds: List<LightKind>): List<Pair<String, String>> {
        val owner = repo.substringBefore('/'); val name = repo.substringAfter('/')
        val page = if (name.equals("$owner.github.io", ignoreCase = true)) "https://$owner.github.io/" else "https://$owner.github.io/$name/"
        return kinds.filter { it.found }.mapNotNull {
            when (it.mode) {
                "documents" -> "docs" to "https://github.com/$repo/tree/HEAD/docs"
                "video" -> "media" to "https://github.com/$repo/releases/tag/videos"
                "android" -> "app" to "https://github.com/$repo/releases"
                "pages" -> "page" to page
                "aiwa" -> "contract" to "https://github.com/$repo/tree/HEAD/aiwa-apps"
                else -> null
            }
        }
    }
}

class AiwaWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        wakeAiwa(context)
        FastWidget.render(context, force = true)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) = FastWidget.render(context, force = true)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            FastWidget.ACTION_TAB -> {
                FastWidget.switchTab(context, intent.getStringExtra("tab") ?: return)
            }
            FastWidget.ACTION_BUSY -> tellBusy(context, toast = true)
            FastWidget.ACTION_VOIR -> {
                when (intent.getStringExtra("do")) {
                    "toggle" -> VoirSettings.setOn(context, !VoirSettings.on(context))
                    "radius" -> VoirSettings.setRadius(context, intent.getStringExtra("r")?.toIntOrNull() ?: return)
                }
                FastWidget.render(context, force = true)
            }
            FastWidget.ACTION_KIND -> {
                val mode = intent.getStringExtra("mode") ?: return
                val found = intent.getStringExtra("found") == "1"
                val pending = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        if (Projects.isAll(context)) context.startActivity(Intent(context, AllActivity::class.java).putExtra("kind", mode).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        else openProduction(context.applicationContext, mode, found)
                    } finally { pending.finish() }
                }
            }
            else -> super.onReceive(context, intent)
        }
    }
}

/** « Voir »: whether the person publishes the links of her Files to the people near (off by default), and how far (0 = 100 m, 1 = 1 km, 2 = 10 km, 3 = the town). */
object VoirSettings {
    private const val FILE = "aiwa_voir"
    fun on(context: Context): Boolean = try { context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("on", false) } catch (err: Exception) { false }
    fun setOn(context: Context, on: Boolean) { try { context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("on", on).apply() } catch (err: Exception) { } }
    fun radius(context: Context): Int = try { context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt("radius", 1).coerceIn(0, 3) } catch (err: Exception) { 1 }
    fun setRadius(context: Context, radius: Int) { try { context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt("radius", radius.coerceIn(0, 3)).apply() } catch (err: Exception) { } }
}
