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
     * Looks at what the repository holds, by the layout Aiwa gives it (the same for every repository): videos (the release `videos`, the folder `aiwa-videos`, or an MP4 in `docs`),
     * documents (`aiwa-documents`), the page (the GitHub Pages address answers), the APK (a release with an .apk file), the Aiwa app (`aiwa-apps/*.aiwa.html`). A question that GitHub did not
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
            val release = get("$api/releases/tags/videos").let { (c, b) -> if (c == 404) 0 else if (c == 200 && b != null) runCatching { JSONObject(b).optJSONArray("assets")?.length() ?: 0 }.getOrNull() else null }
            val folder = count("$api/contents/aiwa-videos")
            val docsDir = names("$api/contents/docs")
            val mp4 = docsDir?.count { n -> n.lowercase().let { it.endsWith(".mp4") || it.endsWith(".webm") || it.endsWith(".mov") } }
            out["video"] = if ((release ?: 0) > 0 || (folder ?: 0) > 0 || (mp4 ?: 0) > 0) true else if (release == null || folder == null || mp4 == null) null else false
            out["documents"] = count("$api/contents/aiwa-documents")?.let { it > 0 }
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
