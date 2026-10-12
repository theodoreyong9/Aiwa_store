package com.aiwa.store

import android.content.Context

/** The shape of the widget: the classic bands, or a pair of glasses (two lenses and the microphone on the bridge). Chosen in the settings list. */
object WidgetStyle {
    const val CLASSIC = "classic"
    const val GLASSES = "glasses"
    private const val PREFS = "aiwa_widget_style"

    fun current(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("shape", CLASSIC).let { if (it == GLASSES) GLASSES else CLASSIC }

    fun set(context: Context, shape: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("shape", if (shape == GLASSES) GLASSES else CLASSIC).apply()
    }
}
