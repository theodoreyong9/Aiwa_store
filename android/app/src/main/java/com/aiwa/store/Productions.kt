package com.aiwa.store

import android.content.Context
import androidx.glance.appwidget.updateAll
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * What the repository in use already holds, for the grey / coloured buttons of the Light widget: videos (the release `videos` or the folder `aiwa-videos`) and documents (the
 * folder `aiwa-documents`). Read from GitHub without a token, so only a PUBLIC repository can say (a private one stays grey); asked again after two minutes at the earliest.
 */
object Productions {
    private const val PREFS = "aiwa_productions"

    fun has(context: Context, repo: String?, kind: String): Boolean =
        repo != null && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("$kind:$repo", false)

    fun set(context: Context, repo: String, kind: String, present: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("$kind:$repo", present).apply()
    }

    // The answers of GitHub's API, kept with their ETag: asked again with it, GitHub answers 304 « nothing changed », which does NOT count in its limit of 60 questions an hour
    // without a token. Set when a look begins (refresh); without it a question is simply asked in full.
    @Volatile private var memory: android.content.SharedPreferences? = null
    private const val MAX_KEPT = 300_000

    /** (status, body): status 0 when the network did not answer. A 403 (rate limit) or a 5xx is "unknown", not "absent". A 304 gives the kept answer as a 200. */
    private fun get(url: String): Pair<Int, String?> = try {
        val conditional = url.startsWith("https://api.github.com/")
        val kept = if (conditional) memory else null
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = 8000; readTimeout = 8000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "aiwa")
            val tag = kept?.getString("etag-of:$url", null)
            val body = kept?.getString("body-of:$url", null)
            if (tag != null && body != null) setRequestProperty("If-None-Match", tag)
            val code = responseCode
            when {
                code == 304 && body != null -> 200 to body
                code == 200 -> {
                    val text = inputStream.bufferedReader().readText()
                    val newTag = getHeaderField("ETag")
                    if (kept != null && newTag != null && text.length <= MAX_KEPT) kept.edit().putString("etag-of:$url", newTag).putString("body-of:$url", text).apply()
                    200 to text
                }
                else -> code to null
            }
        }
    } catch (e: Exception) { 0 to null }

    /**
     * The names in a folder of a repository, read from github.com's own page of the folder (asked as JSON) and NOT from the API: those pages are not in the API's limit of 60 an hour.
     * null when it did not answer (the API is then tried); an absent folder (404) is an empty list.
     */
    private fun webNames(repo: String, path: String): List<String>? = try {
        (URL("https://github.com/$repo/tree/HEAD/$path").openConnection() as HttpURLConnection).run {
            connectTimeout = 8000; readTimeout = 8000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "aiwa")
            when (responseCode) {
                404 -> emptyList()
                200 -> {
                    val items = JSONObject(inputStream.bufferedReader().readText()).getJSONObject("payload").getJSONObject("codeViewTreeRoute").getJSONObject("tree").getJSONArray("items")
                    (0 until items.length()).map { items.getJSONObject(it).optString("name") }
                }
                else -> null
            }
        }
    } catch (e: Exception) { null }

    /** The folder's names: the web page first, then the API (with its kept answer). */
    private fun folder(repo: String, path: String): List<String>? = webNames(repo, path) ?: names("https://api.github.com/repos/$repo/contents/$path")

    /** Does a recent release of the repository hold an .apk? From the releases feed and the pages of the releases (github.com, not the API); null when they did not answer. */
    private fun webApk(repo: String): Boolean? = try {
        val feed = (URL("https://github.com/$repo/releases.atom").openConnection() as HttpURLConnection).run {
            connectTimeout = 8000; readTimeout = 8000
            setRequestProperty("User-Agent", "aiwa")
            if (responseCode == 200) inputStream.bufferedReader().readText() else null
        }
        if (feed == null) null else {
            val tags = Regex("""/releases/tag/([^"<\s]+)""").findAll(feed).map { it.groupValues[1] }.distinct().take(5).toList()
            var answered = true
            var found = false
            for (tag in tags) {
                val page = (URL("https://github.com/$repo/releases/expanded_assets/$tag").openConnection() as HttpURLConnection).run {
                    connectTimeout = 8000; readTimeout = 8000
                    setRequestProperty("User-Agent", "aiwa")
                    if (responseCode == 200) inputStream.bufferedReader().readText() else null
                }
                if (page == null) answered = false else if (Regex("""href="[^"]*/releases/download/[^"]+\.apk"""", RegexOption.IGNORE_CASE).containsMatchIn(page)) { found = true; break }
            }
            if (found) true else if (answered) false else null
        }
    } catch (e: Exception) { null }

    private fun count(url: String, pick: (JSONArray) -> Int = { it.length() }): Int? {
        val (code, body) = get(url)
        return when {
            code == 404 -> 0
            code == 200 && body != null -> runCatching { pick(JSONArray(body)) }.getOrNull()
            else -> null
        }
    }

    private fun names(url: String): List<String>? {
        val (code, body) = get(url)
        return when {
            code == 404 -> emptyList()
            code == 200 && body != null -> runCatching { JSONArray(body).let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("name") } } }.getOrNull()
            else -> null
        }
    }

    /**
     * The fast watch of the videos: the folder `docs/videos` asked with the ETag of the last answer, so that GitHub answers 304 (which does not count in its limit of 60 questions an hour
     * without a token) while nothing changed, and the new film shows on the button within a minute. Asked from the keep-alive service about every 45 seconds.
     */
    suspend fun watchVideos(context: Context, repo: String?) {
        if (repo == null || !Regex("[A-Za-z0-9._-]+/[A-Za-z0-9._-]+").matches(repo)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val found = withContext(Dispatchers.IO) {
            try {
                (URL("https://api.github.com/repos/$repo/contents/docs/videos").openConnection() as HttpURLConnection).run {
                    connectTimeout = 8000; readTimeout = 8000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "aiwa")
                    prefs.getString("etag:$repo", null)?.let { setRequestProperty("If-None-Match", it) }
                    when (val code = responseCode) {
                        304 -> null                                       // nothing changed
                        404 -> { prefs.edit().remove("etag:$repo").apply(); false }
                        200 -> {
                            getHeaderField("ETag")?.let { prefs.edit().putString("etag:$repo", it).apply() }
                            val a = JSONArray(inputStream.bufferedReader().readText())
                            (0 until a.length()).any { i -> a.getJSONObject(i).optString("name").lowercase().let { n -> n.endsWith(".mp4") || n.endsWith(".webm") || n.endsWith(".mov") } }
                        }
                        else -> null                                      // rate limit, no network: what was known stays
                    }
                }
            } catch (e: Exception) { null }
        }
        if (found != null) {
            val changed = has(context, repo, "video") != found
            set(context, repo, "video", found)
            // a new film (even when there was one already): the Videos window asks the brain again, and the widget is redrawn when the button changes
            if (changed) AiwaWidget().updateAll(context)
        }
    }

    /** The document files of the folder `docs` of a repository (the names), for the lists to tick; empty when GitHub does not answer (a private repository). */
    suspend fun documentFiles(repo: String): List<String> = withContext(Dispatchers.IO) {
        val ext = listOf(".md", ".pdf", ".docx", ".xlsx", ".pptx", ".txt", ".csv", ".html")
        (folder(repo, "docs") ?: emptyList()).filter { n -> n.lowercase().let { l -> ext.any { l.endsWith(it) } } }.sorted()
    }

    /**
     * Looks at what the repository holds, by the layout Aiwa gives it (the same for every repository): videos and documents (the files of `docs`), the page (the GitHub Pages address answers), the APK (a release with an .apk file), the Aiwa app (an `.aiwa.html` file in `aiwa-apps`). A question that GitHub did not
     * answer (rate limit, no network) leaves what was known: a button does not go grey for a failed request. At most every ten minutes per repository (GitHub allows 60 questions an hour
     * without a token); the widget is redrawn when something changed.
     */
    suspend fun refresh(context: Context, repo: String?, force: Boolean = false) {
        if (repo == null || !Regex("[A-Za-z0-9._-]+/[A-Za-z0-9._-]+").matches(repo)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong("at:$repo", 0L) < 600_000L) return
        prefs.edit().putLong("at:$repo", now).apply()
        memory = prefs
        val found = withContext(Dispatchers.IO) {
            val api = "https://api.github.com/repos/$repo"
            val out = HashMap<String, Boolean?>()
            // ONE look at docs/: the videos and the documents of a repository live there, the same for all
            val docsDir = folder(repo, "docs")
            val videosDir = folder(repo, "docs/videos")
            fun has(ext: List<String>) = docsDir?.any { n -> n.lowercase().let { l -> ext.any { l.endsWith(it) } } }
            val videoExt = listOf(".mp4", ".webm", ".mov")
            // the videos of docs/videos/ (and the films a project keeps at the root of docs/)
            out["video"] = if (videosDir?.any { n -> n.lowercase().let { l -> videoExt.any { l.endsWith(it) } } } == true || has(videoExt) == true) true
                else if (videosDir == null || docsDir == null) null else false
            out["documents"] = has(listOf(".md", ".pdf", ".docx", ".xlsx", ".pptx", ".txt", ".csv"))
            val owner = repo.substringBefore('/'); val name = repo.substringAfter('/')
            out["page"] = get("https://$owner.github.io/$name/").first.let { if (it == 200) true else if (it == 404) false else null }
            out["apk"] = webApk(repo) ?: get("$api/releases?per_page=10").let { (c, b) ->
                if (c == 200 && b != null) runCatching { JSONArray(b).let { rs -> (0 until rs.length()).any { i -> rs.getJSONObject(i).optJSONArray("assets")?.let { a -> (0 until a.length()).any { j -> a.getJSONObject(j).optString("name").lowercase().endsWith(".apk") } } == true } } }.getOrNull()
                else if (c == 404) false else null
            }
            out["aiwa"] = folder(repo, "aiwa-apps")?.any { it.endsWith(".aiwa.html") }
            out
        }
        // A question GitHub did not answer (rate limit, no network, a slow answer) is asked again in a minute, not after ten: a button must not stay grey for ten minutes after one failure
        if (found.values.any { it == null }) prefs.edit().putLong("at:$repo", now - 540_000L).apply()
        android.util.Log.i("AiwaProductions", "$repo: $found")
        var changed = false
        found.forEach { (kind, value) ->
            if (value != null) { if (has(context, repo, kind) != value) changed = true; set(context, repo, kind, value) }
        }
        if (changed) AiwaWidget().updateAll(context)
    }
}
