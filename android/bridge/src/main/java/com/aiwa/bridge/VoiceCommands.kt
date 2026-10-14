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
    data class Effort(val id: String?, val label: String) : VoiceCommand()
    data class VideoQuality(val id: String, val label: String) : VoiceCommand()
    /** « envoie la vidéo sur la TV du salon »: `tv` is the name as said (« salon », or empty). With a message it is done when the video is ready, without one at once. */
    data class Cast(val tv: String) : VoiceCommand()
    /** « arrête la TV ». */
    object CastStop : VoiceCommand()
    data class PushMain(val direct: Boolean) : VoiceCommand()
    data class Deploy(val mode: String, val label: String) : VoiceCommand()
    object NewSession : VoiceCommand()
    data class SelectSession(val id: String, val title: String) : VoiceCommand()
    data class Repo(val name: String) : VoiceCommand()
    /** "t'es sur quoi ?": the pills read aloud (statusReport). */
    object Report : VoiceCommand()
    /** "stop", "arrête Claude": a message that asks the session to stop (it reads it at its next step: not a hard interruption). */
    object StopClaude : VoiceCommand()
}

data class VoiceModel(val id: String?, val label: String)
data class VoiceSession(val id: String, val title: String)

/** message: what goes to Claude; commands: what the person asked of Aiwa, in the order said; problems: what was not understood. */
data class Dictation(val message: String, val commands: List<VoiceCommand>, val problems: List<String>, val instructions: Boolean)

/** The command in a few words, for the line that says what was understood. */
fun describeCommand(command: VoiceCommand): String = when (command) {
    is VoiceCommand.Model -> "modèle " + command.label.substringBefore(" (")
    is VoiceCommand.Effort -> "effort " + command.label
    is VoiceCommand.VideoQuality -> "qualité vidéo " + command.label
    is VoiceCommand.Cast -> "vidéo sur la TV" + (if (command.tv.isBlank()) "" else " « ${command.tv} »")
    is VoiceCommand.CastStop -> "arrêt de la TV"
    is VoiceCommand.PushMain -> if (command.direct) "push main" else "push branche"
    is VoiceCommand.Deploy -> "déploiement " + command.label
    is VoiceCommand.NewSession -> "nouvelle session"
    is VoiceCommand.SelectSession -> "session « " + command.title + " »"
    is VoiceCommand.Repo -> "dépôt " + command.name.substringAfter('/')
    is VoiceCommand.Report -> "rapport des réglages"
    is VoiceCommand.StopClaude -> "stop Claude"
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
private val STOP_WORDS = setOf("stop", "stoppe", "arrete", "arrete-toi", "stoppe-toi")
private val STOP_TARGETS = setOf("claude", "toi", "tout", "maintenant")
private val QUESTION_WORDS = setOf("tu", "es", "ou", "la", "est", "quel", "quels")
private val FILLERS = setOf("et", "puis", "ensuite", "alors", "aussi", "donc", "ok", "voila", "apres")
private val SKIPPABLE = setOf("de", "du", "le", "la", "l", "les", "en", "sur", "avec", "mode", "est", "sera", "pour", "passe", "mets", "met", "a", "au", "ma", "une", "sa", "ta", "un")
private val FAMILIES = setOf("opus", "sonnet", "haiku", "fable")
private val AUTO_WORDS = setOf("auto", "automatique", "defaut")
private val DIRECT_WORDS = PillReference.PUSH_DIRECT
private val BRANCH_WORDS = PillReference.PUSH_BRANCH
private val EFFORT_WORDS = setOf("effort")
private val QUALITY_WORDS = setOf("qualite")
private val VIDEO_WORDS = setOf("video", "videos")
private val CAST_VERBS = setOf("envoie", "envoi", "envoyer", "diffuse", "diffuser", "caste", "caster", "lance", "lancer", "joue", "jouer", "passe", "projette", "mets", "met")
private val CAST_STOP_VERBS = setOf("arrete", "stoppe", "stop", "coupe", "termine")
private val TV_WORDS = setOf("tv", "tele", "teles", "television", "televisions")
private val CAST_PREPOSITIONS = setOf("sur", "a", "au", "vers", "dans")
private val CAST_OBJECT = setOf("la", "le", "les", "l", "une", "un", "video", "videos", "film", "ca", "resultat", "derniere", "dernier", "premiere", "nouvelle")
private val CAST_ARTICLES = setOf("la", "le", "les", "l", "ma", "mon", "notre")
private val CAST_NAME_LEAD = setOf("du", "de", "d", "la", "le", "chez", "au")
private val CAST_NAME_STOP = setOf("et", "puis", "ensuite", "quand", "apres", "avec", "pour", "mais")
private val CONNECTORS = setOf("et", "puis", "ensuite", "aussi", "apres")

private val COMMAND_WORDS = setOf("commande")
private val COMMAND_DIRECT_WORDS = setOf("directe", "direct")

/**
 * The two spoken sections: « instruction … » changes the pills, « commande … » is what goes to Claude, then « c'est bon vas-y » (cut off before this runs).
 *   « instruction modèle Opus push main commande fais une page »   pills, then the message
 *   « commande directe fais une page »                             the message only (the pills as they are go along anyway)
 *   « instruction modèle Opus »                                    the pills only
 * « instruction » alone STARTS the pill mode (what it says that is not a command stays text for Claude when no « commande » follows). « commande » is a keyword only when it STARTS the sentence or follows an instruction section (« une page de commande » is text). In the wake-word mode, where
 * everything is instruction, it is one wherever it is said. Returns (the instruction words, the command words or null), or null when the sentence has none of it.
 */
private fun explicitSections(all: List<Word>, startInInstructions: Boolean): Pair<List<Word>, List<Word>?>? {
    fun afterCommand(from: Int): List<Word> = all.subList(from, all.size).let { rest -> if (rest.firstOrNull()?.plain in COMMAND_DIRECT_WORDS) rest.drop(1) else rest }
    if (startInInstructions) {
        val k = all.indexOfFirst { it.plain in COMMAND_WORDS }
        return if (k < 0) null else all.subList(0, k) to afterCommand(k + 1)
    }
    val first = all.firstOrNull() ?: return null
    if (first.plain in COMMAND_WORDS) return emptyList<Word>() to afterCommand(1)
    if (first.plain in TRIGGER) {
        val k = all.indexOfFirst { it.plain in COMMAND_WORDS }
        return if (k < 0) all.subList(1, all.size) to null else all.subList(1, k) to afterCommand(k + 1)
    }
    return null
}

// Verbs that start « mets-toi sur le dépôt X », and the little words between the verb and « dépôt ».
private val LEAD_VERBS = setOf("mets", "met", "mets-toi", "met-toi", "passe", "passe-toi", "va", "vas", "bascule", "change", "changer", "reviens", "retourne", "travaille", "bosse", "utilise", "prends")
private val LEAD_FILLERS = setOf("toi", "sur", "vers", "dans", "a", "au", "le", "la", "les", "l", "mon", "ton", "notre", "de", "du")

/** The index of « dépôt » when the sentence STARTS with an order that leads to it (« mets-toi sur le dépôt X »), else -1. */
private fun leadingRepoCommandAt(all: List<Word>): Int {
    if (all.isEmpty() || all[0].plain !in LEAD_VERBS) return -1
    var k = 1
    while (k < all.size && k <= 4 && all[k].plain in LEAD_FILLERS) k++
    return if (k < all.size && all[k].plain in REPO_WORDS) k else -1
}

private fun startsCommand(word: Word, next: Word?): Boolean =
    word.plain in MODEL_WORDS || word.plain in PUSH_WORDS || word.plain in EFFORT_WORDS || word.plain in QUALITY_WORDS || word.plain in DEPLOY_WORDS || word.plain in SESSION_WORDS ||
        word.plain in REPO_WORDS || (word.plain in NEW_WORDS && next?.plain in SESSION_WORDS) || (word.plain in TRIGGER)

/** « envoie la vidéo sur la TV du salon »: the name said after « TV » and the index after it, or null when these words are not that (« envoie le mail à Paul »). */
private fun castAhead(segment: List<Word>, at: Int): Pair<String, Int>? {
    var k = at + 1
    var steps = 0
    while (k < segment.size && segment[k].plain in CAST_OBJECT && steps < 5) { k++; steps++ }
    if (segment.getOrNull(k)?.plain !in CAST_PREPOSITIONS) return null
    k++
    while (segment.getOrNull(k)?.plain in CAST_ARTICLES) k++
    if (segment.getOrNull(k)?.plain !in TV_WORDS) return null
    k++
    val name = mutableListOf<String>()
    while (k < segment.size && name.size < 3) {
        val w = segment[k]
        if (w.plain in CAST_NAME_STOP || startsCommand(w, segment.getOrNull(k + 1))) break
        if (name.isEmpty() && w.plain in CAST_NAME_LEAD) { k++; continue }
        name.add(w.original)
        k++
    }
    return name.joinToString(" ") to k
}

/** « arrête la TV »: the index after « TV », or null. */
private fun castStopAhead(segment: List<Word>, at: Int): Int? {
    var k = at + 1
    while (segment.getOrNull(k)?.plain in CAST_ARTICLES) k++
    return if (segment.getOrNull(k)?.plain in TV_WORDS) k + 1 else null
}

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
    // « instruction … commande … »: the spoken sections (see explicitSections)
    explicitSections(all, startInInstructions)?.let { (instructionPart, commandPart) ->
        fun text(part: List<Word>) = part.joinToString(" ") { it.original }
        val instruction = if (instructionPart.isEmpty()) Dictation("", emptyList(), emptyList(), false) else parseDictation(text(instructionPart), models, sessions, repos, startInInstructions = true)
        val command = if (commandPart == null) null else parseDictation(text(commandPart), models, sessions, repos)
        val problems = instruction.problems + (command?.problems ?: emptyList()) +
            // What the instruction section said that is not a command is text for Claude when no « commande » follows; with one, it is reported (nothing is guessed)
            (if (command != null && instruction.message.isNotBlank()) listOf("Pas compris dans l'instruction : « ${instruction.message} »") else emptyList())
        val message = if (command != null) command.message else instruction.message
        return Dictation(message, instruction.commands + (command?.commands ?: emptyList()), problems, instructionPart.isNotEmpty() || command?.instructions == true)
    }
    var trigger = -1
    var triggerLength = 0
    if (!startInInstructions) {
        for (i in all.indices) {
            if (all[i].plain in TRIGGER) {
                val n = isAiwa(all, i + 1)
                if (n > 0) { trigger = i; triggerLength = 1 + n; break }
            }
        }
        if (trigger < 0) {
            // « mets-toi sur le dépôt Aiwa store »: an order that starts the sentence and names a repository needs no « instruction Aiwa » (it is read back and confirmed like the others)
            val repoAt = leadingRepoCommandAt(all)
            if (repoAt >= 0) {
                val asOrder = parseDictation(all.subList(repoAt, all.size).joinToString(" ") { it.original }, models, sessions, repos, startInInstructions = true)
                // Only when the name is one of the repositories: « change le dépôt des tests » (a sentence for Claude) stays one
                if (asOrder.commands.any { it is VoiceCommand.Repo }) return asOrder
            }
            return Dictation(heard.trim(), emptyList(), emptyList(), false)
        }
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

            // « arrête la TV », « envoie la vidéo sur la TV du salon » (before « stop Claude »: they share the verb)
            w.plain in CAST_STOP_VERBS && castStopAhead(segment, i) != null -> {
                commands.add(VoiceCommand.CastStop)
                i = castStopAhead(segment, i)!!
                afterCommand = true
            }
            w.plain in CAST_VERBS && castAhead(segment, i) != null -> {
                val (name, next) = castAhead(segment, i)!!
                commands.add(VoiceCommand.Cast(name))
                i = next
                afterCommand = true
            }

            // "stop", "arrête Claude", "arrête-toi": not "t'arrête pas" (an apostrophe in the word), and "arrête" alone only when nothing else follows it
            w.plain in STOP_WORDS && !w.original.contains('\'') && !w.original.contains('’') &&
                (w.plain.startsWith("stop") || w.plain.endsWith("-toi") || next == null || next.plain in STOP_TARGETS || startsCommand(next, wordAt(i + 2))) -> {
                commands.add(VoiceCommand.StopClaude)
                i++
                while (wordAt(i)?.plain in STOP_TARGETS) i++
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

            // « effort élevé », « effort très élevé »: said without a value after it, the word is only text for Claude
            w.plain in EFFORT_WORDS -> {
                val k = skipSkippable(i + 1)
                val found = PillReference.effortAt(listOfNotNull(wordAt(k)?.plain, wordAt(k + 1)?.plain))
                if (found == null) { leftover.add(w.original); afterCommand = false; i++ }
                else { commands.add(VoiceCommand.Effort(found.first.id, found.first.label)); i = k + found.second; afterCommand = true }
            }

            // « qualité vidéo extra », « vidéo simple »: only when a quality follows; « fais une vidéo » is text for Claude
            w.plain in QUALITY_WORDS || w.plain in VIDEO_WORDS -> {
                var k = skipSkippable(i + 1)
                if (w.plain in QUALITY_WORDS && wordAt(k)?.plain in VIDEO_WORDS) k = skipSkippable(k + 1)
                val found = wordAt(k)?.plain?.let { PillReference.videoQualityOf(it) }
                if (found == null) { leftover.add(w.original); afterCommand = false; i++ }
                else { commands.add(VoiceCommand.VideoQuality(found.id ?: "moyen", found.label)); i = k + 1; afterCommand = true }
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
                    p in AIWA || isAiwa(segment, k) > 0 -> "aiwa" to "Aiwa"
                    else -> PillReference.deployOf(p)?.let { (it.id ?: "none") to it.label }
                }
                if (mode != null) {
                    commands.add(VoiceCommand.Deploy(mode.first, mode.second))
                    k += if (p in AIWA || isAiwa(segment, k) == 0) 1 else isAiwa(segment, k)
                } else {
                    problems.add("Déploiement « ${wordAt(k)?.original ?: "?"} » inconnu : " + PillReference.DEPLOY.filter { it.listed }.joinToString(", ") { it.label })
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
                if (picked == null) {
                    // Heard badly (« ai oie store » for aiwa_store): the closest name, if it is clearly the closest. The repository is read back and confirmed anyway.
                    var bestName: String? = null
                    var bestScore = 0.0
                    var bestLen = 0
                    for (len in minOf(5, segment.size - start) downTo 1) {
                        val spoken = compact(segment.subList(start, start + len).joinToString("") { it.plain })
                        if (spoken.length < 4) continue
                        val scored = keyed.map { it.first to similarity(spoken, it.second) }.sortedByDescending { it.second }
                        val top = scored.firstOrNull() ?: continue
                        val gap = top.second - (scored.getOrNull(1)?.second ?: 0.0)
                        if (top.second >= 0.75 && gap >= 0.1 && top.second > bestScore) { bestName = top.first; bestScore = top.second; bestLen = len }
                    }
                    if (bestName != null) { picked = bestName; used = bestLen }
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
    // « fais un site et une vidéo et envoie la vidéo sur la TV »: the « et » that led to the cast is not part of the message
    if (commands.any { it is VoiceCommand.Cast }) {
        while (leftover.isNotEmpty() && plainWord(leftover.last()) in CONNECTORS) leftover.removeAt(leftover.size - 1)
    }
    val message = (before + leftover).joinToString(" ").trim()
    return Dictation(message, commands, problems, true)
}

/** 1.0 for the same text, towards 0 as the edits needed to turn one into the other grow (Levenshtein over the longer length). */
fun similarityOf(a: String, b: String): Double = similarity(a, b)

internal fun similarity(a: String, b: String): Double {
    if (a == b) return 1.0
    if (a.isEmpty() || b.isEmpty()) return 0.0
    var previous = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val current = IntArray(b.length + 1)
        current[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
        }
        previous = current
    }
    return 1.0 - previous[b.length].toDouble() / maxOf(a.length, b.length)
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
fun statusReport(repo: String?, modelLabel: String, pushMain: Boolean, deploy: String, session: String?, backendUp: Boolean, effort: String? = null, videoQuality: String? = null): String {
    val parts = mutableListOf<String>()
    parts.add(if (repo == null) "Aucun dépôt choisi" else "Dépôt " + repo.substringAfter('/').replace('_', ' ').replace('-', ' '))
    parts.add("Modèle $modelLabel")
    if (effort != null) parts.add("Effort $effort")
    parts.add(if (pushMain) "Push direct sur main" else "Push sur une branche")
    parts.add(
        when (deploy) {
            "pages" -> "Déploiement Pages"
            "android" -> "Déploiement Android"
            "store" -> "Déploiement Store"
            "aiwa" -> "Déploiement Aiwa"
            "campaign" -> "Mode campagne"
            else -> "Aucun déploiement"
        },
    )
    if (videoQuality != null) parts.add("Qualité vidéo $videoQuality")
    parts.add(if (session.isNullOrBlank()) "Pas de session en cours" else "Session $session")
    if (!backendUp) parts.add("Attention : le serveur Termux ne répond pas, ces réglages peuvent ne pas être à jour")
    return parts.joinToString(". ") + "."
}

/** What woke the listening: "mon agent …" (the phone then reads where things stand) or "instruction …" (it goes straight to listening). */
enum class WakeKind { AGENT, INSTRUCTION, STOP, LIAISON }

/**
 * The wake word, as the detector hears it. "Aiwa" is not in the small French model's vocabulary, so it cannot be written in the grammar: the words of origin are French ones
 * ("mon agent", "instruction"), and a word of the person's own is SAID to the phone, which keeps how the engine hears it (WakeWords): `custom` holds those spellings.
 */
fun wakeKind(heard: String, custom: List<String> = emptyList()): WakeKind? {
    val list = words(heard)
    // "stop Claude" / "arrête Claude": said at any moment, it is not a dictation: it stops Claude (see StopClaude)
    for (i in 1 until list.size) if (list[i].plain == "claude" && list[i - 1].plain in STOP_WORDS) return WakeKind.STOP
    // "mon super agent": the call of the liaison agent (the second listening, see WakeWordService); checked before "mon agent"
    for (i in 1 until list.size) if (list[i].plain == "agent" && list[i - 1].plain == "super") return WakeKind.LIAISON
    for (i in 1 until list.size) if (list[i].plain == "agent" && list[i - 1].plain == "mon") return WakeKind.AGENT
    // the word the person chose (WakeWords): it wakes like "mon agent"
    if (custom.isNotEmpty() && WakeWords.heardCustom(heard, custom)) return WakeKind.AGENT
    return if (list.any { it.plain in TRIGGER }) WakeKind.INSTRUCTION else null
}

fun isWakeWord(heard: String): Boolean = wakeKind(heard) != null

/** What the phone says back before a hands-free dictation is applied: everything it is about to do, and the question. Null when there is nothing to do. */
fun recapSentence(dictation: Dictation, pills: Pills? = null): String? {
    // The report and the stop are not "applied": the first only reads, the second is done at once, never read back and waited on
    val commands = dictation.commands.filter { it !is VoiceCommand.Report && it !is VoiceCommand.StopClaude }
    if (commands.isEmpty() && dictation.message.isBlank()) return null
    val parts = mutableListOf<String>()
    if (commands.isNotEmpty()) parts.add("J'applique : " + commands.joinToString(", ") { describeCommand(it) })
    // With the pills as they are: everything the request will go with, changed or not, read before it leaves
    if (pills != null) {
        val after = applyCommands(pills, commands)
        parts.add("Réglages : " + statusReport(after.repo, after.model, after.pushMain, after.deploy, after.session, true, after.effort, after.videoQuality).removeSuffix("."))
    }
    if (dictation.message.isNotBlank()) parts.add("J'envoie à Claude : " + dictation.message.take(160) + if (dictation.message.length > 160) "…" else "")
    if (dictation.problems.isNotEmpty()) parts.add("Je n'ai pas compris : " + dictation.problems.joinToString(", "))
    parts.add("Tu confirmes ?")
    return parts.joinToString(". ")
}

/**
 * "Non, c'est pas bon, arrête" (or "annule") said at the END of what was heard: the dictation is dropped, nothing is applied and nothing is sent.
 * It is looked for at the end only: "annule la commande" in the middle of a message is a message.
 */
fun endsWithCancelPhrase(text: String): Boolean {
    val tail = words(text).map { it.plain }.filter { it.isNotEmpty() }.takeLast(6)
    if (tail.isEmpty()) return false
    if (tail.last() == "annule" || tail.last() == "annuler" || (tail.size >= 2 && tail[tail.size - 2] == "annule" && tail.last() == "tout")) return true
    val non = tail.indexOfLast { it == "non" }
    if (non < 0) return false
    val after = tail.subList(non + 1, tail.size)
    val pas = after.indexOf("pas")
    return pas >= 0 && after.subList(pas + 1, after.size).contains("bon")
}
