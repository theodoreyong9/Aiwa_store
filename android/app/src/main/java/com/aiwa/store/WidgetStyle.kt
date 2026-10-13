package com.aiwa.store

import android.content.Context

/**
 * The shape of the widget: the classic bands, a pair of glasses (two lenses and the microphone on the bridge), or « Aiwa » (the modes in a row at the top, what the mode
 * needs, the voice, and a bottom row). Chosen in the settings list.
 */
object WidgetStyle {
    const val CLASSIC = "classic"
    const val GLASSES = "glasses"
    const val AIWA = "aiwa"
    private const val PREFS = "aiwa_widget_style"

    fun current(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("shape", CLASSIC).let { if (it == GLASSES || it == AIWA) it!! else CLASSIC }

    fun set(context: Context, shape: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("shape", if (shape == GLASSES || shape == AIWA) shape else CLASSIC).apply()
    }

    /** The « Vidéo » button of the Aiwa shape is a view, not a mode of the selector: whether it is the one shown. */
    fun videoView(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("video_view", false)

    fun setVideoView(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("video_view", on).apply()
    }
}
