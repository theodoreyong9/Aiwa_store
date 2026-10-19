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
    val KINDS = listOf("mail" to "Mail", "agenda" to "Agenda", "sms" to "SMS", "appel" to "Appel", "whatsapp" to "WhatsApp", "telegram" to "Telegram", "twitter" to "X (Twitter)", "instagram" to "Instagram", "linkedin" to "LinkedIn", "tiktok" to "TikTok")
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
        "whatsapp" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + c.to.trimStart('+') + "?text=" + Uri.encode(c.body)))
        // Telegram has no link that fills a message for a given person: its share sheet opens, the person picks (or the chat of the name given is opened by t.me)
        "telegram" -> if (c.to.isNotBlank()) Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/" + c.to)).also { copyText(context, c.body) }
                      else Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/share/url?url=%20&text=" + Uri.encode(c.body)))
        // No app lets another one fill a private message: the profile opens and the text is on the clipboard, to paste. LinkedIn without a profile opens its post box with the text.
        "instagram" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/" + c.to)).also { copyText(context, c.body) }
        "tiktok" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tiktok.com/@" + c.to)).also { copyText(context, c.body) }
        "linkedin" -> if (c.to.isNotBlank()) Intent(Intent.ACTION_VIEW, Uri.parse(if (c.to.startsWith("http")) c.to else "https://www.linkedin.com/in/" + c.to)).also { copyText(context, c.body) }
                      else Intent(Intent.ACTION_VIEW, Uri.parse("https://www.linkedin.com/feed/?shareActive=true&text=" + Uri.encode(c.body)))
        "twitter" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://twitter.com/intent/tweet?text=" + Uri.encode(c.body)))
        "agenda" -> {
            val begin = try { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).parse(c.start)?.time } catch (err: Exception) { null } ?: return false
            Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).putExtra(CalendarContract.Events.TITLE, c.subject)
                .putExtra(CalendarContract.Events.DESCRIPTION, c.body).putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 3_600_000L)
        }
        else -> return false
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try { context.startActivity(intent); true } catch (err: Exception) { false }
}

/** Telegram opens the chat of a name without a text: the text is put on the clipboard, to paste. */
private fun copyText(context: Context, text: String) {
    try { (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Aiwa", text)) } catch (err: Exception) { }
}
