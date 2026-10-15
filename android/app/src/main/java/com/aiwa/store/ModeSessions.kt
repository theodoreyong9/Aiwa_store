package com.aiwa.store

import android.content.Context

/**
 * Every mode of the widget (Vidéos, Documents, Campagne, Page, App, Store, Aiwa) has its OWN session: what was said in one is not in the others. The session of a mode is
 * remembered when the person leaves it and taken again when they come back; the first time in a mode there is none, so a new one is made with the first message.
 */
object ModeSessions {
    private const val PREFS = "aiwa_mode_sessions"

    /** The key of a mode: "video" while the Vidéo view is shown, else the mode of the selector (documents, campaign, pages, android, store, aiwa). */
    fun key(videoView: Boolean, deploy: String): String = if (videoView) "video" else deploy

    fun remember(context: Context, key: String, sessionId: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, sessionId ?: "new").apply()
    }

    /** The session of the mode, null when it has none yet (or a new one was wanted). */
    fun recall(context: Context, key: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null)?.takeIf { it != "new" }
}
