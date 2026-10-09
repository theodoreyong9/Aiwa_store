package com.aiwa.store

import android.content.Context
import android.content.SharedPreferences
import com.aiwa.bridge.AskInfo
import com.aiwa.bridge.CloudSessionInfo
import com.aiwa.bridge.SentApp
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Bump together with BACKEND_VERSION in backend/aiwa_server.py whenever
// the app starts relying on a new backend feature.
const val EXPECTED_BACKEND_VERSION = 42

/** How long the widget keeps saying that a message was refused because another is on its way. */
const val BUSY_NOTICE_MS = 20_000L

data class ModelChoice(val id: String?, val label: String)

// The models Claude Code documents (code.claude.com, model-config), with
// their exact ids. The choice is REAL in two places: `claude --model` when a
// new cloud session is created, and `/model <id>` sent to the open session
// (confirmed on a device: the session answers "Set model to ..."). null id =
// the account's default ("/model default", currently Opus 5.5). The Claude
// app's own model chip lagged one step behind /model at first; the user reports
// it follows now (unverified here).
val MODEL_CHOICES = listOf(
    ModelChoice(null, "Auto (défaut du compte)"),
    ModelChoice("claude-fable-5-1", "Fable 5.1"),
    ModelChoice("claude-fable-5", "Fable 5"),
    ModelChoice("claude-opus-5-5", "Opus 5.5"),
    ModelChoice("claude-opus-5", "Opus 5"),
    ModelChoice("claude-opus-4-8", "Opus 4.8"),
    ModelChoice("claude-opus-4-7", "Opus 4.7"),
    ModelChoice("claude-opus-4-6", "Opus 4.6"),
    ModelChoice("claude-sonnet-5-5", "Sonnet 5.5"),
    ModelChoice("claude-sonnet-5", "Sonnet 5"),
    ModelChoice("claude-sonnet-4-6", "Sonnet 4.6"),
    ModelChoice("claude-haiku-4-5", "Haiku 4.5"),
    ModelChoice("claude-fable-5-1[1m]", "Fable 5.1 · 1M"),
    ModelChoice("claude-opus-4-8[1m]", "Opus 4.8 · 1M"),
    ModelChoice("claude-sonnet-4-6[1m]", "Sonnet 4.6 · 1M"),
    ModelChoice("opusplan", "Opus plan (Opus, puis Sonnet)"),
)

// What Claude is asked to produce with the work (the widget's project-type chip, a list).
val DEPLOY_CHOICES = listOf(
    ModelChoice("pages", "Site web — publié avec GitHub Pages"),
    ModelChoice("android", "Application Android — l'APK dans une release GitHub"),
    ModelChoice("store", "Store Aiwa — une app écrite par Claude, que tu essaies puis publies depuis le Store (▦)"),
    ModelChoice("aiwa", "App Aiwa — un contrat publié par Aiwa : GitHub n'en garde qu'un pointeur immuable"),
)

// The two modes that end with an app Claude sends to the phone, to be opened in the Store.
fun deliversApp(deploy: String) = deploy == "store" || deploy == "aiwa"

fun modelLabel(id: String?): String = MODEL_CHOICES.find { it.id == id }?.label?.substringBefore(" (") ?: id ?: "Auto"

// The effort levels `/effort` and `claude --effort` accept (null = automatic).
// Not every model has all of them (Haiku's are not documented).
val EFFORT_CHOICES = listOf(
    ModelChoice(null, "Auto"),
    ModelChoice("low", "Faible"),
    ModelChoice("medium", "Moyen"),
    ModelChoice("high", "Élevé"),
    ModelChoice("xhigh", "Très élevé"),
    ModelChoice("max", "Max"),
)

data class AiwaState(
    // Display name of the current cloud session, derived by BackendSync
    // from the backend's real answer — never guessed locally.
    val session: String = "Nouvelle session",
    val cloudSessionId: String? = null,
    // The session most recently in use — "Claude ↗" opens it when no session is in progress.
    val lastSessionId: String? = null,
    val cloudSessions: List<CloudSessionInfo> = emptyList(),
    val model: String? = null,
    val backendVersion: Int = 0,
    // Instructions integrated into the conversation — Claude Code does the
    // work itself: the repository new sessions start on (null = the plain
    // chat), push straight to the main branch, publish with GitHub Pages,
    // alert when it needs an answer, plus the user's own text. siteState:
    // off / waiting / live (whether the Pages address answers).
    val repo: String? = null,
    // Other repositories Claude may ALSO work on (checked in the repository picker).
    val extraRepos: List<String> = emptyList(),
    val pushMain: Boolean = true,
    // none / pages (GitHub Pages) / android (the APK as a GitHub release) / store (an
    // app for the Store sent to the phone, no GitHub).
    val deploy: String = "pages",
    val extra: String = "",
    // Claude pinged the relay: it waits for an answer (the widget shows it).
    // alertAt: epoch seconds of the last ping ever received, null = never.
    val waiting: Boolean = false,
    val alertAt: Long? = null,
    val effort: String? = null,
    val siteUrl: String? = null,
    val siteState: String = "off",
    // "site", "apk" or "store": what siteUrl is (see SiteInfo).
    val siteKind: String = "site",
    // With the Android mode: the line the app declared for Termux (copied when the APK is downloaded), or null.
    val siteTermux: String? = null,
    // Whether the CLI is logged in to a Claude account (ok / needed / unknown), whether Claude's
    // cloud environment reaches the relay (ok / untested / pending / missing), and the last
    // app Claude sent (null = none yet).
    val claudeLogin: String = "unknown",
    val relayCloud: String = "untested",
    val sentApp: SentApp? = null,
    // A question Claude asked through the widget and that is not answered yet (the widget says so; the window that answers it is AskActivity).
    val ask: AskInfo? = null,
    // Who may use this build (AccessGate): the list is on or off, the level of the signed-in GitHub account (2 everything, 1 Pages and APK, 0 nothing), and its name.
    val accessEnforced: Boolean = false,
    val accessLevel: Int = 2,
    val accessLogin: String? = null,
    // The always-on listening is running right now (WakeWordService): the widget's voice button is green then. Not persisted: a new process starts with it off.
    val listening: Boolean = false,
    // The latest GitHub Actions run of the repository: running / success /
    // failure / none, with the link to that run (null = unknown).
    val ciState: String? = null,
    val ciUrl: String? = null,
    // A new green run the user has not been told about: there is something to look at.
    val ciFresh: Boolean = false,
    // Which workflow / commit / event / author the GitHub verdict is about (the "État d'Aiwa" list says it).
    val ciDetail: String? = null,
    // A setting of GitHub to change by hand for the deployment to work (what the failed run says): its title, steps and the page to open.
    val ciHintTitle: String? = null,
    val ciHintSteps: List<String> = emptyList(),
    val ciHintUrl: String? = null,
    val githubError: String? = null,
    // A newer release of the Store's page has been downloaded and waits for the next start of the Store (a button of the widget applies it).
    val storeUpdateReady: Boolean = false,
    val repoAccessMissing: String? = null,
    val sourceRepos: List<String> = emptyList(),
    val githubAppUrl: String? = null,
    // Whether the local backend answers: "unknown" (not asked yet), "up", "down",
    // or "starting" (Termux was asked to start it, since backendStartedAt).
    // "missing" is a backend that cannot be there: Termux is not installed, or it was started twice in vain (not installed in it).
    val backend: String = "unknown",
    val backendStartedAt: Long = 0L,
    // How many times Termux was asked to start the backend since it last answered, and why it is "missing" ("termux" / "install").
    val backendStarts: Int = 0,
    val backendMissing: String? = null,
    val status: Status = Status.READY,
    // The last problem worth telling the user about, shown in the app only
    // (the widget has no message area: its errors are toasts). There is no
    // conversation text at all: cloud replies can't be read back by a
    // program (see aiwa_server.py), so they are read in the Claude app.
    val notice: String? = null,
    val question: String? = null,
    // When a second message was last refused because one is already on its way (epoch ms): the widget says "attends" for BUSY_NOTICE_MS.
    val busyNoticeAt: Long = 0L,
    // When the message being sent began (epoch ms; 0 = none): the widget says how long it has been going.
    val sendingSince: Long = 0L,
    // What the send is doing now (from the backend): shown after the seconds.
    val sendingNote: String? = null,
) {
    /** Nothing can be sent yet: a new session needs a repository (there is no free conversation), and none is chosen or running. */
    val needsRepo: Boolean get() = repo == null && cloudSessionId == null

    enum class Status { READY, WORKING, WAITING, DONE, ERROR }
}

/**
 * The one process-wide source of truth both the app's Compose UI and the
 * widget read. The widget observes it reactively (collectAsState inside
 * its composition) — it must NOT capture a snapshot in provideGlance:
 * Glance keeps a composition alive for a while and answers updateAll()
 * by recomposing that same composition, so a captured snapshot stays
 * stale (reported live as the widget header not following the app).
 */
object AiwaRepository {
    private val _state = MutableStateFlow(AiwaState())
    val state: StateFlow<AiwaState> = _state
    private var prefs: SharedPreferences? = null

    fun update(transform: (AiwaState) -> AiwaState) {
        _state.value = transform(_state.value)
    }

    fun markBackendStarting() {
        update { it.copy(backend = "starting", backendStartedAt = System.currentTimeMillis()) }
    }

    /**
     * The last thing the backend told us, restored when the process starts
     * (AiwaApp). Reported live: with the backend down or slow to start, a
     * freshly started process knew nothing — no session, no repository — so
     * the widget lost its "Claude ↗" button until the backend answered.
     * The backend stays the source of truth: the first refresh overwrites this.
     */
    fun restore(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext.getSharedPreferences("aiwa_state", Context.MODE_PRIVATE)
        prefs = store
        val raw = store.getString("snapshot", null) ?: return
        try {
            val json = JSONObject(raw)
            fun text(key: String): String? = if (json.isNull(key)) null else json.optString(key).ifEmpty { null }
            val sessions = json.optJSONArray("sessions") ?: JSONArray()
            update {
                it.copy(
                    session = text("session") ?: it.session,
                    cloudSessionId = text("cloud"),
                    lastSessionId = text("last"),
                    cloudSessions = (0 until sessions.length()).map { index ->
                        val entry = sessions.getJSONObject(index)
                        CloudSessionInfo(entry.getString("id"), entry.optString("title"), entry.optString("url"), if (entry.isNull("repo")) null else entry.optString("repo"))
                    },
                    model = text("model"),
                    effort = text("effort"),
                    repo = text("repo"),
                    extraRepos = json.optJSONArray("extraRepos")?.let { list -> (0 until list.length()).map { list.getString(it) } } ?: emptyList(),
                    pushMain = json.optBoolean("pushMain", true),
                    deploy = text("deploy")?.takeIf { it != "none" } ?: "pages",
                    extra = text("extra") ?: "",
                    siteUrl = text("siteUrl"),
                    siteState = text("siteState") ?: "off",
                    siteKind = text("siteKind") ?: "site",
                    ciState = text("ciState"),
                    ciUrl = text("ciUrl"),
                )
            }
        } catch (err: Exception) {
            // An unreadable snapshot is just ignored.
        }
    }

    fun persist() {
        val store = prefs ?: return
        val s = _state.value
        val json = JSONObject()
            .put("session", s.session).put("cloud", s.cloudSessionId).put("last", s.lastSessionId)
            .put("model", s.model).put("effort", s.effort).put("repo", s.repo).put("extraRepos", JSONArray(s.extraRepos))
            .put("pushMain", s.pushMain).put("deploy", s.deploy).put("extra", s.extra)
            .put("siteUrl", s.siteUrl).put("siteState", s.siteState).put("siteKind", s.siteKind).put("ciState", s.ciState).put("ciUrl", s.ciUrl)
        val sessions = JSONArray()
        s.cloudSessions.forEach { c ->
            sessions.put(JSONObject().put("id", c.id).put("title", c.title).put("url", c.url).put("repo", c.repo))
        }
        json.put("sessions", sessions)
        store.edit().putString("snapshot", json.toString()).apply()
    }
}
