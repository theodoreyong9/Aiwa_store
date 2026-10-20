package com.aiwa.bridge

import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** The backend refused because a send is already running. Not a failure:
 * callers must not undo the request really in flight. */
class BusyException(message: String) : Exception(message)

/** The backend answered 404: it is older than this app expects. Distinct
 * from "not reachable" so callers can say what to actually do. */
class BackendOutdatedException(message: String) : Exception(message)

// A JSON null (or a missing key, or "") is Kotlin null; JSONObject.optString
// alone would turn a JSON null into the text "null".
private fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key).ifEmpty { null }

class LocalClaudeBridge(private val baseUrl: String = "http://127.0.0.1:8787") : ClaudeBridge {

    // A raw ConnectException's message is plumbing, not something that
    // tells anyone what to do; not being able to reach the local backend
    // is by far the most common failure, so it gets an actionable message.
    private fun <T> withClearConnectionError(block: () -> T): T = try {
        block()
    } catch (err: java.net.ConnectException) {
        throw IllegalStateException(
            "Backend not reachable at $baseUrl — it isn't started. Run `python3 backend/aiwa_server.py` on THIS device first (see README's \"Running this for real\").",
            err,
        )
    } catch (err: java.io.FileNotFoundException) {
        throw BackendOutdatedException(
            "Backend obsolète : il ne connaît pas cette fonction. Dans Termux : cd ~/aiwa_store && git pull, puis rouvre Aiwa.",
        )
    }

    private fun postText(path: String, text: String): String = withClearConnectionError {
        val connection = URI("$baseUrl$path").toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
        connection.outputStream.use { it.write(text.toByteArray()) }
        connection.inputStream.bufferedReader().use { it.readText() }
    }

    private fun getText(path: String): String = withClearConnectionError {
        val connection = URI("$baseUrl$path").toURL().openConnection() as HttpURLConnection
        connection.inputStream.bufferedReader().use { it.readText() }
    }

    // The backend answers {"accepted": false, ...} for a request it
    // refuses; ignoring that made callers believe something had changed.
    private fun requireAccepted(raw: String) {
        if (raw.contains("\"accepted\": false") || raw.contains("\"accepted\":false")) {
            if (raw.contains("busy")) throw BusyException("backend busy: $raw")
            throw IllegalStateException("backend refused: $raw")
        }
    }

    override suspend fun status(): BackendStatus = withContext(Dispatchers.IO) {
        val json = JSONObject(getText("/api/status"))
        val site = json.optJSONObject("site")
        val run = json.optJSONObject("ci")
        BackendStatus(
            model = json.str("model"),
            effort = json.str("effort"),
            version = json.optInt("version", 0),
            updatedAt = json.optLong("updated_at", 0),
            cloudSession = json.str("cloud_session"),
            lastSession = json.str("last_session"),
            repo = json.str("repo"),
            sourceRepos = json.optJSONArray("source_repos")?.let { list -> (0 until list.length()).map { list.getString(it) } } ?: emptyList(),
            extraRepos = json.optJSONArray("extra_repos")?.let { list -> (0 until list.length()).map { list.getString(it) } } ?: emptyList(),
            pushMain = json.optBoolean("push_main", true),
            // An older backend only says yes/no: yes was GitHub Pages.
            deploy = json.str("deploy") ?: if (json.optBoolean("autodeploy", false)) "pages" else "none",
            videoQuality = json.str("video_quality") ?: "moyen",
            videoFormat = json.str("video_format") ?: "16:9",
            extra = json.str("extra") ?: "",
            waiting = json.optBoolean("waiting", false),
            silenceMinutes = json.optInt("silence_minutes", 0),
            attachments = json.optInt("attachments", 0),
            attachmentFiles = json.optJSONArray("attachment_files")?.let { arr -> (0 until arr.length()).map { i -> arr.getJSONObject(i).let { it.optString("name") to it.optInt("size", 0) } } } ?: emptyList(),
            cast = json.optJSONObject("cast")?.let { CastWish(it.optString("tv"), it.optString("name"), it.optString("url")) },
            loop = json.optJSONObject("loop")?.let { LoopRunning(it.optString("pair"), it.optInt("rounds", 0), it.optInt("limit", 0), it.optString("turn"), it.optInt("running", 1)) },
            alertLast = if (json.isNull("alert_last")) null else json.optLong("alert_last"),
            site = SiteInfo(site?.str("url"), site?.str("state") ?: "off", site?.str("kind") ?: "site", site?.str("termux")),
            ci = run?.let { CiInfo(it.str("state") ?: "none", it.str("url"), it.optBoolean("fresh", false), it.str("detail"), ciHint(it.optJSONObject("hint"))) },
            githubError = json.str("github_error"),
            repoAccessMissing = json.str("repo_access_missing"),
            githubAppUrl = json.str("github_app_url"),
            claudeLogin = json.str("claude_login") ?: "unknown",
            relayCloud = json.str("relay_cloud") ?: "untested",
            sending = json.optBoolean("sending", false),
            sendingSince = if (json.isNull("sending_since")) null else (json.optDouble("sending_since") * 1000).toLong(),
            sendingNote = json.str("sending_note"),
            ask = json.optJSONObject("ask")?.let { a ->
                val choices = a.optJSONArray("options")
                AskInfo(a.optString("id"), a.optString("question"), if (choices == null) emptyList() else (0 until choices.length()).map { choices.getString(it) }, a.optLong("ts", 0L), a.optString("session").ifBlank { null }, a.optInt("count", 1))
            },
            agent = json.optJSONObject("agent")?.let { a ->
                AgentInfo(
                    a.optBoolean("enabled", false), a.str("session"), a.optInt("interval", 20), a.optBoolean("creating", false), a.str("error"),
                    a.optJSONObject("say")?.let { AgentSay(it.optString("id"), it.optString("text"), it.optLong("ts", 0L)) },
                    a.optJSONObject("proposal")?.let { AgentProposal(it.optString("id"), it.optString("session"), it.optString("session_title"), it.optString("message"), it.optString("read_back")) },
                    a.optString("voice", "none"),
                    a.optJSONObject("command")?.let { c -> AgentCommand(c.optString("id"), c.optString("read_back"), c.optString("instruction"), c.optJSONObject("cast")?.let { k -> AgentCast(k.optString("tv"), k.optBoolean("wait", false)) }, c.optString("open").takeIf { it.isNotBlank() && it != "null" },
                        c.optJSONObject("connector")?.let { k -> AgentConnector(k.optString("kind"), k.optString("to"), k.optString("subject"), k.optString("body"), k.optString("start")) }) },
                    a.optString("llm", "none"),
                    a.str("repo"),
                    a.optJSONArray("history")?.let { h -> (0 until h.length()).mapNotNull { i -> h.optJSONObject(i)?.let { o -> AgentOld(o.optString("id"), o.str("repo"), o.optString("title", "Ancien agent")) } } } ?: emptyList(),,
                    a.str("voice_error")
                )
            },
            sentApp = json.optJSONObject("sent_app")?.let {
                SentApp(it.optString("name"), it.optInt("size", 0), it.optLong("ts", 0L), it.optBoolean("seen", false), it.str("kind") ?: "code", it.str("github"))
            },
        )
    }

    // The model passed to `claude --model` when a NEW cloud session is
    // created; null = the CLI's own default.
    override suspend fun selectModel(id: String?) = withContext(Dispatchers.IO) {
        requireAccepted(postText("/api/model", id ?: ""))
    }

    // low / medium / high / xhigh / max; null = automatic. Passed to
    // `claude --effort` when a NEW cloud session is created.
    override suspend fun selectEffort(level: String?) = withContext(Dispatchers.IO) {
        requireAccepted(postText("/api/effort", level ?: ""))
    }

    override suspend fun listCloudSessions(): List<CloudSessionInfo> = withContext(Dispatchers.IO) {
        val array = JSONArray(getText("/api/cloud/sessions"))
        (0 until array.length()).map {
            val entry = array.getJSONObject(it)
            CloudSessionInfo(
                id = entry.getString("id"),
                title = entry.optString("title", entry.getString("id")),
                url = entry.optString("url"),
                repo = entry.str("repo"),
            )
        }
    }

    // target: "new" (the next message creates a cloud session) or the id
    // of one of listCloudSessions().
    override suspend fun selectCloud(target: String) = withContext(Dispatchers.IO) {
        requireAccepted(postText("/api/cloud/select", target))
    }

    // An EXISTING cloud session, given the name of its branch (claude/…), which the backend
    // looks up in the repositories it knows (the CLI can't list sessions); it becomes the
    // current one. A link alone is refused. Throws with the reason when it can't be found.
    override suspend fun addCloud(link: String) = withContext(Dispatchers.IO) {
        val json = JSONObject(postText("/api/cloud/add", link))
        if (!json.optBoolean("accepted", false)) throw IllegalStateException(json.optString("reason", "session introuvable"))
    }

    // Synchronous on the backend: creating a session can take a while
    // (the cloud machine starts). HONEST LIMIT (documented by Anthropic):
    // the CLI only queues the message — there is no way to read the reply
    // back, so it is read in the Claude app.
    override suspend fun sendCloud(text: String): CloudSendResult = postCloud("/api/cloud/message", text)

    // A slash command (e.g. "/model opus") for the CURRENT cloud session:
    // never creates a session, fails when there is none.
    override suspend fun sendCloudCommand(text: String): CloudSendResult = postCloud("/api/cloud/command", text)

    private suspend fun postCloud(path: String, text: String): CloudSendResult = withContext(Dispatchers.IO) {
        val json = JSONObject(postText(path, text))
        if (json.optBoolean("ok", false)) {
            CloudSendResult(
                ok = true,
                sessionId = json.optString("session_id").ifEmpty { null },
                url = json.optString("url").ifEmpty { null },
                error = null,
            )
        } else {
            val error = json.optString("error", "erreur inconnue")
            if (error == "busy") throw BusyException("a cloud message is already being sent")
            CloudSendResult(ok = false, sessionId = null, url = null, error = error)
        }
    }

    /** « Tous »: what every repository holds of one kind (docs, video, pages, android, aiwa), read by the backend. */
    fun all(kind: String, refresh: Boolean = false): AllResult = try {
        val json = JSONObject(getText("/api/all?kind=$kind" + if (refresh) "&refresh=1" else ""))
        val items = json.optJSONArray("items")
        AllResult(
            ok = json.optBoolean("ok", false),
            items = (0 until (items?.length() ?: 0)).map { i -> items!!.getJSONObject(i).let { AllItem(it.optString("repo"), it.optString("name"), it.optString("url")) } },
            repos = json.optInt("repos", 0), unreadable = json.optInt("unreadable", 0), error = json.optString("error", ""), pending = json.optInt("pending", 0),
        )
    } catch (e: Exception) {
        AllResult(false, emptyList(), 0, 0, e.message ?: "erreur inconnue")
    }

    /** Attaches a file (base64) to the next message to the session. Returns (ok, files waiting, error). */
    fun attach(name: String, base64: String): Triple<Boolean, Int, String> = try {
        val json = JSONObject(postText("/api/attach", JSONObject().put("name", name).put("data", base64).toString()))
        Triple(json.optBoolean("ok", false), json.optInt("count", 0), json.optString("error", "erreur inconnue"))
    } catch (e: Exception) {
        Triple(false, 0, e.message ?: "erreur inconnue")
    }

    /** Renames a session (the name shown by Aiwa, and told to the session). Returns null when it was done, else why not. */
    fun renameSession(id: String, title: String): String? = try {
        val answer = JSONObject(postText("/api/cloud/rename", JSONObject().put("id", id).put("title", title).toString()))
        if (answer.optBoolean("ok", false)) null else answer.optString("error", "refusé")
    } catch (e: Exception) {
        e.message ?: "?"
    }

    /** The names of the « Projet » button kept by the brain (JSON list), or null when it did not answer. */
    fun projectsGet(): String? = try {
        JSONObject(getText("/api/projects")).optJSONArray("projects")?.toString()
    } catch (e: Exception) {
        null
    }

    /** Hands the whole list of names to the brain, which keeps it through a reinstallation of the app. */
    fun projectsSave(json: String): Boolean = try {
        JSONObject(postText("/api/projects", json)).optBoolean("ok", false)
    } catch (e: Exception) {
        false
    }

    /** Takes back ONE file joined and not sent yet (its place in the list). Returns whether the backend answered ok. */
    fun attachRemove(index: Int): Boolean = try {
        JSONObject(postText("/api/attach/remove", JSONObject().put("index", index).toString())).optBoolean("ok", false)
    } catch (e: Exception) {
        false
    }

    /** Takes back the files joined and not sent yet. Returns whether the backend answered ok. */
    fun attachClear(): Boolean = try {
        JSONObject(postText("/api/attach/clear", "")).optBoolean("ok", false)
    } catch (e: Exception) {
        false
    }

    // The list Aiwa keeps of its sessions, emptied. Nothing is touched in Claude.
    override suspend fun clearCloudSessions() = withContext(Dispatchers.IO) {
        requireAccepted(postText("/api/cloud/clear", ""))
    }

    // The repositories the connected GitHub account can push to.
    override suspend fun githubRepos(): List<RepoInfo> = withContext(Dispatchers.IO) {
        val json = JSONObject(getText("/api/github/repos"))
        if (!json.optBoolean("ok", false)) throw IllegalStateException(json.optString("error", "liste des dépôts indisponible"))
        val array = json.getJSONArray("repos")
        (0 until array.length()).map {
            val entry = array.getJSONObject(it)
            RepoInfo(name = entry.getString("name"), isPrivate = entry.optBoolean("private", false), favorite = entry.optBoolean("favorite", false))
        }
    }

    override suspend fun favoriteRepo(repo: String, favorite: Boolean): String? = withContext(Dispatchers.IO) {
        val json = JSONObject(postText("/api/github/favorite", JSONObject().put("repo", repo).put("favorite", favorite).toString()))
        if (json.optBoolean("ok", false)) null else json.optString("error", "le favori n'a pas été enregistré")
    }

    // Changing repository does not change session: the session in progress is told, with its next message, to work on that repository
    // (as it is told a new push mode). With no session in progress, the next one starts there. There is no "no repository": every session has one.
    override suspend fun selectRepo(repo: String) = withContext(Dispatchers.IO) {
        requireAccepted(postText("/api/repo", repo))
    }

    // A repository given as a GitHub link or owner/name: remembered and selected.
    override suspend fun addRepo(text: String) = withContext(Dispatchers.IO) {
        requireAccepted(postText("/api/github/add", text))
    }

    override suspend fun toggleExtraRepo(repo: String?) = withContext(Dispatchers.IO) {
        requireAccepted(postText("/api/github/extra", repo ?: ""))
    }

    override suspend fun toggleSourceRepo(text: String?) = withContext(Dispatchers.IO) {
        requireAccepted(postText("/api/github/sources", text ?: ""))
    }

    // Only the options that are not null are changed.
    override suspend fun setOptions(pushMain: Boolean?, deploy: String?, extra: String?, videoQuality: String?, videoFormat: String?) = withContext(Dispatchers.IO) {
        val body = JSONObject()
        if (pushMain != null) body.put("push_main", pushMain)
        if (deploy != null) body.put("deploy", deploy)
        if (extra != null) body.put("extra", extra)
        if (videoQuality != null) body.put("video_quality", videoQuality)
        if (videoFormat != null) body.put("video_format", videoFormat)
        requireAccepted(postText("/api/options", body.toString()))
    }

    override suspend fun ciSeen() = withContext(Dispatchers.IO) {
        requireAccepted(postText("/api/ci/seen", ""))
    }

    override suspend fun waitingClear() = withContext(Dispatchers.IO) {
        requireAccepted(postText("/api/waiting/clear", ""))
    }

    override suspend fun waitingTest() = withContext(Dispatchers.IO) {
        requireAccepted(postText("/api/waiting/test", ""))
    }

    override suspend fun instructions(): String = withContext(Dispatchers.IO) {
        JSONObject(getText("/api/instructions")).optString("text", "")
    }

    private fun loginStep(raw: String): LoginStep {
        val json = JSONObject(raw)
        return LoginStep(json.optString("phase", "idle"), json.str("url"), json.optString("message", ""))
    }

    override suspend fun claudeLoginState(): LoginStep = withContext(Dispatchers.IO) { loginStep(getText("/api/claude/login")) }

    override suspend fun claudeLoginStart(): LoginStep = withContext(Dispatchers.IO) { loginStep(postText("/api/claude/login/start", "")) }

    override suspend fun claudeLoginCode(code: String): LoginStep = withContext(Dispatchers.IO) { loginStep(postText("/api/claude/login/code", code)) }

    override suspend fun claudeLoginCancel() = withContext(Dispatchers.IO) {
        requireAccepted(postText("/api/claude/login/cancel", ""))
    }

    override suspend fun claudeCheck(): String = withContext(Dispatchers.IO) {
        JSONObject(postText("/api/claude/check", "")).optString("claude_login", "unknown")
    }

    override suspend fun relayRetest() = withContext(Dispatchers.IO) {
        val json = JSONObject(postText("/api/relay/retest", ""))
        if (!json.optBoolean("accepted", false)) throw IllegalStateException(json.str("reason") ?: "le test n'a pas pu partir")
    }

    override suspend fun sentAppCode(): SentAppCode = withContext(Dispatchers.IO) {
        val json = JSONObject(getText("/api/sent-app/code"))
        if (!json.optBoolean("ok", false)) throw IllegalStateException(json.str("error") ?: "aucune app reçue")
        SentAppCode(json.getString("name"), json.getString("code"), json.str("kind") ?: "code", json.str("repo"), json.str("path"), json.str("ref") ?: "main")
    }

    override suspend fun sentAppSeen() = withContext(Dispatchers.IO) {
        requireAccepted(postText("/api/sent-app/seen", ""))
    }

    // The answer chosen or typed in the widget goes back to the session. The backend sends it like any message, so it can take a while
    // (and answers "busy" while another message is on its way).
    override suspend fun askAnswer(id: String, answer: String) = withContext(Dispatchers.IO) {
        val json = JSONObject(postText("/api/ask/answer", JSONObject().put("id", id).put("answer", answer).toString()))
        if (!json.optBoolean("ok", false)) {
            val error = json.optString("error", "la réponse n'est pas partie")
            if (error == "busy") throw BusyException("a message is already being sent")
            throw IllegalStateException(error)
        }
    }

    override suspend fun agent(action: String, fields: Map<String, Any>): String? = withContext(Dispatchers.IO) {
        val body = JSONObject()
        fields.forEach { (k, v) -> body.put(k, v) }
        val json = JSONObject(postText("/api/agent/$action", body.toString()))
        if (json.optBoolean("ok", false)) null else json.optString("error", "l'agent n'a pas pu")
    }

    override suspend fun campaignStop(): String? = withContext(Dispatchers.IO) {
        val json = JSONObject(postText("/api/campaign/stop", ""))
        if (json.optBoolean("ok", false)) null else json.optString("error", "l'arrêt n'est pas parti")
    }

    override suspend fun resendInstructions(): String? = withContext(Dispatchers.IO) {
        val json = JSONObject(postText("/api/instructions/resend", ""))
        if (json.optBoolean("ok", false)) null else json.optString("error", "les instructions n'ont pas pu être renvoyées")
    }

    override suspend fun llmKey(key: String?): String? = withContext(Dispatchers.IO) {
        val body = if (key == null) JSONObject().put("clear", true) else JSONObject().put("key", key)
        val json = JSONObject(postText("/api/llm/key", body.toString()))
        if (json.optBoolean("ok", false)) null else json.optString("error", "la clé n'a pas été prise")
    }

    override suspend fun llmCommands(text: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        val connection = URI("$baseUrl/api/llm/commands").toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.readTimeout = 25_000
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.outputStream.use { it.write(JSONObject().put("text", text).toString().toByteArray()) }
        val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        if (json.optBoolean("ok", false)) Pair(json.optString("instruction"), json.optString("message")) else null
    }

    override suspend fun voiceSetup() = withContext(Dispatchers.IO) { postText("/api/voice/setup", "{}"); Unit }

    override suspend fun voiceSay(text: String): ByteArray? = withContext(Dispatchers.IO) {
        val connection = URI("$baseUrl/api/voice/say").toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.readTimeout = 60_000
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.outputStream.use { it.write(JSONObject().put("text", text).toString().toByteArray()) }
        if (connection.contentType?.startsWith("audio/") == true) connection.inputStream.use { it.readBytes() } else null
    }

    private fun parseProposals(text: String): ProposalList {
        val json = JSONObject(text)
        val array = json.optJSONArray("proposals")
        return ProposalList(
            json.str("repo"),
            if (array == null) emptyList() else (0 until array.length()).map {
                val v = array.getJSONObject(it)
                Proposal(
                    v.optString("id"), v.optString("name"), v.optString("email"), v.optString("subject"), v.optString("body"), v.optString("price"), v.optString("videoUrl"), v.optString("demoUrl"),
                    v.optString("phone"), v.optString("chatBody"), v.optString("xBody"), v.optString("style"), v.optString("currentUrl"), v.optString("reasons"), v.optString("contacts"),
                    v.optString("type"), v.optString("profileUrl"), v.optString("docsUrl"), v.optString("appUrl"), v.optString("aiwaUrl"),
                )
            },
        )
    }

    override suspend fun proposals(): ProposalList = withContext(Dispatchers.IO) { parseProposals(getText("/api/proposals")) }

    override suspend fun campaignProposals(slug: String): ProposalList = withContext(Dispatchers.IO) {
        parseProposals(getText("/api/proposals" + if (slug.isBlank()) "" else "?campaign=" + java.net.URLEncoder.encode(slug, "UTF-8")))
    }

    override suspend fun campaigns(): CampaignList = withContext(Dispatchers.IO) {
        val json = JSONObject(getText("/api/campaigns"))
        val array = json.optJSONArray("campaigns")
        fun strings(o: JSONObject, key: String): List<String> = o.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
        CampaignList(
            json.str("repo"),
            if (array == null) emptyList() else (0 until array.length()).map {
                val v = array.getJSONObject(it)
                Campaign(v.optString("slug"), v.optString("name"), v.optBoolean("test"), v.optLong("createdAt"), strings(v, "documents"), strings(v, "repos"), strings(v, "makes"), strings(v, "uses"), v.optString("prompt"))
            },
        )
    }

    override suspend fun castPending(tv: String) = withContext(Dispatchers.IO) {
        requireAccepted(postText("/api/cast/pending", JSONObject().put("tv", tv).toString()))
    }

    override suspend fun castClear() = withContext(Dispatchers.IO) {
        requireAccepted(postText("/api/cast/clear", ""))
    }

    override suspend fun loops(): LoopsInfo = withContext(Dispatchers.IO) {
        val json = JSONObject(getText("/api/loops"))
        fun time(o: JSONObject, key: String): Long? = if (o.isNull(key) || !o.has(key)) null else o.optDouble(key).toLong()
        val pairs = json.optJSONArray("pairs")
        val chains = json.optJSONArray("chains")
        LoopsInfo(
            json.optInt("limit", 5),
            if (pairs == null) emptyList() else (0 until pairs.length()).map {
                val p = pairs.getJSONObject(it)
                LoopPair(
                    p.optString("id"), p.optString("builder"), p.optString("critic"), p.optString("builder_title"), p.optString("critic_title"),
                    p.optString("state"), p.optString("turn"), p.optInt("rounds", 0), p.optInt("limit", 5), time(p, "started"), time(p, "created"), time(p, "rolled_back"),
                )
            },
            if (chains == null) emptyList() else (0 until chains.length()).map { c ->
                val chain = chains.getJSONArray(c)
                (0 until chain.length()).map {
                    val e = chain.getJSONObject(it)
                    LoopChainEntry(e.optString("id"), time(e, "started"), time(e, "created"), e.optString("state"))
                }
            },
            json.optJSONArray("links")?.let { a ->
                (0 until a.length()).map {
                    val l = a.getJSONObject(it)
                    LoopLink(l.optString("id"), l.optString("sender"), l.optString("receiver"), l.optString("sender_title"), l.optString("receiver_title"), l.optString("state"), l.optString("turn"), l.optInt("rounds", 0), l.optInt("limit", 5), l.optString("opening"))
                }
            } ?: emptyList(),
            json.optJSONArray("critics")?.let { a -> (0 until a.length()).map { LoopCritic(a.getJSONObject(it).optString("id"), a.getJSONObject(it).optString("title")) } } ?: emptyList(),
        )
    }

    override suspend fun critiques(pairId: String): CritiqueList = withContext(Dispatchers.IO) {
        val json = JSONObject(getText("/api/critiques?pair=" + java.net.URLEncoder.encode(pairId, "UTF-8")))
        if (!json.optBoolean("ok", false)) throw IllegalStateException(json.optString("error").ifBlank { "critiques illisibles" })
        val array = json.optJSONArray("items")
        CritiqueList(
            json.optString("branch"),
            if (array == null) emptyList() else (0 until array.length()).map { val i = array.getJSONObject(it); CritiqueItem(i.optInt("n"), i.optString("name"), i.optLong("size", 0L)) },
            json.optBoolean("resume", false),
        )
    }

    override suspend fun critiqueText(pairId: String, name: String): String = withContext(Dispatchers.IO) {
        val json = JSONObject(getText("/api/critiques?pair=" + java.net.URLEncoder.encode(pairId, "UTF-8") + "&name=" + java.net.URLEncoder.encode(name, "UTF-8")))
        if (!json.optBoolean("ok", false)) throw IllegalStateException(json.optString("error").ifBlank { "lecture impossible" })
        json.optString("text")
    }

    override suspend fun loopsAction(action: String, fields: Map<String, String>): String? = withContext(Dispatchers.IO) {
        val body = JSONObject()
        fields.forEach { (k, v) -> body.put(k, v) }
        val json = JSONObject(postText("/api/loops/$action", body.toString()))
        if (json.optBoolean("ok", false)) null else json.optString("error").ifBlank { "refusé" }
    }

    override suspend fun inputs(): InputList = withContext(Dispatchers.IO) {
        val json = JSONObject(getText("/api/inputs"))
        val array = json.optJSONArray("files")
        InputList(
            json.str("repo"),
            if (array == null) emptyList() else (0 until array.length()).map {
                val v = array.getJSONObject(it)
                InputFile(v.optString("name"), v.optLong("size", 0L))
            },
        )
    }

    /** The super agent answers a QUESTION by itself (it is not sent to a session). (true, the words to say) or (false, why not). */
    fun answer(question: String): Pair<Boolean, String> = try {
        val json = JSONObject(postText("/api/answer", JSONObject().put("question", question).toString()))
        if (json.optBoolean("ok", false)) true to json.optString("text") else false to json.optString("error", "pas de réponse")
    } catch (e: Exception) {
        false to (e.message ?: "?")
    }

    /** The answer sentence by sentence, as Claude writes it: `onSentence` is called for each one at once. True when at least one sentence came. Blocking (call it off the main thread). */
    fun answerStream(question: String, onSentence: (String) -> Unit): Boolean = try {
        val connection = URI("$baseUrl/api/answer_stream").toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.readTimeout = 120_000
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.outputStream.use { it.write(JSONObject().put("question", question).toString().toByteArray()) }
        var got = false
        connection.inputStream.bufferedReader().useLines { lines ->
            for (line in lines) {
                val json = try { JSONObject(line) } catch (e: Exception) { continue }
                val sentence = json.optString("s")
                if (sentence.isNotBlank()) { got = true; onSentence(sentence) }
            }
        }
        got
    } catch (e: Exception) {
        false
    }

    private fun filmJob(json: JSONObject): FilmJob {
        val array = json.optJSONArray("scenes")
        val scenes = if (array == null) emptyList() else (0 until array.length()).map {
            val x = array.getJSONObject(it)
            FilmScene(x.optString("situation"), x.optInt("seconds", 5), x.optString("says"), x.optDouble("price", 0.0), x.optBoolean("done", false))
        }
        return FilmJob(json.optBoolean("ok", false), json.optString("error"), json.optString("id"), json.optString("state"), json.optString("step"), json.optDouble("total", 0.0),
            json.optBoolean("over_cap", false), json.optString("aspect", "9:16"), scenes, json.optString("saved", "none"), json.optString("saved_error"))
    }

    private fun filmPost(path: String, body: JSONObject): JSONObject = try { JSONObject(postText(path, body.toString())) } catch (e: Exception) { JSONObject().put("ok", false).put("error", e.message ?: "?") }

    fun filmStatus(): FilmStatus? = try {
        val j = JSONObject(getText("/api/film/status"))
        FilmStatus(j.optBoolean("key"), j.optBoolean("voice"), j.optDouble("cap", 20.0), j.optString("sample_text"), j.optDouble("per_minute", 8.0))
    } catch (e: Exception) { null }

    /** null when done, else why not. */
    fun filmKey(key: String?): String? {
        val j = if (key == null) filmPost("/api/film/key_clear", JSONObject()) else filmPost("/api/film/key", JSONObject().put("key", key))
        return if (j.optBoolean("ok", false)) null else j.optString("error", "?")
    }
    fun filmCap(usd: Double): String? { val j = filmPost("/api/film/cap", JSONObject().put("cap", usd)); return if (j.optBoolean("ok", false)) null else j.optString("error", "?") }
    fun filmVoice(base64: String, ext: String): String? { val j = filmPost("/api/film/voice", JSONObject().put("data", base64).put("ext", ext)); return if (j.optBoolean("ok", false)) null else j.optString("error", "?") }
    fun filmPrepare(direction: String, says: String, aspect: String, photosBase64: List<String>, consent: Boolean, kind: String = "me", photosProductBase64: List<String> = emptyList(), swap: String = "product"): FilmJob =
        filmJob(filmPost("/api/film/prepare", JSONObject().put("direction", direction).put("says", says).put("aspect", aspect).put("consent", consent).put("kind", kind).put("swap", swap)
            .put("photos", org.json.JSONArray(photosBase64)).put("photos_product", org.json.JSONArray(photosProductBase64))))

    /** The person's own video, for a replacement: sent as it is (raw bytes), the quote comes back. Blocking (off the main thread). */
    fun filmSource(id: String, input: java.io.InputStream, length: Long): FilmJob = try {
        val connection = URI("$baseUrl/api/film/source?id=$id").toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.readTimeout = 300_000
        if (length > 0) connection.setFixedLengthStreamingMode(length) else connection.setChunkedStreamingMode(0)
        connection.setRequestProperty("Content-Type", "application/octet-stream")
        connection.outputStream.use { out -> input.use { it.copyTo(out) } }
        filmJob(JSONObject(connection.inputStream.bufferedReader().use { it.readText() }))
    } catch (e: Exception) { filmJob(JSONObject().put("ok", false).put("error", e.message ?: "?")) }
    fun filmEdit(id: String, index: Int, situation: String?, says: String?, remove: Boolean = false): FilmJob =
        filmJob(filmPost("/api/film/edit", JSONObject().put("id", id).put("index", index).apply { if (situation != null) put("situation", situation); if (says != null) put("says", says); put("remove", remove) }))
    fun filmStart(id: String): FilmJob = filmJob(filmPost("/api/film/start", JSONObject().put("id", id).put("confirmed", true)))
    fun externalStatus(): ExternalStatus? = try { externalOf(JSONObject(getText("/api/external/status"))) } catch (e: Exception) { null }
    fun externalOpen(open: Boolean): ExternalStatus? = try { externalOf(filmPost("/api/external/open", JSONObject().put("open", open))) } catch (e: Exception) { null }
    fun externalConfig(url: String, key: String): ExternalStatus? = try { externalOf(filmPost("/api/external/config", JSONObject().put("url", url).put("key", key))) } catch (e: Exception) { null }
    private fun externalOf(j: JSONObject): ExternalStatus {
        val a = j.optJSONArray("inbox")
        return ExternalStatus(j.optBoolean("open"), j.optBoolean("configured"), j.optString("url"), if (a == null) emptyList() else (0 until a.length()).map { val m = a.getJSONObject(it); m.optString("who") + " : " + m.optString("text") }, if (j.optBoolean("ok", true)) null else j.optString("error"))
    }
    fun stockStatus(): StockStatus? = try {
        val j = JSONObject(getText("/api/stock/status"))
        val a = j.optJSONArray("clips")
        StockStatus(j.optBoolean("pexels"), j.optBoolean("pixabay"),
            if (a == null) emptyList() else (0 until a.length()).map { val c = a.getJSONObject(it); StockSaved(c.optString("id"), c.optString("title"), c.optString("author"), c.optString("saved", "none"), c.optString("saved_error")) })
    } catch (e: Exception) { null }

    /** null when done, else why not. */
    fun stockKey(source: String, key: String?): String? {
        val j = if (key == null) filmPost("/api/stock/key_clear", JSONObject().put("source", source)) else filmPost("/api/stock/key", JSONObject().put("source", source).put("key", key))
        return if (j.optBoolean("ok", false)) null else j.optString("error", "?")
    }

    /** (the clips, null) or (empty, why not). */
    fun stockSearch(query: String, source: String, portrait: Boolean): Pair<List<StockClip>, String?> {
        val j = filmPost("/api/stock/search", JSONObject().put("query", query).put("source", source).put("portrait", portrait))
        if (!j.optBoolean("ok", false)) return emptyList<StockClip>() to j.optString("error", "?")
        val a = j.optJSONArray("results") ?: return emptyList<StockClip>() to null
        return (0 until a.length()).map {
            val c = a.getJSONObject(it)
            StockClip(c.optString("id"), c.optString("title"), c.optInt("duration", 0), c.optString("author"), c.optString("licence"), c.optString("source"), c.toString())
        } to null
    }

    fun stockDownload(clip: StockClip): String? { val j = filmPost("/api/stock/download", JSONObject().put("clip", JSONObject(clip.raw))); return if (j.optBoolean("ok", false)) null else j.optString("error", "?") }
    fun stockPublish(id: String): Pair<Boolean, String?> { val j = filmPost("/api/stock/publish", JSONObject().put("id", id)); return if (j.optBoolean("ok", false)) true to null else false to j.optString("error", "?") }

    fun filmInfo(): FilmInfo? = try {
        val j = JSONObject(getText("/api/film/info"))
        FilmInfo(j.optString("repo").takeIf { it.isNotBlank() && it != "null" }, if (j.isNull("public")) null else j.optBoolean("public"), j.optBoolean("gh"))
    } catch (e: Exception) { null }

    /** (true, null) when the saving started; (false, why, and the message tells to connect gh). */
    fun filmPublish(id: String): Pair<Boolean, String?> {
        val j = filmPost("/api/film/publish", JSONObject().put("id", id))
        return if (j.optBoolean("ok", false)) true to null else false to j.optString("error", "?")
    }

    fun filmGet(id: String): FilmJob = filmJob(try { JSONObject(getText("/api/film/get?id=$id")) } catch (e: Exception) { JSONObject().put("ok", false).put("error", e.message ?: "?") })

    /** The pictures of the repository in use (the files of docs/images/), the most recently changed first. */
    fun images(): Pair<String?, List<ImageItem>> = try {
        val json = JSONObject(getText("/api/images"))
        val array = json.optJSONArray("images")
        json.str("repo") to (if (array == null) emptyList() else (0 until array.length()).map { val d = array.getJSONObject(it); ImageItem(d.optString("name"), d.optString("url"), d.optLong("ts", 0L)) })
    } catch (e: Exception) {
        null to emptyList()
    }

    /** Deletes one file of the media folders (kind: "video" | "image"); `path` is "docs" for a video that lies at the root of docs/. */
    fun mediaDelete(kind: String, name: String, path: String? = null): MediaDeleteResult = try {
        val body = JSONObject().put("kind", kind).put("name", name).apply { if (path != null) put("path", path) }
        val json = JSONObject(postText("/api/media_delete", body.toString()))
        MediaDeleteResult(json.optBoolean("ok", false), json.optString("error"), json.optString("needs") == "session")
    } catch (e: Exception) {
        MediaDeleteResult(false, e.message ?: "?", false)
    }

    /** The documents of the repository in use (the files of docs/), the most recently changed first; `ts` is epoch seconds, 0 when unknown. */
    fun documents(): List<DocItem> = try {
        val array = JSONObject(getText("/api/documents")).optJSONArray("documents")
        if (array == null) emptyList() else (0 until array.length()).map { val d = array.getJSONObject(it); DocItem(d.optString("name"), d.optString("url"), d.optLong("ts", 0L)) }
    } catch (e: Exception) {
        emptyList()
    }

    override suspend fun videos(): VideoList = withContext(Dispatchers.IO) {
        val json = JSONObject(getText("/api/videos"))
        val array = json.optJSONArray("videos")
        VideoList(
            json.str("repo"),
            if (array == null) emptyList() else (0 until array.length()).map {
                val v = array.getJSONObject(it)
                VideoItem(v.optString("name"), v.optLong("size", 0L), v.optString("url"), v.optString("ts"))
            },
        )
    }
}

private fun ciHint(json: org.json.JSONObject?): CiHint? {
    if (json == null) return null
    val steps = json.optJSONArray("steps")
    return CiHint(
        json.optString("code"), json.optString("title"),
        if (steps == null) emptyList() else (0 until steps.length()).map { steps.getString(it) },
        json.optString("url"),
    )
}
