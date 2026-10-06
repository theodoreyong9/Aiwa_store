package com.aiwa.bridge

import java.text.Normalizer
import java.util.Locale

/**
 * What the person says to Aiwa itself rather than to Claude, in the middle of a dictation.
 *
 * "fais-moi une page d'accueil instruction Aiwa modèle Opus push main déploiement Pages dépôt Aiwa store" is a message for Claude
 * (everything before "instruction Aiwa") and settings for the widget (everything after it, as long as it is a keyword followed by
 * what it takes). Words after "instruction Aiwa" that are not part of a command stay text for Claude. The end of the dictation
 * ("c'est bon vas-y") is cut off before this runs.
 *
 * The keywords are the widget's own pills: the session, the model, the push mode, the deployment, the repository. Nothing is guessed:
 * a name that matches nothing, or several things, is reported as a problem and changes nothing.
 */
sealed class VoiceCommand {
    data class Model(val id: String?, val label: String) : VoiceCommand()
    data class PushMain(val direct: Boolean) : VoiceCommand()
    data class Deploy(val mode: String, val label: String) : VoiceCommand()
    object NewSession : VoiceCommand()
    data class SelectSession(val id: String, val title: String) : VoiceCommand()
    data class Repo(val name: String) : VoiceCommand()
    /** "t'es sur quoi ?": the pills read aloud (statusReport). */
    object Report : VoiceCommand()
}

data class VoiceModel(val id: String?, val label: String)
data class VoiceSession(val id: String, val title: String)

/** message: what goes to Claude; commands: what the person asked of Aiwa, in the order said; problems: what was not understood. */
data class Dictation(val message: String, val commands: List<VoiceCommand>, val problems: List<String>, val instructions: Boolean)

/** The command in a few words, for the line that says what was understood. */
fun describeCommand(command: VoiceCommand): String = when (command) {
    is VoiceCommand.Model -> "modèle " + command.label.substringBefore(" (")
    is VoiceCommand.PushMain -> if (command.direct) "push main" else "push branche"
    is VoiceCommand.Deploy -> "déploiement " + command.label
    is VoiceCommand.NewSession -> "nouvelle session"
    is VoiceCommand.SelectSession -> "session « " + command.title + " »"
    is VoiceCommand.Repo -> "dépôt " + command.name.substringAfter('/')
    is VoiceCommand.Report -> "rapport des réglages"
}

private class Word(val original: String, val plain: String)

/** A word without accents, capitals or punctuation at its ends; "l'instruction" is "instruction". */
internal fun plainWord(word: String): String {
    val last = word.substringAfterLast('\'').substringAfterLast('’')
    val decomposed = Normalizer.normalize(last.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    return decomposed.replace(Regex("\\p{M}+"), "").replace(Regex("^[^a-z0-9]+|[^a-z0-9]+$"), "")
}

private fun words(text: String): List<Word> = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { Word(it, plainWord(it)) }

private val TRIGGER = setOf("instruction", "instructions")
// How a recognizer may write "Aiwa" said aloud: one word, or two (ai wa).
private val AIWA = setOf("aiwa", "aiva", "aywa", "ayoua", "aiwha", "eiwa", "aiwah", "aywa", "aiouwa")
private val MODEL_WORDS = setOf("modele", "modeles", "model")
private val PUSH_WORDS = setOf("push")
private val DEPLOY_WORDS = setOf("deploiement", "deploiements", "deploy")
private val SESSION_WORDS = setOf("session", "sessions")
private val REPO_WORDS = setOf("depot", "depots", "repo", "repos", "repository")
private val NEW_WORDS = setOf("nouvelle", "nouveau")
private val REPORT_WORDS = setOf("rapport", "statut", "bilan", "etat")
private val QUESTION_WORDS = setOf("tu", "es", "ou", "la", "est", "quel", "quels")
private val FILLERS = setOf("et", "puis", "ensuite", "alors", "aussi", "donc", "ok", "voila", "apres")
private val SKIPPABLE = setOf("de", "du", "le", "la", "l", "les", "en", "sur", "avec", "mode", "est", "sera", "pour", "passe", "mets", "met", "a", "au", "ma", "une", "sa", "ta", "un")
private val FAMILIES = setOf("opus", "sonnet", "haiku", "fable")
private val AUTO_WORDS = setOf("auto", "automatique", "defaut")
private val DIRECT_WORDS = setOf("main", "direct", "directement", "principal", "principale", "master")
private val BRANCH_WORDS = setOf("branche", "branches")
private val NONE_WORDS = setOf("aucun", "aucune", "rien", "desactive", "desactiver", "non", "zero", "stop", "arret")
private val PAGES_WORDS = setOf("pages", "page", "site", "web")
private val ANDROID_WORDS = setOf("android", "apk")

private fun startsCommand(word: Word, next: Word?): Boolean =
    word.plain in MODEL_WORDS || word.plain in PUSH_WORDS || word.plain in DEPLOY_WORDS || word.plain in SESSION_WORDS ||
        word.plain in REPO_WORDS || (word.plain in NEW_WORDS && next?.plain in SESSION_WORDS) || (word.plain in TRIGGER)

private fun isAiwa(list: List<Word>, at: Int): Int {
    // number of words that say "Aiwa" from `at` (0 = they do not)
    val first = list.getOrNull(at)?.plain ?: return 0
    if (first in AIWA) return 1
    val second = list.getOrNull(at + 1)?.plain ?: return 0
    return if ((first + second) in AIWA) 2 else 0
}

private fun isVersion(word: String) = Regex("^\\d+([.,]\\d+)?$").matches(word)

/**
 * The text with "instruction Aiwa" and what follows read as commands where they are one.
 * startInInstructions: the whole text is already what follows "instruction Aiwa" (it was the wake word, heard before the dictation began).
 */
fun parseDictation(heard: String, models: List<VoiceModel>, sessions: List<VoiceSession>, repos: List<String>, startInInstructions: Boolean = false): Dictation {
    val all = words(heard)
    var trigger = -1
    var triggerLength = 0
    if (!startInInstructions) {
        for (i in all.indices) {
            if (all[i].plain in TRIGGER) {
                val n = isAiwa(all, i + 1)
                if (n > 0) { trigger = i; triggerLength = 1 + n; break }
            }
        }
        if (trigger < 0) return Dictation(heard.trim(), emptyList(), emptyList(), false)
    }

    val before = if (startInInstructions) emptyList() else all.subList(0, trigger).map { it.original }
    val segment = if (startInInstructions) all else all.subList(trigger + triggerLength, all.size)
    val commands = mutableListOf<VoiceCommand>()
    val problems = mutableListOf<String>()
    val leftover = mutableListOf<String>()
    var afterCommand = true
    var i = 0

    fun wordAt(k: Int): Word? = segment.getOrNull(k)
    fun skipSkippable(from: Int): Int { var k = from; while (wordAt(k)?.plain in SKIPPABLE && k < segment.size) k++; return k }

    while (i < segment.size) {
        val w = segment[i]
        val next = wordAt(i + 1)
        when {
            w.plain in TRIGGER && isAiwa(segment, i + 1) > 0 -> { i += 1 + isAiwa(segment, i + 1) }

            // "Aiwa" said again right after the keyword or a command is not text for Claude
            afterCommand && isAiwa(segment, i) > 0 -> { i += isAiwa(segment, i) }

            // The wake phrase that opened this dictation, said again as the first words ("mon agent …"): not text for Claude either
            startInInstructions && afterCommand && w.plain == "mon" && next?.plain == "agent" -> { i += 2 }
            startInInstructions && afterCommand && w.plain == "agent" -> { i++ }

            // "t'en es où là ?" is "t'es sur quoi" said the other way round
            w.plain == "en" && next?.plain == "es" && wordAt(i + 2)?.plain == "ou" -> {
                commands.add(VoiceCommand.Report)
                i += 3
                while (leftover.isNotEmpty() && plainWord(leftover.last()) in QUESTION_WORDS) leftover.removeAt(leftover.size - 1)
                while (wordAt(i)?.plain in QUESTION_WORDS) i++
                afterCommand = true
            }

            w.plain in REPORT_WORDS || (w.plain == "sur" && next?.plain == "quoi") -> {
                commands.add(VoiceCommand.Report)
                i += if (w.plain == "sur") 2 else 1
                // "tu es sur quoi là ?": the words that asked it are not text for Claude either
                while (leftover.isNotEmpty() && plainWord(leftover.last()) in QUESTION_WORDS) leftover.removeAt(leftover.size - 1)
                while (wordAt(i)?.plain in QUESTION_WORDS) i++
                afterCommand = true
            }

            w.plain in MODEL_WORDS -> {
                var k = skipSkippable(i + 1)
                val start = k
                val taken = mutableListOf<String>()
                while (k < segment.size && taken.size < 4) {
                    val p = segment[k].plain
                    val n = wordAt(k + 1)?.plain
                    if (p in FAMILIES || p in AUTO_WORDS || isVersion(p) || p == "1m" || p == "million" || (p == "un" && n == "million") || (p == "par" && n == "defaut")) { taken.add(p); k++ } else break
                }
                val said = segment.subList(start, k).joinToString(" ") { it.original }
                val found = pickModel(taken, models)
                if (found != null) commands.add(VoiceCommand.Model(found.id, found.label))
                else problems.add("Modèle « ${said.ifBlank { "?" }} » inconnu")
                i = k
                afterCommand = true
            }

            w.plain in PUSH_WORDS -> {
                var k = skipSkippable(i + 1)
                val p = wordAt(k)?.plain
                if (p != null && p in DIRECT_WORDS) { commands.add(VoiceCommand.PushMain(true)); k++ }
                else if (p != null && p in BRANCH_WORDS) { commands.add(VoiceCommand.PushMain(false)); k++ }
                else problems.add("Push : dis « main » ou « branche »")
                i = k
                afterCommand = true
            }

            w.plain in DEPLOY_WORDS -> {
                var k = skipSkippable(i + 1)
                if (wordAt(k)?.plain == "github") k++
                val p = wordAt(k)?.plain
                val mode = when {
                    p == null -> null
                    p in PAGES_WORDS -> "pages" to "Pages"
                    p in ANDROID_WORDS -> "android" to "Android"
                    p == "store" -> "store" to "Store"
                    p in AIWA || isAiwa(segment, k) > 0 -> "aiwa" to "Aiwa"
                    p in NONE_WORDS -> "none" to "aucun"
                    else -> null
                }
                if (mode != null) {
                    commands.add(VoiceCommand.Deploy(mode.first, mode.second))
                    k += if (p in AIWA || isAiwa(segment, k) == 0) 1 else isAiwa(segment, k)
                } else {
                    problems.add("Déploiement « ${wordAt(k)?.original ?: "?"} » inconnu : Pages, Android, Store, Aiwa ou aucun")
                    if (k < segment.size) k++
                }
                i = k
                afterCommand = true
            }

            w.plain in NEW_WORDS && next?.plain in SESSION_WORDS -> { commands.add(VoiceCommand.NewSession); i += 2; afterCommand = true }

            w.plain in SESSION_WORDS -> {
                if (next?.plain in NEW_WORDS) { commands.add(VoiceCommand.NewSession); i += 2 }
                else {
                    val titles = sessions.map { it.id to plainText(it.title) }
                    val (picked, used, state) = pickByWords(segment, i + 1, 4) { spoken -> titles.filter { it.second.contains(spoken.joinToString(" ")) }.map { it.first } }
                    if (picked != null) { val s = sessions.first { it.id == picked }; commands.add(VoiceCommand.SelectSession(s.id, s.title)) }
                    else problems.add(if (state == "many") "Session : plusieurs correspondent, précise" else "Session « ${wordAt(i + 1)?.original ?: "?"} » introuvable")
                    i += 1 + used
                }
                afterCommand = true
            }

            w.plain in REPO_WORDS -> {
                val start = skipSkippable(i + 1)
                val keyed = repos.map { it to compact(it.substringAfter('/')) }
                var picked: String? = null
                var used = 0
                var state = "none"
                for (len in minOf(5, segment.size - start) downTo 1) {
                    val spoken = compact(segment.subList(start, start + len).joinToString("") { it.plain })
                    val exact = keyed.filter { it.second == spoken }
                    if (exact.size == 1) { picked = exact[0].first; used = len; break }
                    if (exact.size > 1) state = "many"
                    val partial = keyed.filter { spoken.length >= 3 && it.second.contains(spoken) }
                    if (partial.size == 1) { picked = partial[0].first; used = len; break }
                    if (partial.size > 1) state = "many"
                }
                if (picked != null) commands.add(VoiceCommand.Repo(picked))
                else {
                    val said = wordAt(start)?.original ?: "?"
                    problems.add(if (state == "many") "Dépôt « $said » : plusieurs correspondent, précise" else "Dépôt « $said » introuvable")
                    used = if (start < segment.size) 1 else 0
                }
                i = start + used
                afterCommand = true
            }

            w.plain in FILLERS && (afterCommand || (next != null && startsCommand(next, wordAt(i + 2)))) -> { i++ }

            else -> { leftover.add(w.original); afterCommand = false; i++ }
        }
    }
    val message = (before + leftover).joinToString(" ").trim()
    return Dictation(message, commands, problems, true)
}

private fun plainText(text: String): String = words(text).joinToString(" ") { it.plain }.trim()
private fun compact(text: String): String = plainText(text).replace(" ", "").replace(Regex("[^a-z0-9]"), "")

/** The longest run of words (at most `max`) that names exactly one thing: (what, how many words it took, "one" | "many" | "none"). */
private fun pickByWords(segment: List<Word>, from: Int, max: Int, matches: (List<String>) -> List<String>): Triple<String?, Int, String> {
    var state = "none"
    for (len in minOf(max, segment.size - from) downTo 1) {
        val spoken = segment.subList(from, from + len).map { it.plain }
        val found = matches(spoken)
        if (found.size == 1) return Triple(found[0], len, "one")
        if (found.size > 1) state = "many"
    }
    return Triple(null, if (from < segment.size) 1 else 0, state)
}

/** The model that a spoken name ("opus", "opus 5 5", "sonnet 5.5", "auto", "fable un million") designates: the first in the list when the version is not said. */
private fun pickModel(spoken: List<String>, models: List<VoiceModel>): VoiceModel? {
    if (spoken.isEmpty()) return null
    if (spoken.any { it in AUTO_WORDS }) return models.firstOrNull { it.id == null }
    val family = spoken.firstOrNull { it in FAMILIES } ?: return null
    val digits = spoken.filter { isVersion(it) && it != "1m" }.map { it.replace(',', '.') }
    // "5 5" said as two numbers is 5.5
    val version = when {
        digits.size >= 2 && digits.all { !it.contains('.') } -> digits.take(2).joinToString(".")
        digits.isNotEmpty() -> digits[0]
        else -> null
    }
    val million = spoken.any { it == "1m" || it == "million" }
    return models.firstOrNull { model ->
        val label = model.label.lowercase(Locale.ROOT)
        label.startsWith(family) &&
            (version == null || Regex("(^|\\s)" + Regex.escape(version) + "(\\s|$)").containsMatchIn(label.replace("·", " "))) &&
            (label.contains("1m") == million)
    }
}

/** What the pills say, as a sentence the phone can read aloud. */
fun statusReport(repo: String?, modelLabel: String, pushMain: Boolean, deploy: String, session: String?, backendUp: Boolean): String {
    val parts = mutableListOf<String>()
    parts.add(if (repo == null) "Aucun dépôt choisi" else "Dépôt " + repo.substringAfter('/').replace('_', ' ').replace('-', ' '))
    parts.add("Modèle $modelLabel")
    parts.add(if (pushMain) "Push direct sur main" else "Push sur une branche")
    parts.add(
        when (deploy) {
            "pages" -> "Déploiement Pages"
            "android" -> "Déploiement Android"
            "store" -> "Déploiement Store"
            "aiwa" -> "Déploiement Aiwa"
            else -> "Aucun déploiement"
        },
    )
    parts.add(if (session.isNullOrBlank()) "Pas de session en cours" else "Session $session")
    if (!backendUp) parts.add("Attention : le serveur Termux ne répond pas, ces réglages peuvent ne pas être à jour")
    return parts.joinToString(". ") + "."
}

/** What woke the listening: "mon agent …" (the phone then reads where things stand) or "instruction …" (it goes straight to listening). */
enum class WakeKind { AGENT, INSTRUCTION }

/**
 * The wake word, as the detector hears it. "Aiwa" cannot be listened for (it is not in the small French model's vocabulary), so the words
 * are French ones: "mon agent", and "instruction".
 */
fun wakeKind(heard: String): WakeKind? {
    val list = words(heard)
    for (i in 1 until list.size) if (list[i].plain == "agent" && list[i - 1].plain == "mon") return WakeKind.AGENT
    return if (list.any { it.plain in TRIGGER }) WakeKind.INSTRUCTION else null
}

fun isWakeWord(heard: String): Boolean = wakeKind(heard) != null

/** What the phone says back before a hands-free dictation is applied: everything it is about to do, and the question. Null when there is nothing to do. */
fun recapSentence(dictation: Dictation): String? {
    val commands = dictation.commands.filter { it !is VoiceCommand.Report }
    if (commands.isEmpty() && dictation.message.isBlank()) return null
    val parts = mutableListOf<String>()
    if (commands.isNotEmpty()) parts.add("J'applique : " + commands.joinToString(", ") { describeCommand(it) })
    if (dictation.message.isNotBlank()) parts.add("J'envoie à Claude : " + dictation.message.take(160) + if (dictation.message.length > 160) "…" else "")
    if (dictation.problems.isNotEmpty()) parts.add("Je n'ai pas compris : " + dictation.problems.joinToString(", "))
    parts.add("Tu confirmes ?")
    return parts.joinToString(". ")
}
