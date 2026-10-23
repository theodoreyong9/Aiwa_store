package com.aiwa.store

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.aiwa.bridge.LocalClaudeBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The widget in plain views (RemoteViews): the same three tabs as the Glance one, but a tap on a tab only turns a ViewFlipper and recolours the tabs, with no recomposition and no
 * trip through Glance, which made the switch slow. The state is read from AiwaRepository and drawn again whenever it changes (see AiwaApp); the buttons open the same screens.
 */
/** A row of a list shown INSIDE the widget: what it says, whether it is the current one, and what a tap does. */
internal class ListRow(val label: String, val active: Boolean, val run: suspend (Context) -> Unit)

/** The lists shown inside the widget (the tabs give way to a page of three rows with a pager): for now the projects. */
object FastLists {
    @Volatile var current: String? = null
    @Volatile var offset = 0
    const val PAGE = 3

    fun title(): String = when (current) { "projects" -> "Projets"; else -> "" }

    internal fun rows(context: Context, state: AiwaState): List<ListRow> = when (current) {
        "projects" -> {
            val all = Projects.isAll(context)
            val session = state.cloudSessionId ?: "new"
            buildList {
                add(ListRow("⚙ Session, dépôt, nom ▸", false) { c -> c.startActivity(Intent(c, ProjectPickerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
                add(ListRow(if (all) "Tous : activé" else "Tous", all) { c -> Projects.setAll(c, !all) })
                Projects.all(context).forEach { p -> add(ListRow(p.name, !all && p.repo == state.repo && p.session == session) { c -> applyProject(c, LocalClaudeBridge(), p) }) }
            }
        }
        else -> emptyList()
    }

    fun signature(context: Context): String = if (current == null) "" else current + offset + Projects.all(context).joinToString { it.name }
}

object FastWidget {
    const val ACTION_TAB = "com.aiwa.store.FW_TAB"
    const val ACTION_KIND = "com.aiwa.store.FW_KIND"
    const val ACTION_BUSY = "com.aiwa.store.FW_BUSY"
    const val ACTION_LIST = "com.aiwa.store.FW_LIST"
    const val ACTION_BACK = "com.aiwa.store.FW_BACK"
    const val ACTION_PAGE = "com.aiwa.store.FW_PAGE"
    const val ACTION_ROW = "com.aiwa.store.FW_ROW"
    private var lastSignature = ""
    /** How each tab looks when it is NOT the open one (its text and colour, from the last full drawing): a tap on a tab only needs these, not the state. */
    private val tabText = mutableMapOf("domain" to "Domaine", "files" to "Files", "build" to "Build")
    private val tabColor = mutableMapOf("domain" to "taboff", "files" to "taboff", "build" to "taboff")
    private val tabView = mapOf("domain" to R.id.fw_tab_domain, "files" to R.id.fw_tab_files, "build" to R.id.fw_tab_build)
    private val tabPage = mapOf("domain" to 0, "files" to 1, "build" to 2)

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

    private val BG = mapOf("pill" to R.drawable.fw_pill, "blue" to R.drawable.fw_blue, "green" to R.drawable.fw_green, "red" to R.drawable.fw_red, "dim" to R.drawable.fw_dim, "tabon" to R.drawable.fw_tabon, "taboff" to R.drawable.fw_taboff, "orange" to R.drawable.fw_orange)
    private const val TEXT = 0xFFF0F0F5.toInt()
    private const val TEXT_DIM = 0xFF828290.toInt()
    private const val TAB_ON_TEXT = 0xFF1C2250.toInt()
    private const val TAB_OFF_TEXT = 0xFFCFD3FF.toInt()

    fun ids(context: Context): IntArray = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, AiwaWidgetReceiver::class.java))

    /** Draws the widget again from the current state; `force` also when nothing visible changed (a tap on a tab, a new widget). */
    fun render(context: Context, force: Boolean = false) {
        val ids = ids(context)
        if (ids.isEmpty()) return
        val state = AiwaRepository.state.value
        val tab = WidgetTab.get(context)
        val kinds = lightKinds(state, context).filter { it.mode != "aiwa" }
        val signature = listOf(tab, state.repo, state.cloudSessionId, state.status, state.waiting, state.silenceMinutes, state.loopPair, state.listening, state.agent?.enabled, state.agent?.say != null, state.agent?.proposal != null,
            state.agent?.command != null, state.ask != null, state.appUpdateReady, state.attachments, Projects.isAll(context), Projects.nameOf(context, state.repo, state.cloudSessionId), hasTermuxPermission(context),
            kinds.map { it.word + it.found + it.current }, FastLists.signature(context)).joinToString("|")
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

    private fun RemoteViews.chip(id: Int, text: String?, color: String, textColor: Int = TEXT, click: PendingIntent? = null, bold: Boolean = false) {
        if (text != null) setTextViewText(id, if (bold) android.text.SpannableString(text).also { it.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, it.length, 0) } else text)
        setInt(id, "setBackgroundResource", BG.getValue(color))
        setTextColor(id, textColor)
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
        tabText["build"] = "Build" + (if (buildBusy) " ●" else ""); tabColor["build"] = if (state.waiting || state.silenceMinutes > 0 || agentNeeds || needsSetup) "red" else if (buildBusy) "green" else "taboff"
        listOf("domain", "files", "build").forEachIndexed { i, name ->
            v.chip(tabView.getValue(name), tabText.getValue(name), if (tab == name) "tabon" else tabColor.getValue(name), if (tab == name) TAB_ON_TEXT else TAB_OFF_TEXT, click = broadcast(context, 1 + i, ACTION_TAB, "tab" to name), bold = tab == name)
        }
        v.setViewVisibility(R.id.fw_question, if (state.ask != null) android.view.View.VISIBLE else android.view.View.GONE)
        v.chip(R.id.fw_question, null, "red", click = activity(context, 4, AskActivity::class.java))
        v.setViewVisibility(R.id.fw_update, if (state.appUpdateReady) android.view.View.VISIBLE else android.view.View.GONE)
        v.box(R.id.fw_update, "green", activity(context, 5, InstallUpdateActivity::class.java))
        v.setDisplayedChild(R.id.fw_flipper, when (tab) { "domain" -> 0; "files" -> 1; else -> 2 })
        // ---- Domaine ----
        val projectBad = !allMode && (state.repo == null || (accessNotice(state, 1) != null || state.backend == "missing" || state.claudeLogin == "needed"))
        val projectLabel = if (allMode) "Tous" else (Projects.nameOf(context, state.repo, state.cloudSessionId) ?: "Projet") + " ▾"
        v.chip(R.id.fw_project, projectLabel, if (allMode) "green" else if (locked) "dim" else if (projectBad) "red" else "blue", if (locked) TEXT_DIM else TEXT,
            if (locked) broadcast(context, 6, ACTION_BUSY) else broadcast(context, 6, ACTION_LIST, "id" to "projects"))
        v.chip(R.id.fw_loops, if (loopOn) "Boucle ${state.loopPair}" else "Boucles", if (loopOn) "green" else "pill", click = activity(context, 7, LoopsActivity::class.java))
        v.chip(R.id.fw_settings, "Réglages", "pill", click = activity(context, 8, SettingsActivity::class.java))
        v.chip(R.id.fw_wallet, "Wallet", "pill", click = activity(context, 9, storePageIntent(context, "wallet", state.repo)))
        // ---- Files ----
        val kindIds = intArrayOf(R.id.fw_k0, R.id.fw_k1, R.id.fw_k2, R.id.fw_k3)
        kindIds.forEachIndexed { i, id ->
            val kind = kinds.getOrNull(i)
            v.setViewVisibility(id, if (kind == null) android.view.View.GONE else android.view.View.VISIBLE)
            if (kind != null) v.chip(id, kind.word, if (kind.found) "green" else "dim", if (kind.found) TEXT else TEXT_DIM, broadcast(context, 10 + i, ACTION_KIND, "mode" to kind.mode, "found" to (if (kind.found) "1" else "0")), bold = kind.current)
        }
        v.chip(R.id.fw_campaign, "Campagne", "blue", click = activity(context, 14, ProposalsActivity::class.java))
        v.chip(R.id.fw_store, "Store", "pill", click = activity(context, 15, storePageIntent(context, "store", state.repo)))
        // ---- Build: the robot (the permanent listening on puts the ear in its place), Claude, the ear and the + ----
        if (permanentOn) {
            v.box(R.id.fw_robot, "green", activity(context, 16, ListenToggleActivity::class.java))
            v.setImageViewResource(R.id.fw_robot_icon, R.drawable.ic_ear)
            v.setTextViewText(R.id.fw_robot_label, "Écoute permanente")
            v.setViewVisibility(R.id.fw_robot_icon, android.view.View.VISIBLE)
            v.setViewVisibility(R.id.fw_robot_text, android.view.View.GONE)
            v.setViewVisibility(R.id.fw_robot_label, android.view.View.VISIBLE)
        } else {
            v.box(R.id.fw_robot, if (needsSetup || agentNeeds) "red" else if (agentOn) "green" else "pill", if (needsSetup) activity(context, 16, SetupActivity::class.java) else activity(context, 16, AgentToggleActivity::class.java))
            v.setImageViewResource(R.id.fw_robot_icon, R.drawable.ic_agent)
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
        // ---- a list open inside the widget: the tabs give way to it (the page of the flipper after the three tabs) ----
        if (FastLists.current != null) {
            val rows = FastLists.rows(context, state)
            val offset = FastLists.offset.coerceIn(0, maxOf(0, rows.size - 1) / FastLists.PAGE * FastLists.PAGE)
            v.setViewVisibility(R.id.fw_top, android.view.View.GONE)
            v.setDisplayedChild(R.id.fw_flipper, 3)
            v.chip(R.id.fw_back, "‹ Retour", "pill", click = broadcast(context, 30, ACTION_BACK))
            v.chip(R.id.fw_list_title, FastLists.title() + (if (rows.size > FastLists.PAGE) "  ${offset / FastLists.PAGE + 1}/${(rows.size + FastLists.PAGE - 1) / FastLists.PAGE}" else ""), "taboff", TAB_OFF_TEXT)
            val paged = rows.size > FastLists.PAGE
            v.setViewVisibility(R.id.fw_prev, if (paged) android.view.View.VISIBLE else android.view.View.GONE)
            v.setViewVisibility(R.id.fw_next, if (paged) android.view.View.VISIBLE else android.view.View.GONE)
            v.chip(R.id.fw_prev, null, if (offset > 0) "pill" else "dim", if (offset > 0) TEXT else TEXT_DIM, broadcast(context, 31, ACTION_PAGE, "d" to "-1"))
            v.chip(R.id.fw_next, null, if (offset + FastLists.PAGE < rows.size) "pill" else "dim", if (offset + FastLists.PAGE < rows.size) TEXT else TEXT_DIM, broadcast(context, 32, ACTION_PAGE, "d" to "1"))
            intArrayOf(R.id.fw_row0, R.id.fw_row1, R.id.fw_row2).forEachIndexed { i, id ->
                val row = rows.getOrNull(offset + i)
                v.setViewVisibility(id, if (row == null) android.view.View.INVISIBLE else android.view.View.VISIBLE)
                if (row != null) v.chip(id, row.label, if (row.active) "blue" else "pill", click = broadcast(context, 33 + i, ACTION_ROW, "i" to (offset + i).toString()), bold = row.active)
            }
        } else v.setViewVisibility(R.id.fw_top, android.view.View.VISIBLE)
        return v
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
            FastWidget.ACTION_LIST -> { FastLists.current = intent.getStringExtra("id"); FastLists.offset = 0; FastWidget.render(context, force = true) }
            FastWidget.ACTION_BACK -> { FastLists.current = null; FastWidget.render(context, force = true) }
            FastWidget.ACTION_PAGE -> {
                FastLists.offset = maxOf(0, FastLists.offset + (intent.getStringExtra("d")?.toIntOrNull() ?: 0) * FastLists.PAGE)
                FastWidget.render(context, force = true)
            }
            FastWidget.ACTION_ROW -> {
                val index = intent.getStringExtra("i")?.toIntOrNull() ?: return
                val pending = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        FastLists.rows(context, AiwaRepository.state.value).getOrNull(index)?.let { row ->
                            FastLists.current = null      // the list closes on the choice, the widget shows the result
                            FastWidget.render(context, force = true)
                            row.run(context.applicationContext)
                        }
                    } finally { FastWidget.render(context, force = true); pending.finish() }
                }
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
