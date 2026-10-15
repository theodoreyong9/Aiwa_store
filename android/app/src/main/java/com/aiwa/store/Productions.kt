package com.aiwa.store

import android.content.Context

/** What is known to exist without asking the network again: whether the list of videos had something in it the last time it was read (the widget then shows the Vidéos button). */
object Productions {
    private const val PREFS = "aiwa_productions"
    fun has(context: Context, kind: String): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(kind, false)
    fun set(context: Context, kind: String, present: Boolean) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(kind, present).apply() }
}
