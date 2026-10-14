package com.aiwa.bridge

// state: off (no repository), waiting (the address doesn't answer yet) or live.
// kind: "site" (the GitHub Pages address), "apk" (the download address of the
// Android APK, in the Android deploy mode) or "store" (no address: live means an app
// sent by Claude has not been opened in the Store yet; the "store" and "aiwa" modes both).
// termux: with the Android mode, the line to paste in Termux that the app declared (aiwa-android.json in its repository), or null.
data class SiteInfo(val url: String?, val state: String, val kind: String = "site", val termux: String? = null)

// What Claude sent to the phone (its source: ClaudeBridge.sentAppCode): an app for the Store, one self-contained index.html.
// kind: "code" (name.app.html, its code travels in the package on GitHub) or "aiwa" (name.aiwa.html, published through Aiwa).
// seen: it was opened in the Store's publish sheet already.
// github: the address of its code in the repository (aiwa-apps/<name> on Claude's branch), once it is known to be there.
data class SentApp(val name: String, val size: Int, val ts: Long, val seen: Boolean, val kind: String = "code", val github: String? = null)
// A question Claude asked through the widget (it never sees the conversation): the question, a few short choices, and an id the answer carries.
// `session` is the title of the session that asked, `count` how many questions wait (of every session): they wait together, each is answered in its own session.
data class AgentSay(val id: String, val text: String, val ts: Long)
data class AgentProposal(val id: String, val session: String, val sessionTitle: String, val message: String, val readBack: String)
data class AgentCommand(val id: String, val readBack: String, val instruction: String)
data class AgentInfo(val enabled: Boolean, val session: String?, val interval: Int, val creating: Boolean, val error: String?, val say: AgentSay?, val proposal: AgentProposal?, val voice: String = "none", val command: AgentCommand? = null)
data class AskInfo(val id: String, val question: String, val options: List<String>, val ts: Long, val session: String? = null, val count: Int = 1)

// A promotional video of the creation: a file of the GitHub release `videos` of its repository (a public repository only: Aiwa asks for no token).
data class VideoItem(val name: String, val size: Long, val url: String, val ts: String)
data class VideoList(val repo: String?, val videos: List<VideoItem>)

/** A source file of a creation or a campaign, in the folder aiwa-campagne/ of the repository. */
data class InputFile(val name: String, val size: Long)

/** A loop between two sessions: a builder and a critic (the backend is the courier between them). */
data class LoopPair(
    val id: String, val builder: String, val critic: String, val builderTitle: String, val criticTitle: String,
    val state: String, val turn: String, val rounds: Int, val limit: Int, val started: Long?, val created: Long?, val rolledBack: Long?,
)
data class LoopChainEntry(val id: String, val started: Long?, val created: Long?, val state: String)
/** The critiques the critic of a pair wrote on the corpus, the last turn first; `resume` is true when its summary of facts exists. */
data class CritiqueItem(val n: Int, val name: String, val size: Long)
data class CritiqueList(val branch: String, val items: List<CritiqueItem>, val resume: Boolean)
data class LoopsInfo(val limit: Int, val pairs: List<LoopPair>, val chains: List<List<LoopChainEntry>>)
/** The running loop that began last, for the widget: which pair, the round it is at, the limit, whose turn it is. */
/** A cast waiting for its video: the television as said (may be empty), and the video that has just been made. */
data class CastWish(val tv: String, val name: String, val url: String)
data class LoopRunning(val pair: String, val rounds: Int, val limit: Int, val turn: String, val running: Int)
data class InputList(val repo: String?, val files: List<InputFile>)

data class SentAppCode(val name: String, val code: String, val kind: String = "code", val repo: String? = null, val path: String? = null, val ref: String = "main")

// The `claude auth login` the backend runs for the app. phase: idle / starting / url (the
// page to open is there, a code is awaited) / checking / done / failed; message: what the
// CLI said last, when it said something.
data class LoginStep(val phase: String, val url: String?, val message: String)

// state: running, success, failure or none — the verdict on the latest commit's runs together.
// fresh: a new green commit the user has not been told about yet. detail: which workflow, commit,
// event and author the verdict is about (so that a surprising "Prêt" can be explained).
// hint: a setting of GitHub that has to be changed by hand for the deployment to work (title, the steps, the page of that setting), or null.
data class CiHint(val code: String, val title: String, val steps: List<String>, val url: String)
data class CiInfo(val state: String, val url: String?, val fresh: Boolean = false, val detail: String? = null, val hint: CiHint? = null)

data class BackendStatus(
    val model: String?,
    // low / medium / high / xhigh / max, null = automatic.
    val effort: String? = null,
    val version: Int,
    val cloudSession: String?,
    // The session most recently in use, kept when the next message will
    // start a new one — so its conversation can still be opened.
    val lastSession: String? = null,
    val repo: String? = null,
    // Other repositories Claude may ALSO work on (told to it with the next message).
    val extraRepos: List<String> = emptyList(),
    // Other people's repositories Claude reads for inspiration and never writes.
    val sourceRepos: List<String> = emptyList(),
    val pushMain: Boolean = true,
    // none / pages (GitHub Pages) / android (the APK as a GitHub release) / store (an app for the Store).
    val deploy: String = "none",
    // simple / moyen / extra: how far a video goes (and how long it takes).
    val videoQuality: String = "moyen",
    val extra: String = "",
    // Claude pinged the relay: it is waiting for an answer. alertLast: epoch
    // seconds of the last ping ever received (null = never).
    val waiting: Boolean = false,
    val alertLast: Long? = null,
    // Minutes since a message went with no sign of Claude by any road (0 = nothing to say): the widget then asks to go and look in the conversation.
    val silenceMinutes: Int = 0,
    val loop: LoopRunning? = null,
    val cast: CastWish? = null,
    val site: SiteInfo = SiteInfo(null, "off"),
    val ci: CiInfo? = null,
    val githubError: String? = null,
    // The repository whose last session started empty because Claude's GitHub app is not installed on it, and where to install it.
    val repoAccessMissing: String? = null,
    val githubAppUrl: String? = null,
    // Whether the CLI is logged in to a Claude account: ok / needed / unknown.
    val claudeLogin: String = "unknown",
    // Whether a command Claude runs in the cloud reaches the relay (its network access must
    // allow ntfy.sh): ok / untested / pending / missing.
    val relayCloud: String = "untested",
    val sentApp: SentApp? = null,
    // The question Claude asked and that is not answered yet, or null.
    val ask: AskInfo? = null,
    // The agent: a dedicated session that talks with the user by voice only.
    val agent: AgentInfo? = null,
    // The backend is working on a message right now (creating a session takes a while). It knows, whatever became of the app that sent it.
    val sending: Boolean = false,
    // When it began (epoch milliseconds), so that the widget can say how long it has lasted.
    val sendingSince: Long? = null,
    // What it is doing right now: a step of the backend's, then the last line the CLI printed (e.g. an upload's progress).
    val sendingNote: String? = null,
)

data class CloudSessionInfo(val id: String, val title: String, val url: String, val repo: String? = null)
data class CloudSendResult(val ok: Boolean, val sessionId: String?, val url: String?, val error: String?)
data class RepoInfo(val name: String, val isPrivate: Boolean)

interface ClaudeBridge {
    suspend fun status(): BackendStatus
    suspend fun selectModel(id: String?)
    suspend fun selectEffort(level: String?)
    suspend fun listCloudSessions(): List<CloudSessionInfo>
    suspend fun selectCloud(target: String)
    suspend fun addCloud(link: String)
    suspend fun clearCloudSessions()
    suspend fun sendCloud(text: String): CloudSendResult
    suspend fun sendCloudCommand(text: String): CloudSendResult
    suspend fun githubRepos(): List<RepoInfo>
    suspend fun selectRepo(repo: String)
    suspend fun addRepo(text: String)

    // Checks or unchecks a repository Claude may ALSO work on; null = none.
    suspend fun toggleExtraRepo(repo: String?)

    /** Adds the repository (an address or owner/name) to the ones read for inspiration, or removes it when it is one; null empties the list. */
    suspend fun toggleSourceRepo(text: String?)

    // The instructions integrated into the conversation. Only the ones that
    // are not null are changed.
    suspend fun setOptions(pushMain: Boolean?, deploy: String?, extra: String?, videoQuality: String? = null)

    // The user went to look at the latest Actions run: it is no longer news.
    suspend fun ciSeen()

    // The user opened the session (or answered): the waiting indicator goes off.
    suspend fun waitingClear()

    // A ping sent by the backend itself, to check the phone side of the alert.
    // Throws when the relay can't be reached.
    suspend fun waitingTest()

    // The block of instructions Claude Code would receive with the next message.
    suspend fun instructions(): String

    // ---- Connecting the CLI to a Claude account (the app's "Connecter Claude" window) ----

    // What the running login is up to (idle when none).
    suspend fun claudeLoginState(): LoginStep

    // Starts a login and waits for the page to open (phase url) — or for its failure.
    suspend fun claudeLoginStart(): LoginStep

    // Gives the code the login page shows; answers when the CLI has decided.
    suspend fun claudeLoginCode(code: String): LoginStep

    suspend fun claudeLoginCancel()

    // A fresh look at whether the CLI is logged in: ok / needed / unknown.
    suspend fun claudeCheck(): String

    // Asks the current session to run the relay test again. Throws with the reason.
    suspend fun relayRetest()

    // ---- The app Claude sent ----

    // Throws when none was received.
    suspend fun sentAppCode(): SentAppCode

    suspend fun sentAppSeen()

    // ---- What Claude asks, and the videos of the creation ----

    // Sends the answer to the question `id` to the session, as its next message. Throws with the reason.
    suspend fun askAnswer(id: String, answer: String) { throw UnsupportedOperationException("askAnswer") }

    // One action of the agent (create, set, now, ask, confirm, cancel, said). Returns the reason when it fails, else null.
    suspend fun agent(action: String, fields: Map<String, Any> = emptyMap()): String? { throw UnsupportedOperationException("agent") }

    // The natural voice (Piper, in the backend): asks for its installation (once), and for a sentence as a WAV, or null when it is not ready.
    suspend fun voiceSetup() { throw UnsupportedOperationException("voiceSetup") }
    suspend fun voiceSay(text: String): ByteArray? { throw UnsupportedOperationException("voiceSay") }

    suspend fun videos(): VideoList { throw UnsupportedOperationException("videos") }
    suspend fun inputs(): InputList { throw UnsupportedOperationException("inputs") }
    suspend fun critiques(pairId: String): CritiqueList { throw UnsupportedOperationException("critiques") }
    suspend fun critiqueText(pairId: String, name: String): String { throw UnsupportedOperationException("critiqueText") }
    suspend fun loops(): LoopsInfo { throw UnsupportedOperationException("loops") }
    /** The person asked for the video to go to the television once Claude has made it. */
    suspend fun castPending(tv: String) { throw UnsupportedOperationException("castPending") }
    suspend fun castClear() { throw UnsupportedOperationException("castClear") }
    /** create (builder, critic), start / stop / relaunch / rollback / delete (id), limit (limit). Returns the reason when refused, null when done. */
    suspend fun loopsAction(action: String, fields: Map<String, String> = emptyMap()): String? { throw UnsupportedOperationException("loopsAction") }
    // The proposals a session prepared (`aiwa-proposals/proposals.json` of the repository): to read, validate and send by hand.
    suspend fun proposals(): ProposalList { throw UnsupportedOperationException("proposals") }
}
