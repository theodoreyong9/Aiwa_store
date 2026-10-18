package com.aiwa.store

import android.content.Context

/** What the widget remembers about its own view. */
object WidgetStyle {
    private const val PREFS = "aiwa_widget_style"

    /** The « Vidéo » button of the Aiwa shape is a view, not a mode of the selector: whether it is the one shown. */
    fun videoView(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("video_view", false)

    fun setVideoView(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("video_view", on).apply()
    }
}
