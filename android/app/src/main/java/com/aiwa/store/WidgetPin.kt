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
    private const val PREFS = "aiwa_widget_pin"
    private const val ASKED = "asked"
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

    /** At the first opening, when there is no widget yet: one request, once (never again by itself, whatever the answer). */
    fun offerOnce(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(ASKED, false)) return
        prefs.edit().putBoolean(ASKED, true).apply()
        if (hasWidget(context)) return
        request(context, explain = false)     // a launcher that cannot be asked is not nagged at the first opening: the menu says the gesture
    }
}
