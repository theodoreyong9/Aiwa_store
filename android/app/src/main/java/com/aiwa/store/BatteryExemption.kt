package com.aiwa.store
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Without the exemption from the battery's optimisation, Android may stop Aiwa's background service (the widget stops following Claude, the updates and the
 * brain's restarts stop with it). The widget says so, and one tap shows Android's own question « Autoriser Aiwa à fonctionner en arrière-plan ? ».
 * The line is shown at most twice if the person refuses: it is a choice.
 */
object BatteryExemption {
    private const val MAX_ASKS = 2

    fun isExempt(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) ?: true

    private fun prefs(context: Context) = context.getSharedPreferences("aiwa_battery", Context.MODE_PRIVATE)

    /** Whether the widget should say it: not exempt, and not refused twice already. */
    fun shouldWarn(context: Context): Boolean = !isExempt(context) && prefs(context).getInt("asks", 0) < MAX_ASKS

    fun noteAsked(context: Context) = prefs(context).edit().putInt("asks", prefs(context).getInt("asks", 0) + 1).apply()

    fun requestIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
