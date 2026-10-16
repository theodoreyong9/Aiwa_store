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

    /** (status, body): status 0 when the network did not answer. A 403 (rate limit) or a 5xx is "unknown", not "absent". */
    private fun get(url: String): Pair<Int, String?> = try {
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = 8000; readTimeout = 8000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "aiwa")
            val code = responseCode
            code to (if (code == 200) inputStream.bufferedReader().readText() else null)
        }
    } catch (e: Exception) { 0 to null }

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
        (names("https://api.github.com/repos/$repo/contents/docs") ?: emptyList()).filter { n -> n.lowercase().let { l -> ext.any { l.endsWith(it) } } }.sorted()
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
        val found = withContext(Dispatchers.IO) {
            val api = "https://api.github.com/repos/$repo"
            val out = HashMap<String, Boolean?>()
            // ONE look at docs/: the videos and the documents of a repository live there, the same for all
            val docsDir = names("$api/contents/docs")
            val videosDir = names("$api/contents/docs/videos")
            fun has(ext: List<String>) = docsDir?.any { n -> n.lowercase().let { l -> ext.any { l.endsWith(it) } } }
            val videoExt = listOf(".mp4", ".webm", ".mov")
            // the videos of docs/videos/ (and the films a project keeps at the root of docs/)
            out["video"] = if (videosDir?.any { n -> n.lowercase().let { l -> videoExt.any { l.endsWith(it) } } } == true || has(videoExt) == true) true
                else if (videosDir == null || docsDir == null) null else false
            out["documents"] = has(listOf(".md", ".pdf", ".docx", ".xlsx", ".pptx", ".txt", ".csv"))
            val owner = repo.substringBefore('/'); val name = repo.substringAfter('/')
            out["page"] = get("https://$owner.github.io/$name/").first.let { if (it == 200) true else if (it == 404) false else null }
            out["apk"] = get("$api/releases?per_page=10").let { (c, b) ->
                if (c == 200 && b != null) runCatching { JSONArray(b).let { rs -> (0 until rs.length()).any { i -> rs.getJSONObject(i).optJSONArray("assets")?.let { a -> (0 until a.length()).any { j -> a.getJSONObject(j).optString("name").lowercase().endsWith(".apk") } } == true } } }.getOrNull()
                else if (c == 404) false else null
            }
            out["aiwa"] = names("$api/contents/aiwa-apps")?.any { it.endsWith(".aiwa.html") }
            out
        }
        var changed = false
        found.forEach { (kind, value) ->
            if (value != null) { if (has(context, repo, kind) != value) changed = true; set(context, repo, kind, value) }
        }
        if (changed) AiwaWidget().updateAll(context)
    }
}
