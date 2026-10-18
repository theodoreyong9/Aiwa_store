package com.aiwa.bridge

/**
 * The wake word the person chose, as the engine HEARS it. The detector's grammar can only hold words that the small French model knows ("Aiwa" is not one of
 * them), so a chosen word is not written but SAID: the person says it a few times, the model writes what it hears with its nearest French words ("ail oua",
 * "ai oua"…), and those spellings, the variants, are what the detector listens for. It works for any word, because it keeps how the engine hears it, not how it is spelt.
 *
 * Nothing here touches the phone: the variants are checked (check), put in the grammar (grammar) and recognised (heardCustom).
 */
object WakeWords {
    /** What the detector always listens for. */
    val DEFAULT_PHRASES = listOf("heille wa", "haye wa", "mon agent", "mon super agent", "instruction", "stop claude", "arrête claude", "stop la commande", "éteins", "éteins toi", "renvoie les instructions", "t'es où là", "tu es où là")
    const val MIN_LETTERS = 5
    const val MAX_VARIANTS = 6

    // Words so common that, heard alone, they would wake the phone all day long.
    private val COMMON = setOf(
        "oui", "non", "ok", "ca", "le", "la", "les", "un", "une", "des", "et", "ou", "je", "tu", "il", "elle", "on", "nous", "vous", "ils", "elles", "de", "du", "en", "ce", "se", "me", "te", "ne", "pas",
        "que", "qui", "est", "bon", "salut", "allo", "ail", "eau", "aie", "voila", "alors", "donc", "merci", "bonjour", "allez", "tiens", "hein", "ben", "bah", "euh", "mais", "comme", "avec", "pour",
        "dans", "sur", "par", "plus", "tout", "tous", "cette", "cela", "ici", "la-bas", "bien", "tres", "fait", "faire", "dit", "dire", "va", "vas", "vais", "suis", "es", "sont", "ete", "etre", "avoir",
    )

    /** A transcription as a plain phrase: lowercase, no accent, no punctuation, one space between words. "[unk]" and an empty text give "". */
    fun plain(text: String): String = text.trim().split(Regex("\\s+")).map { plainWord(it) }.filter { it.isNotEmpty() && it != "unk" }.joinToString(" ")

    sealed class Check {
        data class Ok(val variants: List<String>) : Check()
        data class Rejected(val reason: String) : Check()
    }

    /** What was heard when the person said their word, one text per try: the variants to keep, or why this word cannot be listened for. */
    fun check(heard: List<String>): Check {
        val phrases = heard.map { plain(it) }.filter { it.isNotEmpty() }
        if (phrases.size < 2) return Check.Rejected("Je n'ai rien compris de ton mot (${phrases.size} essai sur ${heard.size}). Redis-le distinctement, au moins deux fois, dans le calme.")
        val variants = phrases.distinct().take(MAX_VARIANTS)
        val defaults = DEFAULT_PHRASES.map { plain(it) }
        for (v in variants) {
            val letters = v.count { it in 'a'..'z' || it in '0'..'9' }
            val parts = v.split(' ')
            when {
                v in defaults -> return Check.Rejected("« $v » est déjà un mot d'éveil d'origine : choisis-en un autre.")
                letters < MIN_LETTERS -> return Check.Rejected("J'ai entendu « $v » : c'est trop court (au moins $MIN_LETTERS lettres), il se déclencherait sans arrêt. Choisis un mot plus long ou deux mots.")
                parts.all { it in COMMON } -> return Check.Rejected("J'ai entendu « $v » : ce sont des mots trop courants, ils se déclencheraient dans toutes les conversations. Choisis un mot plus rare.")
            }
        }
        return Check.Ok(variants)
    }

    /** The grammar of the detector (the JSON list of the Vosk Recognizer): the phrases of origin, the variants of the chosen word, and "[unk]" for everything else. */
    fun grammar(variants: List<String>): String =
        (DEFAULT_PHRASES + variants.map { plain(it) }.filter { it.isNotEmpty() } + "[unk]").distinct().joinToString(prefix = "[", postfix="]") { "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" }

    /** Is the chosen word in what was heard? (a variant, as a whole word or whole words: "ail oua" in "ail oua t'en es où"). */
    fun heardCustom(heard: String, variants: List<String>): Boolean {
        val h = plain(heard)
        if (h.isEmpty()) return false
        return variants.any { v -> v.isNotEmpty() && (h == v || " $h ".contains(" $v ")) }
    }
}
