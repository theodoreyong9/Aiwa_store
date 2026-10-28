package com.aiwa.store

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.aiwa.bridge.LocalClaudeBridge
import com.aiwa.bridge.Scenario
import com.aiwa.bridge.ScenarioWord
import org.json.JSONObject

/**
 * The home page and the page of a profile (`Profile/index.html`) are the SAME kind of page: it draws what it wants, and wherever it puts a slot (`data-aiwa-slot="build"`, `data-aiwa-button="docs"`) the application puts ITS buttons,
 * the widget's buttons (assets/home/aiwa-home.js does the drawing). The buttons act on the account of whoever looks: on the page of someone else, the design and everything written in the page are hers, and the buttons
 * of the slots are still the visitor's. A press reaches the phone by ONE channel, a web message listener (never a Java object, which every frame would see), and only a press of the main frame counts.
 * The pages of the others run no script of their own (see lockScripts); the runtime is put in by the application, outside of the page's reach.
 */
object HomeRuntime {
    private var cached: String? = null

    /** The runtime, read once from the assets. */
    fun script(context: Context): String {
        cached?.let { return it }
        val text = try { context.assets.open("home/aiwa-home.js").use { String(it.readBytes(), Charsets.UTF_8) } } catch (err: Exception) { "" }
        cached = text
        return text
    }

    /** The home of the application, for the person who has no profile page yet: every button, and a word that says how to make her own (the page replaces __WORD__). */
    fun defaultPage(context: Context): String = try { context.assets.open("home/default.html").use { String(it.readBytes(), Charsets.UTF_8) } } catch (err: Exception) { "" }

    /**
     * The channel from the page to the phone: a message {"cmd":"press","id":"docs"} or {"cmd":"scenario","scenario":{…}} (a scenario of the page, launched in the account of whoever looks).
     * `own`: the page is the person's own; `source` says whose page it is (a repository « owner/name »).
     */
    fun attach(web: WebView, own: Boolean, source: () -> String?) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        try {
            WebViewCompat.addWebMessageListener(web, "AiwaHomeHost", setOf("*")) { _, message: WebMessageCompat, _, isMainFrame, _ ->
                if (!isMainFrame) return@addWebMessageListener
                val json = try { JSONObject(message.data ?: return@addWebMessageListener) } catch (err: Exception) { return@addWebMessageListener }
                when (json.optString("cmd")) {
                    "press" -> HomeActions.press(web.context, json.optString("id"))
                    "scenario" -> json.optJSONObject("scenario")?.let { HomeActions.scenario(web.context, it, source(), own) }
                }
            }
        } catch (err: Exception) { /* a second attach on the same view */ }
    }

    /** Puts the runtime in the page (once it is loaded) and tells it what the buttons show now. */
    fun inject(web: WebView) {
        val code = script(web.context)
        if (code.isEmpty()) return
        web.evaluateJavascript(code, null)
        pushState(web)
        pump(web)
    }

    fun pushState(web: WebView) {
        val json = try { HomeActions.state(web.context).toString() } catch (err: Exception) { "{}" }
        web.evaluateJavascript("(window.aiwa&&window.aiwa.setState)&&window.aiwa.setState($json)", null)
    }

    /** What the buttons show (the agent listening, the project, Visible/Caché…) is sent again every few seconds, while the page is on the screen. */
    private fun pump(web: WebView) {
        if (web.getTag(R.id.home_pump) == true) return
        web.setTag(R.id.home_pump, true)
        val tick = object : Runnable {
            override fun run() {
                if (web.getTag(R.id.home_pump) != true) return
                if (web.isAttachedToWindow) pushState(web)
                web.postDelayed(this, 2500)
            }
        }
        web.postDelayed(tick, 2500)
    }

    /** To be called when the view is let go: the pump stops. */
    fun release(web: WebView) { web.setTag(R.id.home_pump, false) }

    /**
     * The page of someone else: its scripts, its frames and its plugins are forbidden (a policy put first in the page, which the page cannot remove), so that only the design and the words of the page count
     * and a page cannot press a button, read the state of the visitor or send anything out. The runtime of the application is not under this rule (a script run by the application itself).
     */
    fun lockScripts(html: String): String {
        val policy = "<meta http-equiv=\"Content-Security-Policy\" content=\"script-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'\">"
        val head = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html)
        if (head != null) return html.substring(0, head.range.last + 1) + policy + html.substring(head.range.last + 1)
        val root = Regex("<html[^>]*>", RegexOption.IGNORE_CASE).find(html)
        if (root != null) return html.substring(0, root.range.last + 1) + "<head>" + policy + "</head>" + html.substring(root.range.last + 1)
        val doctype = Regex("<!doctype[^>]*>", RegexOption.IGNORE_CASE).find(html)
        if (doctype != null) return html.substring(0, doctype.range.last + 1) + policy + html.substring(doctype.range.last + 1)
        return policy + html
    }

    /** What a page may open: only https, mail, phone and message links leave for the app that handles them. */
    fun openExternal(context: Context, request: WebResourceRequest): Boolean {
        if (request.url.scheme in setOf("https", "mailto", "tel", "sms")) try { context.startActivity(Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (err: Exception) { }
        return true
    }
}

/** What each button of the catalogue does (the same as the widget's cell, same activities) and what it shows now. */
object HomeActions {
    private fun go(context: Context, cls: Class<*>) { context.startActivity(Intent(context, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    private fun kindMode(id: String): String? = when (id) { "docs" -> "documents"; "media" -> "video"; "app" -> "android"; "page" -> "pages"; "contract" -> "aiwa"; else -> null }

    fun press(context: Context, id: String) {
        val state = AiwaRepository.state.value
        val kinds = lightKinds(state, context)
        val needsSetup = !hasTermuxPermission(context)
        val permanentOn = state.listening && !needsSetup
        val agentOn = state.agent?.enabled == true
        when (id) {
            "project" -> if (state.status == AiwaState.Status.WORKING) tellBusy(context, toast = true) else go(context, ProjectPickerActivity::class.java)
            "loops" -> go(context, LoopsActivity::class.java)
            "wallet" -> context.startActivity(storePageIntent(context, "wallet", state.repo))
            "settings" -> go(context, SettingsActivity::class.java)
            "mcp" -> go(context, ExternalActivity::class.java)
            "cast" -> go(context, CastActivity::class.java)
            "campaigns" -> go(context, ProposalsActivity::class.java)
            "docs", "media", "app", "page", "contract" -> {
                val mode = kindMode(id) ?: return
                val found = kinds.firstOrNull { it.mode == mode }?.found == true
                context.sendBroadcast(Intent(context, AiwaWidgetReceiver::class.java).setAction(FastWidget.ACTION_KIND).putExtra("mode", mode).putExtra("found", if (found) "1" else "0"))
            }
            "agent" -> if (permanentOn) go(context, ListenToggleActivity::class.java) else if (needsSetup) go(context, SetupActivity::class.java) else go(context, AgentToggleActivity::class.java)
            "claude" -> if (state.cloudSessionId != null) go(context, OpenClaudeActivity::class.java) else toastOnMain(context, "Pas de session Claude pour l'instant.")
            "listen" -> if (!permanentOn && !agentOn) go(context, ListenToggleActivity::class.java)
            "plus" -> go(context, PlusMenuActivity::class.java)
            "scenario" -> go(context, ScenariosActivity::class.java)
            "visible" -> context.sendBroadcast(Intent(context, AiwaWidgetReceiver::class.java).setAction(FastWidget.ACTION_VOIR).putExtra("do", "toggle"))
            "nearby" -> context.startActivity(FastWidget.voirIntent(context, state, kinds, "proche"))
            "link" -> context.startActivity(FastWidget.voirIntent(context, state, kinds, "lien"))
            "qr" -> context.startActivity(FastWidget.voirIntent(context, state, kinds, "qr"))
            "favorites" -> context.startActivity(FastWidget.voirIntent(context, state, kinds, "favoris"))
        }
    }

    /**
     * A scenario of a profile page (`Profile/scenarios.json`), launched in OUR environment: it is saved among the scenarios of the person and started (the brain makes its session: a double of her). A scenario can write to her sessions and ask her phone
     * for things, so the one that comes from someone else's page is shown whole and only runs after she says yes.
     */
    fun scenario(context: Context, raw: JSONObject, source: String?, own: Boolean) {
        val name = raw.optString("name").trim().take(40)
        val prompt = raw.optString("prompt").trim().take(1500)
        if (name.isEmpty()) return
        val run = { Thread { runScenario(context.applicationContext, raw, name, prompt, source, own) }.start() }
        if (own) { run(); return }
        val activity = context as? Activity
        if (activity == null) { toastOnMain(context, "Ouvre le profil dans Aiwa pour lancer ce scénario."); return }
        activity.runOnUiThread {
            AlertDialog.Builder(activity)
                .setTitle("Lancer « $name » ?")
                .setMessage("Ce scénario vient du profil de ${source ?: "quelqu'un d'autre"}. Une fois lancé, il agit comme un double de toi : une session à lui, qui peut écrire à tes sessions et demander des choses à ton téléphone. Voici ce qu'on lui demande :\n\n$prompt")
                .setPositiveButton("Lancer") { _, _ -> run() }
                .setNegativeButton("Annuler", null)
                .show()
        }
    }

    private fun runScenario(app: Context, raw: JSONObject, name: String, prompt: String, source: String?, own: Boolean) {
        val base = raw.optString("id").ifBlank { name }
        val id = (if (own) base else "p-" + (source ?: "x") + "-" + base).lowercase().replace(Regex("[^a-z0-9-]"), "-").take(30)
        val list = raw.optJSONArray("words")
        val words = if (list == null) emptyList() else (0 until minOf(list.length(), 8)).mapNotNull {
            val w = list.optJSONObject(it) ?: return@mapNotNull null
            ScenarioWord(w.optString("word"), w.optString("does"))
        }
        val kind = if (raw.optString("kind") == "report") "report" else "double"
        val scenario = Scenario(id, name, raw.optString("word").trim(), kind, prompt, raw.optString("speak").trim(), words)
        val bridge = LocalClaudeBridge()
        val saved = bridge.scenarioSave(scenario)
        if (saved == null) { toastOnMain(app, "Le cerveau ne répond pas : le scénario n'est pas lancé."); return }
        if (saved.error != null) { toastOnMain(app, saved.error ?: "Scénario refusé."); return }
        val launched = bridge.scenarioLaunch(id = id)
        toastOnMain(app, if (launched != null) "Scénario « $name » lancé." else "Scénario « $name » ajouté, mais il ne démarre pas.")
    }

    private fun entry(label: String? = null, tone: String? = null, on: Boolean? = null, found: Boolean? = null, icon: String? = null): JSONObject {
        val o = JSONObject()
        if (label != null) o.put("label", label)
        if (tone != null) o.put("tone", tone)
        if (on != null) o.put("on", on)
        if (found != null) o.put("found", found)
        if (icon != null) o.put("icon", icon)
        return o
    }

    /** What the buttons show now, by id of button: the same words and tones as the cells of the widget. */
    fun state(context: Context): JSONObject {
        val state = AiwaRepository.state.value
        val kinds = lightKinds(state, context)
        val out = JSONObject()
        val allMode = Projects.isAll(context)
        val loopOn = state.loopPair.isNotEmpty()
        val projectBad = !allMode && (state.repo == null || (accessNotice(state, 1) != null || state.backend == "missing" || state.claudeLogin == "needed"))
        out.put("project", entry(label = if (allMode) "Tous" else (Projects.nameOf(context, state.repo, state.cloudSessionId) ?: "Projet") + " ▾", tone = if (allMode) "green" else if (projectBad) "red" else "grey"))
        out.put("loops", entry(label = if (loopOn) "Boucle ${state.loopPair}" else "Boucles", tone = if (loopOn) "green" else "blue", on = loopOn))
        for (id in listOf("docs", "media", "app", "page", "contract")) {
            val mode = kindMode(id) ?: continue
            val found = kinds.firstOrNull { it.mode == mode }?.found == true
            out.put(id, entry(found = found, tone = if (!found) "grey" else if (mode == "pages" || mode == "aiwa") "yellow" else "green"))
        }
        val needsSetup = !hasTermuxPermission(context)
        val permanentOn = state.listening && !needsSetup
        val agentOn = state.agent?.enabled == true
        val agentNeeds = state.agent?.let { it.say != null || it.proposal != null || it.command != null } == true
        out.put("agent", when {
            permanentOn -> entry("Écoute", "green", true, icon = "listen")
            needsSetup -> entry("Autoriser Aiwa", "red")
            agentNeeds -> entry("À dire", "red", true)
            agentOn -> entry("Super agent", "green", true)
            else -> entry("Super agent", "orange", false)
        })
        out.put("claude", entry(tone = if (state.waiting || state.silenceMinutes > 0) "red" else "grey", found = state.cloudSessionId != null))
        out.put("listen", entry(label = if (state.listening) "Écoute active" else "Écoute", tone = if (state.listening) "green" else "grey", on = state.listening))
        out.put("plus", entry(label = if (state.attachments > 0) "${state.attachments} jointe(s)" else "Ajouter", tone = if (state.attachments > 0) "green" else "grey"))
        val voirOn = VoirSettings.on(context)
        out.put("visible", entry(label = if (voirOn) "Visible" else "Caché", tone = if (voirOn) "violet" else "grey", on = voirOn, icon = if (voirOn) "visible" else "hidden"))
        return out
    }
}
