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

    private fun get(url: String): String? = try {
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = 8000; readTimeout = 8000
            setRequestProperty("Accept", "application/vnd.github+json")
            if (responseCode == 200) inputStream.bufferedReader().readText() else null
        }
    } catch (e: Exception) { null }

    /** Looks again (at most every two minutes per repository); the widget is redrawn when something changed. */
    suspend fun refresh(context: Context, repo: String?) {
        if (repo == null || !Regex("[A-Za-z0-9._-]+/[A-Za-z0-9._-]+").matches(repo)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("at:$repo", 0L) < 120_000L) return
        prefs.edit().putLong("at:$repo", now).apply()
        val (video, docs) = withContext(Dispatchers.IO) {
            val release = get("https://api.github.com/repos/$repo/releases/tags/videos")?.let { runCatching { JSONObject(it).optJSONArray("assets")?.length() ?: 0 }.getOrDefault(0) } ?: 0
            val folder = get("https://api.github.com/repos/$repo/contents/aiwa-videos")?.let { runCatching { JSONArray(it).length() }.getOrDefault(0) } ?: 0
            val documents = get("https://api.github.com/repos/$repo/contents/aiwa-documents")?.let { runCatching { JSONArray(it).length() }.getOrDefault(0) } ?: 0
            (release > 0 || folder > 0) to (documents > 0)
        }
        val changed = has(context, repo, "video") != video || has(context, repo, "documents") != docs
        set(context, repo, "video", video); set(context, repo, "documents", docs)
        if (changed) AiwaWidget().updateAll(context)
    }
}
