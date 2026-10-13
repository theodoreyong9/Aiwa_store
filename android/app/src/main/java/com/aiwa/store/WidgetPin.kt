package com.aiwa.store

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/**
 * Putting the widget on the home screen. Android never lets an application place a widget by itself: it can only ASK, and the system shows its own window
 * (« Ajouter le widget à l'écran d'accueil ? ») where one tap on « Ajouter » does it. Some launchers do not take the request: the person is then told the gesture by hand.
 * It is asked once, when the application is first opened and no widget is there; the menu d'Aiwa asks again whenever the person wants.
 */
object WidgetPin {
    private const val HAND_GESTURE = "Ajoute le widget toi-même : appui long sur l'écran d'accueil, puis Widgets, puis Aiwa Store."

    private fun provider(context: Context) = ComponentName(context, AiwaWidgetReceiver::class.java)

    fun hasWidget(context: Context): Boolean = try {
        AppWidgetManager.getInstance(context).getAppWidgetIds(provider(context)).isNotEmpty()
    } catch (err: Exception) { false }

    /** Asks the system to add the widget. Returns false when the launcher cannot be asked (the gesture by hand is then said, if `explain`). */
    fun request(context: Context, explain: Boolean): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        val supported = try { manager.isRequestPinAppWidgetSupported } catch (err: Exception) { false }
        val asked = supported && try { manager.requestPinAppWidget(provider(context), null, null) } catch (err: Exception) { false }
        if (!asked && explain) toastOnMain(context.applicationContext, HAND_GESTURE)
        return asked
    }

    /**
     * Whenever the application is opened and there is no widget: one request, at most a few times and not twice in a day. NOT « once for ever »: a flag kept for ever was restored
     * by Android's backup after a reinstall, or set at a time when a widget already existed, and the request was then never made again. The counter is in storage that
     * the backup does not carry (noBackupFilesDir). When the launcher cannot be asked, the gesture by hand is said (at most twice).
     */
    fun offerOnce(context: Context) {
        if (hasWidget(context)) return
        val file = java.io.File(context.noBackupFilesDir, "widget_pin.txt")
        val parts = try { file.readText().trim().split(' ') } catch (err: Exception) { emptyList() }
        val count = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val last = parts.getOrNull(1)?.toLongOrNull() ?: 0L
        val now = System.currentTimeMillis()
        if (count >= 4 || now - last < 20 * 60 * 60 * 1000L) return
        try { file.writeText("${count + 1} $now") } catch (err: Exception) { }
        request(context, explain = count < 2)
    }
}
