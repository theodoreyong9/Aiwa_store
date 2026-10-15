package com.aiwa.store

import android.content.Context

/**
 * A repository has its OWN session: the session in use is remembered when the repository is left and taken again when it is chosen again; the first time there is none, so the
 * first message makes a new one. (The modes are not tied to sessions: what a repository holds — videos, documents, a page, an APK, an Aiwa app — is read from the repository.)
 */
object ModeSessions {
    private const val PREFS = "aiwa_repo_sessions"

    fun remember(context: Context, repo: String, sessionId: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(repo, sessionId ?: "new").apply()
    }

    /** The session of the repository, null when it has none yet (or a new one was wanted). */
    fun recall(context: Context, repo: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(repo, null)?.takeIf { it != "new" }
}
