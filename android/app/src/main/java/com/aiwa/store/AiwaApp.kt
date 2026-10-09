package com.aiwa.store
import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * The dictation module (the widget, the Termux backend, the keep-alive service) is OPTIONAL: the Store and the wallet
 * need none of it. It runs once it has been turned on — by adding the widget to the home screen, or by opening its
 * screen (MainActivity) — and not before: a user who only wants the Store gets no service, no permission request and no
 * notification.
 */
fun dictationEnabled(context: Context): Boolean {
    val app = context.applicationContext
    if (app.getSharedPreferences("aiwa_module", Context.MODE_PRIVATE).getBoolean("dictation", false)) return true
    return try {
        AppWidgetManager.getInstance(app).getAppWidgetIds(ComponentName(app, AiwaWidgetReceiver::class.java)).isNotEmpty()
    } catch (err: Exception) {
        false
    }
}

fun enableDictation(context: Context) {
    context.applicationContext.getSharedPreferences("aiwa_module", Context.MODE_PRIVATE).edit().putBoolean("dictation", true).apply()
}

/**
 * Makes sure the keep-alive service runs: it polls the backend and brings it
 * back through Termux when it is down. Reported live: the backend only came up
 * after opening the Aiwa app, because that was the one thing that started the
 * service — a widget that was merely tapped (or redrawn) never did. Now every
 * way Aiwa's process comes to life calls this. Android may refuse a service
 * start from the background (a widget redraw with no user action); that is
 * harmless, the next tap or app open does it.
 */
fun wakeAiwa(context: Context) {
    if (!dictationEnabled(context)) return
    try {
        ContextCompat.startForegroundService(context, Intent(context, KeepAliveService::class.java))
    } catch (err: Exception) {
        // Refused in the background: retried at the next widget tap or app open.
    }
}

/** Runs in every process Aiwa starts (widget, service, activity): brings back the last known state. */
class AiwaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AiwaRepository.restore(this)
        AccessGate.load(this)
        wakeAiwa(this)
    }
}
