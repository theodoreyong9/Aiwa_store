package com.aiwa.bridge

/**
 * The nine recognitions the person can make their own, for the small listening model, the agent (the dictation parser) and the super agent. Each has what the phone says by default and
 * two ways to be changed: SAID to the phone (the spellings the small model hears: for the wake-level words) or WRITTEN (a single word or phrase, for what the recogniser of the phone writes
 * down in a dictation or in the agent's conversation).
 */
object Recognitions {
    /** id: what the settings store it under; "agent" and "liaison" are the two first words that were ever customisable (their keys are kept). */
    class Slot(val id: String, val label: String, val defaults: List<String>, val help: String)

    val SLOTS = listOf(
        Slot("agent", "Réveil", listOf("mon agent"), "Réveille l'écoute : tu dictes ensuite."),
        Slot("liaison", "Super agent", listOf("mon super agent"), "Ouvre le super agent (la deuxième écoute, parlée)."),
        Slot("instruction", "Instruction", listOf("instruction"), "Ce qui suit règle les pastilles session et dépôt."),
        Slot("session", "Pastille session", listOf("session"), "Dans une instruction : « session … » choisit la session."),
        Slot("repo", "Pastille dépôt", listOf("dépôt", "repo"), "Dans une instruction : « dépôt … » choisit le dépôt."),
        Slot("validation", "Validation", listOf("c'est bon vas-y"), "Ce qu'on dit pour valider ce que le téléphone vient de relire."),
        Slot("stop", "Stop", listOf("stop claude", "arrête claude"), "Arrête la production en cours et fait oublier à la dernière session le dernier commentaire."),
        Slot("eteins", "Éteins", listOf("éteins"), "Éteint l'écoute permanente et le super agent."),
        Slot("resend", "Renvoi des instructions", listOf("renvoie les instructions"), "Renvoie à la session les instructions générales et celles des pastilles."),
    )

    fun slot(id: String): Slot? = SLOTS.firstOrNull { it.id == id }

    /** One word, plain, long enough not to be said by accident, not a very common word: what can be WRITTEN for a slot that the parser reads word by word. */
    fun checkWritten(slot: String, text: String): String? {
        val plain = WakeWords.plain(text)
        if (plain.isEmpty()) return "Écris un mot."
        val single = setOf("instruction", "session", "repo", "stop", "eteins")
        val words = plain.split(' ')
        if (slot in single && words.size != 1) return "Un seul mot pour cette reconnaissance."
        if (slot !in setOf("validation", "stop", "eteins", "resend") && words.size != 1) return "Un seul mot pour cette reconnaissance."
        if (plain.length < 3) return "« $plain » est trop court : au moins 3 lettres."
        if (words.all { it in COMMON_WORDS }) return "« $plain » est un mot trop courant : il se déclencherait dans toutes les phrases."
        return null
    }

    private val COMMON_WORDS = setOf("le", "la", "les", "un", "une", "des", "et", "ou", "oui", "non", "ok", "je", "tu", "il", "on", "de", "du", "en", "ce", "se", "pas", "que", "qui", "est", "bon", "va", "vas", "ca", "mais", "avec", "pour", "dans", "sur")
}

/** The spellings (as the small model hears them) of the words chosen for the wake-level recognitions; empty lists mean the words of origin only. */
data class WakeCustoms(
    val instruction: List<String> = emptyList(),
    val stop: List<String> = emptyList(),
    val off: List<String> = emptyList(),
    val resend: List<String> = emptyList(),
) {
    fun all(): List<String> = instruction + stop + off + resend
}

/**
 * The words the person WROTE for the recognitions that the parser and the agent read in text. Set by the app when the settings change (and at start); read by the parser. Plain words (no accent,
 * lowercase); a phrase is kept as one string.
 */
object VoiceVocabulary {
    @Volatile var written: Map<String, Set<String>> = emptyMap()

    fun extra(slot: String): Set<String> = written[slot] ?: emptySet()
    fun hasWord(slot: String, plainWord: String?): Boolean = plainWord != null && plainWord in extra(slot)

    /** Is one of the written phrases of the slot in the plain text, as whole words? */
    fun phraseHeard(slot: String, plainText: String): Boolean {
        val text = " " + plainText.trim() + " "
        return extra(slot).any { it.isNotBlank() && text.contains(" $it ") }
    }
}
