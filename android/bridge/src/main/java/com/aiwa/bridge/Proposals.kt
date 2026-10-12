package com.aiwa.bridge

/** A proposal a session prepared for one person or company (read from the repository: the session never sends anything). */
data class Proposal(
    val id: String,
    val name: String,
    val email: String,
    val subject: String,
    val body: String,
    val price: String,
    val videoUrl: String,
    val demoUrl: String,
    /** International form with the +, or empty (WhatsApp needs the country code). */
    val phone: String = "",
)

data class ProposalList(val repo: String?, val proposals: List<Proposal>)

/**
 * The mail that opens in the person's own mail application, pre-filled: the person reads it, changes it and presses send there. Nothing is sent from here,
 * and no account of the person is ever handed to Aiwa. Pure, so that it is tested.
 */
object ProposalMail {
    /** Always at the end: a way to say no, and the promise to stop. The person's name is in the body written by the session. */
    const val STOP_LINE = "Si ce message ne vous intéresse pas, répondez simplement « non merci » : je ne vous écrirai plus."

    class Draft(val to: String, val subject: String, val body: String)

    /** price: the price the person validated in the app (empty = none). test: the draft goes to nobody, so that the person tries it on themselves (they type their own address). */
    fun draft(p: Proposal, price: String, test: Boolean): Draft {
        val lines = ArrayList<String>()
        lines.add(p.body.trim())
        if (price.isNotBlank()) lines.add("Prix proposé : ${price.trim()}")
        if (p.demoUrl.isNotBlank()) lines.add("Maquette de démonstration (pas le site officiel) : ${p.demoUrl}")
        if (p.videoUrl.isNotBlank()) lines.add("Vidéo de présentation : ${p.videoUrl}")
        lines.add(STOP_LINE)
        val subject = (if (test) "[TEST] " else "") + p.subject.ifBlank { "Proposition pour ${p.name}" }
        return Draft(if (test) "" else p.email, subject, lines.filter { it.isNotBlank() }.joinToString("\n\n"))
    }
}

/**
 * The same proposal as a chat message, opened in the person's own application (WhatsApp, SMS, Telegram) through the address that application publishes
 * for it: no account, no key, nothing sent from here. The person reads the message and presses send there. Pure, so that it is tested.
 */
object ProposalShare {
    private val PHONE = Regex("^\\+[1-9][0-9]{7,14}$")

    /** The number's digits for wa.me, or null when it is not an international number. */
    fun digits(phone: String): String? {
        val clean = phone.replace(Regex("[\\s().-]"), "")
        return if (PHONE.matches(clean)) clean.substring(1) else null
    }

    private fun encode(text: String) = java.net.URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    /** The message: the mail's text, the subject first. */
    fun message(p: Proposal, price: String, test: Boolean): String {
        val d = ProposalMail.draft(p, price, test)
        return d.subject + "\n\n" + d.body
    }

    /** WhatsApp: the chat of the number (test: the person picks a chat, to try it on themselves). Null when there is no number to open and it is not a test. */
    fun whatsapp(p: Proposal, price: String, test: Boolean): String? {
        val text = encode(message(p, price, test))
        if (test) return "https://wa.me/?text=$text"
        val digits = digits(p.phone) ?: return null
        return "https://wa.me/$digits?text=$text"
    }

    /** SMS: the address to open (the message goes apart, as the SMS body). Null without a number outside a test. */
    fun smsAddress(p: Proposal, test: Boolean): String? = if (test) "smsto:" else digits(p.phone)?.let { "smsto:+$it" }

    /** Telegram: its share window, where the person picks the chat (it has no address by phone number). */
    fun telegram(p: Proposal, price: String, test: Boolean): String {
        val text = encode(message(p, price, test))
        val url = encode(p.demoUrl.ifBlank { "https://github.com" })
        return "https://t.me/share/url?url=$url&text=$text"
    }

    /**
     * X: the compose window of a POST on the person's own wall (x.com/intent/post), not a private message: the text is short (280 characters, a link counting for 23),
     * with the mock-up and the video as links. The person reads it and presses « Post » there. Nothing is sent from here.
     */
    fun x(p: Proposal, price: String, test: Boolean): String {
        val links = listOf(p.demoUrl, p.videoUrl).filter { it.isNotBlank() }
        val tail = (if (price.isNotBlank()) " Prix : ${price.trim()}." else "")
        val head = (if (test) "[TEST] " else "") + p.subject.ifBlank { "Proposition pour ${p.name}" }
        val room = 280 - links.size * 24 - tail.length - 1
        val cut = if (head.length <= room) head else head.take(maxOf(0, room - 1)).trimEnd() + "…"
        val text = (cut + tail + (if (links.isEmpty()) "" else " " + links.joinToString(" "))).trim()
        return "https://x.com/intent/post?text=" + encode(text)
    }

    /** The title and description that go with the video shared to YouTube or another application (the person pastes them there). */
    fun videoDescription(p: Proposal, price: String): String = message(p, price, false).take(4800)
}
