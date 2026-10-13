package com.aiwa.bridge

/**
 * The reference of the widget's pills: what each one can hold and the words that say it. ONE table, read by the voice grammar (VoiceCommands.kt),
 * by the help of the menu (helpSections) and by the sentence that reads the pills back; a pill or a value added here is understood, listed and read
 * everywhere. What cannot be known in advance (the repository, the session, the model list, which come from GitHub, Claude and the app) is not in
 * this table: it is matched at the end of the dictation against the lists the phone holds at that moment.
 */
data class PillValue(
    val id: String?,
    val label: String,
    /** Words as they are written after removing accents and capitals (plainWord). */
    val words: Set<String>,
    /** False for a value that can still be said but that the widget no longer offers. */
    val listed: Boolean = true,
)

object PillReference {
    /** The effort of Claude (`/effort`): null is automatic. */
    val EFFORT = listOf(
        PillValue(null, "automatique", setOf("auto", "automatique", "defaut")),
        PillValue("low", "faible", setOf("faible", "bas", "low")),
        PillValue("medium", "moyen", setOf("moyen", "moyenne", "normal", "medium")),
        PillValue("high", "élevé", setOf("eleve", "eleves", "haut", "high")),
        PillValue("xhigh", "très élevé", setOf("xhigh")),       // said « très élevé »: two words, see effortAt
        PillValue("max", "max", setOf("max", "maximum")),
    )

    /** The quality of a video: what it costs in time grows from the first to the last. */
    val VIDEO_QUALITY = listOf(
        PillValue("simple", "simple", setOf("simple", "rapide")),
        PillValue("moyen", "moyenne", setOf("moyen", "moyenne", "normale")),
        PillValue("extra", "extra", setOf("extra", "cinema", "cinematographique", "cine")),
    )

    /** What Claude is asked to produce (the project-type pill). Aiwa is said apart: its spelling varies with the recognizer (see VoiceCommands). */
    val DEPLOY = listOf(
        PillValue("pages", "Pages", setOf("pages", "page", "site", "web")),
        PillValue("android", "Android", setOf("android", "apk")),
        PillValue("store", "Store", setOf("store")),
        PillValue("aiwa", "Aiwa", emptySet()),
        PillValue("campaign", "Campagne", setOf("campagne", "campagnes")),
        PillValue("none", "aucun", setOf("aucun", "aucune", "rien", "desactive", "desactiver", "non", "zero", "stop", "arret"), listed = false),
    )

    val PUSH_DIRECT = setOf("main", "direct", "directement", "principal", "principale", "master")
    val PUSH_BRANCH = setOf("branche", "branches")

    /** The effort said from `spoken` (plain words): the value and the number of words it took, or null. « très élevé » takes two. */
    fun effortAt(spoken: List<String>): Pair<PillValue, Int>? {
        val first = spoken.firstOrNull() ?: return null
        val high = EFFORT.first { it.id == "high" }
        if (first == "tres" && spoken.getOrNull(1) in high.words) return EFFORT.first { it.id == "xhigh" } to 2
        val found = EFFORT.firstOrNull { first in it.words } ?: return null
        return found to 1
    }

    fun videoQualityOf(word: String): PillValue? = VIDEO_QUALITY.firstOrNull { word in it.words }

    fun deployOf(word: String): PillValue? = DEPLOY.firstOrNull { word in it.words }

    fun effortLabel(id: String?): String = EFFORT.firstOrNull { it.id == id }?.label ?: "automatique"
    fun videoQualityLabel(id: String?): String = VIDEO_QUALITY.firstOrNull { it.id == id }?.label ?: "moyenne"

    /**
     * What can be said, pill by pill, for the help of the menu. `models` are the model names the app offers. The first of each pair is the title
     * of the line, the second what it takes.
     */
    fun helpSections(models: List<String>): List<Pair<String, String>> = listOf(
        "modèle" to models.joinToString(" · ") + " (+ la version « 5.5 », « un million »)",
        "effort" to EFFORT.filter { it.listed }.joinToString(" · ") { it.label },
        "push" to "main · branche",
        "déploiement" to DEPLOY.filter { it.listed }.joinToString(" · ") { it.label },
        "qualité vidéo" to VIDEO_QUALITY.joinToString(" · ") { it.label },
        "nouvelle session · session" to "+ un bout du titre d'une session existante",
        "dépôt" to "+ son nom (comparé à tes dépôts à la volée, même mal entendu)",
        "TV" to "« envoie la vidéo sur la TV du salon » (tout de suite, ou quand Claude a fini s'il y a un message avant) · « arrête la TV »",
    )
}

/** What the pills hold: the values before a dictation, or after it once its commands are applied (applyCommands). */
data class Pills(
    val repo: String?,
    val model: String,
    val effort: String,
    val pushMain: Boolean,
    val deploy: String,
    val videoQuality: String,
    val session: String?,
)

/** The pills as they will be once `commands` are applied. A new session leaves no session name until it exists. */
fun applyCommands(pills: Pills, commands: List<VoiceCommand>): Pills = commands.fold(pills) { p, c ->
    when (c) {
        is VoiceCommand.Model -> p.copy(model = c.label.substringBefore(" ("))
        is VoiceCommand.Effort -> p.copy(effort = c.label)
        is VoiceCommand.PushMain -> p.copy(pushMain = c.direct)
        is VoiceCommand.Deploy -> p.copy(deploy = c.mode)
        is VoiceCommand.VideoQuality -> p.copy(videoQuality = c.label)
        is VoiceCommand.Repo -> p.copy(repo = c.name)
        is VoiceCommand.NewSession -> p.copy(session = null)
        is VoiceCommand.SelectSession -> p.copy(session = c.title)
        is VoiceCommand.Report, is VoiceCommand.StopClaude, is VoiceCommand.Cast, is VoiceCommand.CastStop -> p
    }
}
