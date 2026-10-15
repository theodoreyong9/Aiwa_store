package com.aiwa.store

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The shape of the widget: « Light » (the default: the five kinds, the session and the repository, the microphone, and a bottom row) or a pair of glasses (the same buttons in the left lens, the Store in the right one). Chosen in the settings list.
 */
object WidgetStyle {
    const val GLASSES = "glasses"
    const val LIGHT = "light"
    private const val PREFS = "aiwa_widget_style"

    fun current(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("shape", LIGHT).let { if (it == GLASSES) it!! else LIGHT }

    fun set(context: Context, shape: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("shape", if (shape == GLASSES) shape else LIGHT).apply()
    }

    private val refreshing = Mutex()
    @Volatile private var again = false

    /**
     * Redraws the widgets after a change of shape: ONE update at a time (several changes in a row used to pile up their full redraws and the launcher's own
     * re-reading), and a second one a moment later in case a change came while one was running.
     */
    fun refresh(context: Context) {
        val app = context.applicationContext
        again = true
        CoroutineScope(Dispatchers.Default).launch {
            if (!refreshing.tryLock()) return@launch     // one is running: it will see `again`
            try {
                while (again) {
                    again = false
                    AiwaWidget().updateAll(app)
                    delay(600)
                }
            } finally { refreshing.unlock() }
        }
    }

    /** The « Vidéo » button of the Aiwa shape is a view, not a mode of the selector: whether it is the one shown. */
    fun videoView(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("video_view", false)

    fun setVideoView(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("video_view", on).apply()
    }
}
