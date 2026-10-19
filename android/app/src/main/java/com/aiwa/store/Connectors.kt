package com.aiwa.store

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import com.aiwa.bridge.AgentConnector
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The connectors the super agent may use: mail, agenda, SMS, call. Each one is OFF until the person turns it on in the settings. Nothing is done behind her back: the phone reads the
 * request back, she confirms, and the app concerned opens with everything filled in; sending, calling or saving is her own tap there. No account or password is ever asked.
 */
object ConnectorSettings {
    private const val PREFS = "aiwa_connectors"
    val KINDS = listOf("mail" to "Mail", "agenda" to "Agenda", "sms" to "SMS", "appel" to "Appel")
    fun enabled(context: Context, kind: String): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(kind, false)
    fun set(context: Context, kind: String, on: Boolean) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(kind, on).apply() }
    fun label(kind: String): String = KINDS.firstOrNull { it.first == kind }?.second ?: kind
}

/** True when the app was opened; false when the connector is off or no app can take it. */
fun runConnector(context: Context, c: AgentConnector): Boolean {
    if (!ConnectorSettings.enabled(context, c.kind)) return false
    val intent = when (c.kind) {
        "mail" -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(c.to))).putExtra(Intent.EXTRA_SUBJECT, c.subject).putExtra(Intent.EXTRA_TEXT, c.body)
        "sms" -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(c.to))).putExtra("sms_body", c.body)
        "appel" -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(c.to)))
        "agenda" -> {
            val begin = try { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).parse(c.start)?.time } catch (err: Exception) { null } ?: return false
            Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).putExtra(CalendarContract.Events.TITLE, c.subject)
                .putExtra(CalendarContract.Events.DESCRIPTION, c.body).putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 3_600_000L)
        }
        else -> return false
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try { context.startActivity(intent); true } catch (err: Exception) { false }
}
