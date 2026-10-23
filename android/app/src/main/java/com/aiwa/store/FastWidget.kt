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
    private var lastSignature = ""

    private val BG = mapOf("pill" to R.drawable.fw_pill, "blue" to R.drawable.fw_blue, "green" to R.drawable.fw_green, "red" to R.drawable.fw_red, "dim" to R.drawable.fw_dim, "orange" to R.drawable.fw_orange)
    private const val TEXT = 0xFFF0F0F5.toInt()
    private const val TEXT_DIM = 0xFF828290.toInt()

    fun ids(context: Context): IntArray = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, FastWidgetReceiver::class.java))

    /** Draws the widget again from the current state; `force` also when nothing visible changed (a tap on a tab, a new widget). */
    fun render(context: Context, force: Boolean = false) {
        val ids = ids(context)
        if (ids.isEmpty()) return
        val state = AiwaRepository.state.value
        val tab = WidgetTab.get(context)
        val kinds = lightKinds(state, context).filter { it.mode != "aiwa" }
        val signature = listOf(tab, state.repo, state.cloudSessionId, state.status, state.waiting, state.silenceMinutes, state.loopPair, state.listening, state.agent?.enabled, state.agent?.say != null, state.agent?.proposal != null,
            state.agent?.command != null, state.ask != null, state.appUpdateReady, state.attachments, Projects.isAll(context), Projects.nameOf(context, state.repo, state.cloudSessionId), hasTermuxPermission(context),
            kinds.map { it.word + it.found + it.current }).joinToString("|")
        if (!force && signature == lastSignature) return
        lastSignature = signature
        val views = build(context, state, tab, kinds)
        try { AppWidgetManager.getInstance(context).updateAppWidget(ids, views) } catch (err: Exception) { }
    }

    private fun activity(context: Context, code: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(context, code, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun activity(context: Context, code: Int, cls: Class<*>): PendingIntent = activity(context, code, Intent(context, cls))

    private fun broadcast(context: Context, code: Int, action: String, vararg extras: Pair<String, String>): PendingIntent {
        val intent = Intent(context, FastWidgetReceiver::class.java).setAction(action)
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
        v.chip(R.id.fw_tab_domain, "Domaine" + (if (loopOn) " ●" else ""), if (tab == "domain") "blue" else if (loopOn) "green" else "pill", click = broadcast(context, 1, ACTION_TAB, "tab" to "domain"), bold = tab == "domain")
        v.chip(R.id.fw_tab_files, "Files", if (tab == "files") "blue" else "pill", click = broadcast(context, 2, ACTION_TAB, "tab" to "files"), bold = tab == "files")
        v.chip(R.id.fw_tab_build, "Build" + (if (buildBusy) " ●" else ""),
            if (tab == "build") "blue" else if (state.waiting || state.silenceMinutes > 0 || agentNeeds || needsSetup) "red" else if (buildBusy) "green" else "pill", click = broadcast(context, 3, ACTION_TAB, "tab" to "build"), bold = tab == "build")
        v.setViewVisibility(R.id.fw_question, if (state.ask != null) android.view.View.VISIBLE else android.view.View.GONE)
        v.chip(R.id.fw_question, null, "red", click = activity(context, 4, AskActivity::class.java))
        v.setViewVisibility(R.id.fw_update, if (state.appUpdateReady) android.view.View.VISIBLE else android.view.View.GONE)
        v.box(R.id.fw_update, "green", activity(context, 5, InstallUpdateActivity::class.java))
        v.setDisplayedChild(R.id.fw_flipper, when (tab) { "domain" -> 0; "files" -> 1; else -> 2 })
        // ---- Domaine ----
        val projectBad = !allMode && (state.repo == null || bandInfo(state, 200f).title.endsWith("▸"))
        val projectLabel = if (allMode) "Tous" else (Projects.nameOf(context, state.repo, state.cloudSessionId) ?: "Projet") + " ▾"
        v.chip(R.id.fw_project, projectLabel, if (allMode) "green" else if (locked) "dim" else if (projectBad) "red" else "blue", if (locked) TEXT_DIM else TEXT,
            if (locked) broadcast(context, 6, ACTION_BUSY) else activity(context, 6, ProjectPickerActivity::class.java))
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
            v.setViewVisibility(R.id.fw_robot_icon, android.view.View.VISIBLE)
            v.setViewVisibility(R.id.fw_robot_text, android.view.View.GONE)
        } else {
            v.box(R.id.fw_robot, if (needsSetup || agentNeeds) "red" else if (agentOn) "green" else "pill", if (needsSetup) activity(context, 16, SetupActivity::class.java) else activity(context, 16, AgentToggleActivity::class.java))
            v.setImageViewResource(R.id.fw_robot_icon, R.drawable.ic_agent)
            v.setViewVisibility(R.id.fw_robot_icon, if (needsSetup) android.view.View.GONE else android.view.View.VISIBLE)
            v.setViewVisibility(R.id.fw_robot_text, if (needsSetup) android.view.View.VISIBLE else android.view.View.GONE)
        }
        v.setViewVisibility(R.id.fw_claude, if (state.cloudSessionId != null) android.view.View.VISIBLE else android.view.View.GONE)
        v.box(R.id.fw_claude, if (state.waiting || state.silenceMinutes > 0) "red" else "orange", activity(context, 17, OpenClaudeActivity::class.java))
        v.setViewVisibility(R.id.fw_ear, if (!permanentOn && !agentOn) android.view.View.VISIBLE else android.view.View.GONE)
        v.box(R.id.fw_ear, if (state.listening) "green" else "pill", activity(context, 18, ListenToggleActivity::class.java))
        v.box(R.id.fw_plus, if (state.attachments > 0) "green" else "pill", activity(context, 19, PlusMenuActivity::class.java))
        return v
    }
}

class FastWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        wakeAiwa(context)
        FastWidget.render(context, force = true)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) = FastWidget.render(context, force = true)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            FastWidget.ACTION_TAB -> {
                WidgetTab.set(context, intent.getStringExtra("tab") ?: return)
                FastWidget.render(context, force = true)
            }
            FastWidget.ACTION_BUSY -> tellBusy(context, toast = true)
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
