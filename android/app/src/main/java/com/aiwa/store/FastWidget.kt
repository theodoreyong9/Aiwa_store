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
 * The widget: five pictures drawn by WidgetArt (one per tab, the card included) under invisible touch zones (res/layout/fast_widget.xml). A tap on a tab only turns the two ViewFlippers (a partial
 * update): nothing is read, nothing is drawn again, so it is instant. The state is read from AiwaRepository and the pictures are drawn again whenever it changes (see AiwaApp).
 */
object FastWidget {
    const val ACTION_TAB = "com.aiwa.store.FW_TAB"
    const val ACTION_KIND = "com.aiwa.store.FW_KIND"
    const val ACTION_BUSY = "com.aiwa.store.FW_BUSY"
    const val ACTION_VOIR = "com.aiwa.store.FW_VOIR"
    private var lastSignature = ""
    private val TABS = listOf("domain", "files", "build", "voir")
    private val IMG = intArrayOf(R.id.fw_img0, R.id.fw_img1, R.id.fw_img2, R.id.fw_img3)

    /** The tap on a tab: ONLY the page of the two flippers changes, with a partial update. */
    fun switchTab(context: Context, tab: String) {
        WidgetTab.set(context, tab)
        val ids = ids(context)
        if (ids.isEmpty()) return
        val v = RemoteViews(context.packageName, R.layout.fast_widget)
        val page = TABS.indexOf(tab).coerceAtLeast(0)
        v.setDisplayedChild(R.id.fw_img_flip, page)
        v.setDisplayedChild(R.id.fw_zone_flip, page)
        try { AppWidgetManager.getInstance(context).partiallyUpdateAppWidget(ids, v) } catch (err: Exception) { render(context, force = true) }
        lastSignature = ""      // the next full drawing is not skipped
    }

    fun ids(context: Context): IntArray = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, AiwaWidgetReceiver::class.java))

    /** Draws the widget again from the current state; `force` also when nothing visible changed (a new widget, a new size). */
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
        val views = try { build(context, state, tab, kinds) } catch (err: Throwable) { return }
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

    /** One cell of a page: what is drawn, its share of the row, and what a tap does (none = nothing). */
    private class Cell(val btn: WidgetArt.Btn, val weight: Float, val click: PendingIntent?)

    private fun id(context: Context, name: String): Int = context.resources.getIdentifier(name, "id", context.packageName)

    private fun build(context: Context, state: AiwaState, tab: String, kinds: List<LightKind>): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.fast_widget)
        val locked = state.status == AiwaState.Status.WORKING
        val loopOn = state.loopPair.isNotEmpty()
        val allMode = Projects.isAll(context)
        val agentOn = state.agent?.enabled == true
        val agentNeeds = state.agent?.let { it.say != null || it.proposal != null || it.command != null } == true
        val needsSetup = !hasTermuxPermission(context)
        val permanentOn = state.listening && !needsSetup
        val voirOn = VoirSettings.on(context)
        val alert = state.waiting
        val buildBusy = agentOn || permanentOn
        // ---- the tabs (the question and the update join the strip only when there is one) ----
        val tabs = mutableListOf(
            WidgetArt.Tab(R.drawable.fw_i_domain, "Domaine" + (if (loopOn) " ●" else ""), if (loopOn) "green" else "grey"),
            WidgetArt.Tab(R.drawable.fw_i_files, "Files"),
            WidgetArt.Tab(R.drawable.fw_i_build, "Build" + (if (buildBusy) " ●" else ""), if (state.waiting || state.silenceMinutes > 0 || agentNeeds || needsSetup) "red" else if (buildBusy) "green" else "grey"),
            WidgetArt.Tab(R.drawable.fw_i_voir, "Voir" + (if (voirOn) " ●" else ""), if (voirOn) "green" else "grey"))
        val ask = state.ask != null
        val upd = state.appUpdateReady
        if (ask) tabs += WidgetArt.Tab(R.drawable.fw_i_question, "Question", "red")
        if (upd) tabs += WidgetArt.Tab(R.drawable.fw_i_update, "Mise à jour", "green")
        TABS.forEachIndexed { i, name -> v.setOnClickPendingIntent(id(context, "t_$name"), broadcast(context, 1 + i, ACTION_TAB, "tab" to name)) }
        v.setViewVisibility(R.id.t_ask, if (ask) android.view.View.VISIBLE else android.view.View.GONE)
        v.setViewVisibility(R.id.t_upd, if (upd) android.view.View.VISIBLE else android.view.View.GONE)
        v.setOnClickPendingIntent(R.id.t_ask, activity(context, 4, AskActivity::class.java))
        v.setOnClickPendingIntent(R.id.t_upd, activity(context, 5, InstallUpdateActivity::class.java))
        // ---- Domaine ----
        val projectBad = !allMode && (state.repo == null || (accessNotice(state, 1) != null || state.backend == "missing" || state.claudeLogin == "needed"))
        val projectLabel = if (allMode) "Tous" else (Projects.nameOf(context, state.repo, state.cloudSessionId) ?: "Projet") + " ▾"
        val project = WidgetArt.Btn(R.drawable.fw_i_project, projectLabel, if (allMode) "green" else if (projectBad) "red" else "blue", enabled = !locked)
        val projectTap = if (locked) broadcast(context, 6, ACTION_BUSY) else activity(context, 6, ProjectPickerActivity::class.java)
        val domain = listOf(
            listOf(Cell(WidgetArt.Btn(R.drawable.fw_i_loops, if (loopOn) "Boucle ${state.loopPair}" else "Boucles", if (loopOn) "green" else "grey"), 1f, activity(context, 7, LoopsActivity::class.java)),
                Cell(WidgetArt.Btn(R.drawable.fw_i_wallet, "Wallet"), 1f, activity(context, 9, storePageIntent(context, "wallet", state.repo)))),
            listOf(Cell(WidgetArt.Btn(R.drawable.fw_i_settings, "Réglages"), 1f, activity(context, 8, SettingsActivity::class.java)),
                Cell(WidgetArt.Btn(R.drawable.fw_i_mcp, "MCP"), 1f, activity(context, 21, ExternalActivity::class.java)),
                Cell(WidgetArt.Btn(R.drawable.fw_i_cast, "Cast TV"), 1f, activity(context, 22, CastActivity::class.java))))
        // ---- Files: the project and the campaigns, then one cell per kind of file found ----
        val kindCells = kinds.take(5).mapIndexed { i, kind ->
            val icon = when (kind.mode) { "documents" -> R.drawable.fw_i_docs; "video" -> R.drawable.fw_i_media; "pages" -> R.drawable.fw_i_page; "android" -> R.drawable.fw_i_app; else -> R.drawable.fw_i_contract }
            val tone = if (kind.mode == "pages" || kind.mode == "aiwa") "violet" else if (kind.found) "green" else "grey"
            Cell(WidgetArt.Btn(icon, if (kind.mode == "aiwa") "Contrat" else kind.word, tone, enabled = kind.found || kind.mode == "pages" || kind.mode == "aiwa"), 1f,
                broadcast(context, 40 + i, ACTION_KIND, "mode" to kind.mode, "found" to (if (kind.found) "1" else "0")))
        }.ifEmpty { listOf(Cell(WidgetArt.Btn(R.drawable.fw_i_files, "Aucun fichier", enabled = false), 1f, null)) }
        val files = listOf(
            listOf(Cell(project, 2f, projectTap), Cell(WidgetArt.Btn(R.drawable.fw_i_campaign, "Campagnes", "blue"), 1f, activity(context, 14, ProposalsActivity::class.java))),
            kindCells)
        // ---- Build: the robot (the permanent listening on puts the ear in its place), Claude, then the ear, the + and the scenarios ----
        val robot = when {
            permanentOn -> WidgetArt.Btn(R.drawable.fw_i_ear, "Écoute permanente", "green")
            needsSetup -> WidgetArt.Btn(R.drawable.fw_i_robot, "Autoriser Aiwa", "red")
            agentNeeds -> WidgetArt.Btn(R.drawable.fw_i_robot, "Il a quelque chose à dire", "red")
            agentOn -> WidgetArt.Btn(R.drawable.fw_i_robot, "Super agent · à l'écoute", "green")
            else -> WidgetArt.Btn(R.drawable.fw_i_robot, "Super agent")
        }
        val robotTap = if (permanentOn) activity(context, 70, ListenToggleActivity::class.java) else if (needsSetup) activity(context, 70, SetupActivity::class.java) else activity(context, 70, AgentToggleActivity::class.java)
        val hasClaude = state.cloudSessionId != null
        val earOk = !permanentOn && !agentOn
        val buildPage = listOf(
            listOf(Cell(robot, 3f, robotTap),
                Cell(WidgetArt.Btn(R.drawable.fw_i_claude, "Claude", if (state.waiting || state.silenceMinutes > 0) "red" else "orange", enabled = hasClaude), 1f, if (hasClaude) activity(context, 71, OpenClaudeActivity::class.java) else null)),
            listOf(Cell(WidgetArt.Btn(R.drawable.fw_i_ear, if (state.listening) "Écoute active" else "Écoute", if (state.listening) "green" else "grey", enabled = earOk), 1f, if (earOk) activity(context, 72, ListenToggleActivity::class.java) else null),
                Cell(WidgetArt.Btn(R.drawable.fw_i_plus, if (state.attachments > 0) "${state.attachments} jointe(s)" else "Ajouter", if (state.attachments > 0) "green" else "grey"), 1f, activity(context, 73, PlusMenuActivity::class.java)),
                Cell(WidgetArt.Btn(R.drawable.fw_i_scenario, "Scénario", "violet"), 1f, activity(context, 74, ScenariosActivity::class.java))))
        // ---- Voir: visible or not, then the four ways to see ----
        val voir = listOf(
            listOf(Cell(WidgetArt.Btn(R.drawable.fw_i_voir, if (voirOn) "Visible" else "Caché", if (voirOn) "green" else "grey"), 1f, broadcast(context, 50, ACTION_VOIR, "do" to "toggle"))),
            listOf("lien" to (R.drawable.fw_i_link to "Lien"), "proche" to (R.drawable.fw_i_pin to "Proximité"), "qr" to (R.drawable.fw_i_qr to "QR code"), "favoris" to (R.drawable.fw_i_star to "Favoris")).mapIndexed { i, (mode, il) ->
                Cell(WidgetArt.Btn(il.first, il.second, if (mode == "proche" && voirOn) "green" else "grey"), 1f, activity(context, 51 + i, voirIntent(context, state, kinds, mode))) })
        val pages = listOf(domain, files, buildPage, voir)
        // ---- the pictures and the touch zones ----
        val (pw, ph) = sizePx(context)
        val letter = listOf("d", "f", "b", "v")
        pages.forEachIndexed { p, rows ->
            v.setImageViewBitmap(IMG[p], WidgetArt.full(context, pw, ph, alert, tabs, p, rows.map { row -> row.map { it.btn to it.weight } }))
            rows.forEachIndexed { r, row ->
                row.forEachIndexed { c, cell -> id(context, "z_${letter[p]}_${r}_$c").let { zid -> v.setViewVisibility(zid, android.view.View.VISIBLE); cell.click?.let { v.setOnClickPendingIntent(zid, it) } } }
            }
        }
        // a zone with no cell under it (fewer kinds of files than five) must not take a share of its row
        for (c in kindCells.size until 5) v.setViewVisibility(id(context, "z_f_1_$c"), android.view.View.GONE)
        val page = TABS.indexOf(tab).coerceAtLeast(0)
        v.setDisplayedChild(R.id.fw_img_flip, page)
        v.setDisplayedChild(R.id.fw_zone_flip, page)
        return v
    }

    /** The size, in pixels, of the pictures: the size of the widget (from its options) and never so many pixels that five of them exceed what a RemoteViews may carry (a fraction of the screen's memory). */
    private fun sizePx(context: Context): Pair<Int, Int> {
        val dm = context.resources.displayMetrics
        val d = dm.density
        var w = 330f; var h = 126f
        try {
            val id = ids(context).firstOrNull()
            if (id != null) {
                val o = AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
                val minW = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0); val maxH = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
                if (minW > 0) w = minW.toFloat()
                if (maxH > 0) h = maxH.toFloat()
            }
        } catch (err: Exception) { /* the default size */ }
        val budget = dm.widthPixels.toFloat() * dm.heightPixels * 4f * 1.5f * 0.7f / (5f * 4f)   // pixels per picture
        val k = minOf(1f, 900f / (w * d), Math.sqrt((budget / (w * d * h * d)).toDouble()).toFloat())
        return maxOf(150, (w * d * k).toInt()) to maxOf(60, (h * d * k).toInt())
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
