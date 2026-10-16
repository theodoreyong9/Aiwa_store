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
    /** The short version for WhatsApp, SMS and Telegram, the one for a post on X, and the style of this creation (written by the session, read for the next one). */
    val chatBody: String = "",
    val xBody: String = "",
    val style: String = "",
    /** The target's present site, the measured findings that make it dated, and its public points of contact, as the session found them. */
    val currentUrl: String = "",
    val reasons: String = "",
    val contacts: String = "",
    /** The channel of this target (whatsapp, telegram, mail, linkedin, or empty), the link of its profile or page, and what else was produced for it (documents, an app, an Aiwa app). */
    val type: String = "",
    val profileUrl: String = "",
    val docsUrl: String = "",
    val appUrl: String = "",
    val aiwaUrl: String = "",
)

/** One campaign of the repository: its folder under docs/campagnes/ and what its campaign.json says (name, test, what was ticked in the form). */
data class Campaign(
    val slug: String,
    val name: String,
    val test: Boolean = false,
    val createdAt: Long = 0,
    val documents: List<String> = emptyList(),
    val repos: List<String> = emptyList(),
    val makes: List<String> = emptyList(),
    val uses: List<String> = emptyList(),
    val prompt: String = "",
)

data class CampaignList(val repo: String?, val campaigns: List<Campaign>)

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
        if (p.docsUrl.isNotBlank()) lines.add("Documents : ${p.docsUrl}")
        if (p.appUrl.isNotBlank()) lines.add("Application : ${p.appUrl}")
        if (p.aiwaUrl.isNotBlank()) lines.add("App Aiwa : ${p.aiwaUrl}")
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
        // The chat version when the session wrote one (a message app is not a mail), the mail's text otherwise
        val d = ProposalMail.draft(if (p.chatBody.isNotBlank()) p.copy(body = p.chatBody) else p, price, test)
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
        val head = (if (test) "[TEST] " else "") + p.xBody.ifBlank { p.subject.ifBlank { "Proposition pour ${p.name}" } }
        val room = 280 - links.size * 24 - tail.length - 1
        val cut = if (head.length <= room) head else head.take(maxOf(0, room - 1)).trimEnd() + "…"
        val text = (cut + tail + (if (links.isEmpty()) "" else " " + links.joinToString(" "))).trim()
        return "https://x.com/intent/post?text=" + encode(text)
    }

    /** The title and description that go with the video shared to YouTube or another application (the person pastes them there). */
    fun videoDescription(p: Proposal, price: String): String = message(p, price, false).take(4800)
}

/**
 * What the campaign has already sent, channel by channel, and the style asked for: the sentence that goes with a new batch so that the session works from the
 * LAST creation of each channel (« classique »: the same style; « innovante »: something else). `last` maps a channel to the proposal last sent on it.
 */
object CampaignChannels {
    val NAMES = linkedMapOf("mail" to "mail", "chat" to "WhatsApp/SMS/Telegram", "x" to "X")

    fun phrase(innovative: Boolean, last: Map<String, Proposal>): String {
        val style = if (innovative) "INNOVANTE : pour chaque canal, cherche autre chose que son dernier envoi." else "CLASSIQUE : pour chaque canal, reste dans le style de son dernier envoi."
        val lines = NAMES.entries.map { (key, label) ->
            val p = last[key]
            if (p == null) "$label : rien d'envoyé encore, pars du brief."
            else "$label : dernier envoi « ${p.name.ifBlank { p.id }} »" + (if (p.style.isNotBlank()) " (style : ${p.style})" else " (style non noté : relis ses fichiers dans le dossier de la campagne, `docs/campagnes/…/${p.id}/`)") + "."
        }
        return "Création $style Canal par canal : " + lines.joinToString(" ")
    }
}

/** The campaign form: a name becomes the folder of the campaign, and what was ticked becomes the request sent to the session. Pure, so that it is tested. */
object CampaignRequest {
    /** « Sites dépassés — Lausanne » → « sites-depasses-lausanne »: lowercase, no accent, dashes; at most 50 characters; never empty. */
    fun slug(name: String): String {
        val plain = java.text.Normalizer.normalize(name.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        val dashed = plain.replace(Regex("[^a-z0-9]+"), "-").trim('-').take(50).trim('-')
        return dashed.ifBlank { "campagne" }
    }

    fun message(name: String, slug: String, test: Boolean, documents: List<String>, repos: List<String>, makes: List<String>, uses: List<String>, prompt: String): String {
        val parts = ArrayList<String>()
        parts.add("Nouvelle campagne « ${name.trim()} » (dossier docs/campagnes/$slug/).")
        if (test) parts.add("TEST : ne cherche aucune vraie cible, prépare une seule proposition pour moi-même, avec les productions demandées.")
        parts.add(if (documents.isEmpty()) "Aucun document du dossier docs/ à utiliser." else "Documents du dossier docs/ à utiliser (lis-les en entier) : " + documents.joinToString(", ") { "docs/$it" } + ".")
        if (repos.isNotEmpty()) parts.add("Mes réalisations GitHub à utiliser (dépôts publics) : " + repos.joinToString(", ") + ".")
        parts.add(if (makes.isEmpty()) "Rien de personnalisé à produire pour chaque cible." else "Pour chaque cible, produis en personnalisé : " + makes.joinToString(", ") { when (it) { "video" -> "une vidéo"; "page" -> "une page (dans l'arborescence du site de la campagne, avec son lien personnalisé)"; else -> "des documents" } } + ".")
        if (uses.isNotEmpty()) parts.add("Utilise ce que le dépôt produit déjà (ce n'est pas personnalisé) : " + uses.joinToString(", ") + " (mets leurs liens dans les propositions).")
        if (prompt.isNotBlank()) parts.add("Ma demande : ${prompt.trim()}")
        parts.add("Écris d'abord docs/campagnes/$slug/campaign.json avec ces choix. Ne me pose aucune question et n'attends aucune validation : travaille cible par cible, pousse chaque cible avec sa proposition, et continue jusqu'à mon message « [Aiwa — stop campagne] ».")
        return parts.joinToString("\n")
    }
}
