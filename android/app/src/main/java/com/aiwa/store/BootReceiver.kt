package com.aiwa.store
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Reported live: the widget's mic still visibly opened the app on its
 * first tap. The keep-alive service (KeepAliveService.kt) only started
 * when MainActivity was opened, so after a reboot or an app update the
 * process was cold again until then. Starting it on boot and on package
 * replacement closes that gap.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!dictationEnabled(context)) return       // the dictation module is optional: nothing runs until it is turned on
        try {
            ContextCompat.startForegroundService(context, Intent(context, KeepAliveService::class.java))
        } catch (err: Exception) {
            // Background start refused by the OS: the next app open or
            // widget mic tap starts it instead.
        }
    }
}
