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
