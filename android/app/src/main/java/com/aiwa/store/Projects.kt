package com.aiwa.store

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A named pair: the session and the repository the two pills hold together ("new" = a new session). Nothing else links the two. */
data class Project(val name: String, val repo: String, val session: String)

/** The names of the « Projet » button, kept on the phone. Naming a pair changes nothing about the pills; picking one sets both. */
object Projects {
    private const val PREFS = "aiwa_projects"
    private const val KEY = "list"

    fun all(context: Context): List<Project> = try {
        val array = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name"); val repo = o.optString("repo")
            if (name.isBlank() || repo.isBlank()) null else Project(name, repo, o.optString("session").ifBlank { "new" })
        }
    } catch (err: Exception) { emptyList() }

    private fun save(context: Context, list: List<Project>) {
        val array = JSONArray()
        list.forEach { array.put(JSONObject().put("name", it.name).put("repo", it.repo).put("session", it.session)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }

    /** A name already used is replaced: the same name never names two pairs. */
    fun add(context: Context, project: Project) = save(context, all(context).filter { it.name != project.name } + project)

    fun remove(context: Context, name: String) = save(context, all(context).filter { it.name != name })

    fun nameOf(context: Context, repo: String?, session: String?): String? =
        all(context).firstOrNull { it.repo == repo && it.session == (session ?: "new") }?.name
}
