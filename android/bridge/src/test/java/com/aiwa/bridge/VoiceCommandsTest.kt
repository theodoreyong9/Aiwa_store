package com.aiwa.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandsTest {
    private val models = listOf(
        VoiceModel(null, "Auto (défaut du compte)"),
        VoiceModel("claude-fable-5-1", "Fable 5.1"),
        VoiceModel("claude-opus-5-5", "Opus 5.5"),
        VoiceModel("claude-opus-4-8", "Opus 4.8"),
        VoiceModel("claude-sonnet-5-5", "Sonnet 5.5"),
        VoiceModel("claude-sonnet-4-6", "Sonnet 4.6"),
        VoiceModel("claude-opus-4-8[1m]", "Opus 4.8 · 1M"),
    )
    private val sessions = listOf(VoiceSession("session_A", "Widget du matin"), VoiceSession("session_B", "Widget du soir"), VoiceSession("session_C", "Site vitrine"))
    private val repos = listOf("theodoreyong9/Aiwa_store", "theodoreyong9/AIWA_chain", "theodoreyong9/YourMinedApp", "theodoreyong9/Jobber")

    private fun parse(text: String) = parseDictation(text, models, sessions, repos)

    @Test fun aDictationWithoutTheKeywordsIsAllForClaude() {
        val d = parse("fais-moi une page d'accueil avec un modèle de formulaire et un push sur la branche")
        assertEquals("fais-moi une page d'accueil avec un modèle de formulaire et un push sur la branche", d.message)
        assertTrue(d.commands.isEmpty())
        assertFalse(d.instructions)
    }

    @Test fun whatIsBeforeTheKeywordIsTheMessageAndWhatFollowsAreTheCommandsInTheOrderSaid() {
        val d = parse("fais-moi un site instruction Aiwa modèle Opus push main déploiement Pages")
        assertEquals("fais-moi un site", d.message)
        assertEquals(listOf(VoiceCommand.Model("claude-opus-5-5", "Opus 5.5"), VoiceCommand.PushMain(true), VoiceCommand.Deploy("pages", "Pages")), d.commands)
        assertTrue(d.problems.isEmpty())
        assertTrue(d.instructions)
    }

    @Test fun theKeywordIsRecognisedWithAccentsCapitalsAndAsTwoWords() {
        assertEquals(1, parse("Instructions Aïwa push main").commands.size)
        assertEquals(1, parse("instruction ai wa push branche").commands.size)
        assertEquals(0, parse("instruction push main").commands.size)
        assertEquals("instruction push main", parse("instruction push main").message)
    }

    @Test fun wordsAfterTheKeywordThatAreNotCommandsStayTextForClaude() {
        val d = parse("instruction aiwa push branche puis fais attention aux accents")
        assertEquals(listOf<VoiceCommand>(VoiceCommand.PushMain(false)), d.commands)
        assertEquals("fais attention aux accents", d.message)
    }

    @Test fun anAndInsideTheMessageIsKeptButTheOnesBetweenCommandsAreDropped() {
        val d = parse("instruction aiwa modèle opus et push main fais une page et un site")
        assertEquals(2, d.commands.size)
        assertEquals("fais une page et un site", d.message)
    }

    @Test fun theMessageBeforeAndTheTextAfterAreJoined() {
        val d = parse("ajoute un bouton instruction aiwa push main mets-le en rouge")
        assertEquals("ajoute un bouton mets-le en rouge", d.message)
    }

    @Test fun aModelIsNamedByItsFamilyAndOptionallyItsVersion() {
        fun model(text: String) = (parse("instruction aiwa $text").commands.single() as VoiceCommand.Model).label
        assertEquals("Opus 5.5", model("modèle opus"))
        assertEquals("Opus 4.8", model("modèle opus 4.8"))
        assertEquals("Opus 4.8", model("modèle opus 4,8"))
        assertEquals("Sonnet 5.5", model("modèle sonnet 5 5"))
        assertEquals("Sonnet 4.6", model("modèle sonnet 4.6"))
        assertEquals("Opus 4.8 · 1M", model("modèle opus 4.8 un million"))
        assertEquals("Fable 5.1", model("modèle fable"))
        assertEquals("Auto (défaut du compte)", model("modèle automatique"))
    }

    @Test fun aModelThatDoesNotExistChangesNothingAndIsSaid() {
        val d = parse("instruction aiwa modèle opus 9.9")
        assertTrue(d.commands.isEmpty())
        assertEquals(1, d.problems.size)
        assertTrue(d.problems[0].contains("inconnu"))
    }

    @Test fun thePushIsMainOrBranchAndNothingElse() {
        assertEquals(VoiceCommand.PushMain(true), parse("instruction aiwa push sur main").commands.single())
        assertEquals(VoiceCommand.PushMain(true), parse("instruction aiwa push direct").commands.single())
        assertEquals(VoiceCommand.PushMain(false), parse("instruction aiwa push sur une branche").commands.single())
        val d = parse("instruction aiwa push bientôt")
        assertTrue(d.commands.isEmpty())
        assertEquals(1, d.problems.size)
    }

    @Test fun theDeploymentModes() {
        fun mode(text: String) = (parse("instruction aiwa $text").commands.single() as VoiceCommand.Deploy).mode
        assertEquals("pages", mode("déploiement pages"))
        assertEquals("pages", mode("déploiement github pages"))
        assertEquals("android", mode("déploiement en android"))
        assertEquals("android", mode("déploiement apk"))
        assertEquals("store", mode("déploiement store"))
        assertEquals("aiwa", mode("déploiement aiwa"))
        assertEquals("aiwa", mode("déploiement ai wa"))
        assertEquals("none", mode("déploiement aucun"))
        val d = parse("instruction aiwa déploiement partout")
        assertTrue(d.commands.isEmpty())
        assertEquals(1, d.problems.size)
    }

    @Test fun aNewSessionIsSaidEitherWay() {
        assertEquals(VoiceCommand.NewSession, parse("instruction aiwa nouvelle session").commands.single())
        assertEquals(VoiceCommand.NewSession, parse("instruction aiwa session nouvelle").commands.single())
    }

    @Test fun anExistingSessionIsNamedByAPieceOfItsTitleThatMatchesOne() {
        assertEquals(VoiceCommand.SelectSession("session_C", "Site vitrine"), parse("instruction aiwa session vitrine").commands.single())
        assertEquals(VoiceCommand.SelectSession("session_B", "Widget du soir"), parse("instruction aiwa session widget du soir").commands.single())
        val many = parse("instruction aiwa session widget")
        assertTrue(many.commands.isEmpty())
        assertTrue(many.problems.single().contains("plusieurs"))
        val none = parse("instruction aiwa session inexistante")
        assertTrue(none.commands.isEmpty())
        assertTrue(none.problems.single().contains("introuvable"))
    }

    @Test fun aRepositoryIsNamedByItsNameAndNeverGuessed() {
        assertEquals(VoiceCommand.Repo("theodoreyong9/Aiwa_store"), parse("instruction aiwa dépôt Aiwa store").commands.single())
        assertEquals(VoiceCommand.Repo("theodoreyong9/YourMinedApp"), parse("instruction aiwa dépôt your mined app").commands.single())
        assertEquals(VoiceCommand.Repo("theodoreyong9/Jobber"), parse("instruction aiwa dépôt jobber").commands.single())
        val ambiguous = parse("instruction aiwa dépôt aiwa")
        assertTrue("two repositories start with Aiwa: none is taken", ambiguous.commands.isEmpty())
        assertTrue(ambiguous.problems.single().contains("plusieurs"))
        val unknown = parse("instruction aiwa dépôt zorglub")
        assertTrue(unknown.commands.isEmpty())
        assertTrue(unknown.problems.single().contains("introuvable"))
    }

    @Test fun aRepositoryNameDoesNotSwallowTheWordsThatFollowIt() {
        val d = parse("instruction aiwa dépôt jobber fais une page")
        assertEquals(VoiceCommand.Repo("theodoreyong9/Jobber"), d.commands.single())
        assertEquals("fais une page", d.message)
    }

    @Test fun theWholeChain() {
        val d = parse("corrige le bug instruction aiwa nouvelle session dépôt jobber modèle sonnet push branche déploiement aucun")
        assertEquals("corrige le bug", d.message)
        assertEquals(
            listOf(
                VoiceCommand.NewSession, VoiceCommand.Repo("theodoreyong9/Jobber"), VoiceCommand.Model("claude-sonnet-5-5", "Sonnet 5.5"),
                VoiceCommand.PushMain(false), VoiceCommand.Deploy("none", "aucun"),
            ),
            d.commands,
        )
        assertEquals(listOf("nouvelle session", "dépôt Jobber", "modèle Sonnet 5.5", "push branche", "déploiement aucun"), d.commands.map { describeCommand(it) })
    }

    @Test fun askingWhatTheWidgetIsOnIsAReportAndNotTextForClaude() {
        for (phrase in listOf("t'es sur quoi", "t'es sur quoi là", "tu es sur quoi là", "rapport", "donne-moi le bilan", "statut")) {
            val d = parse("instruction aiwa $phrase")
            assertTrue("$phrase: " + d.commands, d.commands.contains(VoiceCommand.Report))
        }
        val d = parse("instruction aiwa modèle opus t'es sur quoi là")
        assertEquals(listOf(VoiceCommand.Model("claude-opus-5-5", "Opus 5.5"), VoiceCommand.Report), d.commands)
        assertEquals("", d.message)
    }

    @Test fun theReportReadsAllThePillsAndWarnsWhenTheBackendIsDown() {
        assertEquals(
            "Dépôt Aiwa store. Modèle Opus 5.5. Push direct sur main. Déploiement Pages. Session Widget du matin.",
            statusReport("theodoreyong9/Aiwa_store", "Opus 5.5", true, "pages", "Widget du matin", true),
        )
        val bare = statusReport(null, "automatique", false, "none", null, false)
        assertTrue(bare.startsWith("Aucun dépôt choisi. Modèle automatique. Push sur une branche. Aucun déploiement. Pas de session en cours."))
        assertTrue(bare.contains("Termux ne répond pas"))
    }

    @Test fun whenTheWakeWordWasHeardTheWholeTextIsCommands() {
        val d = parseDictation("Aiwa modèle opus push main fais une page", models, sessions, repos, startInInstructions = true)
        assertEquals(listOf(VoiceCommand.Model("claude-opus-5-5", "Opus 5.5"), VoiceCommand.PushMain(true)), d.commands)
        assertEquals("fais une page", d.message)
        assertTrue(d.instructions)
        val none = parseDictation("rien de tout cela", models, sessions, repos, startInInstructions = true)
        assertEquals("rien de tout cela", none.message)
        assertTrue(none.commands.isEmpty())
    }

    @Test fun theWakeWordIsInstructionWhateverFollows() {
        assertTrue(isWakeWord("instruction"))
        assertTrue(isWakeWord("Instructions Aïwa"))
        assertTrue(isWakeWord("bonjour l'instruction"))
        assertFalse(isWakeWord("construction"))
        assertFalse(isWakeWord("bonjour tout le monde"))
    }

    @Test fun stopClaudeIsAKeywordAndAWakePhrase() {
        for (phrase in listOf("stop", "stop claude", "arrête claude", "arrête-toi", "stoppe tout", "arrête")) {
            assertTrue("$phrase", parse("instruction aiwa $phrase").commands.contains(VoiceCommand.StopClaude))
        }
        assertEquals("arrêter is not arrête", "arrêter ne sert à rien", parse("instruction aiwa arrêter ne sert à rien").message)
        val notStop = parse("instruction aiwa je ne t'arrête pas de coder")
        assertTrue(notStop.commands.isEmpty())
        assertEquals(WakeKind.STOP, wakeKind("stop claude"))
        assertEquals(WakeKind.STOP, wakeKind("arrête claude"))
        assertEquals(WakeKind.STOP, wakeKind("mon agent arrête claude"))
        assertEquals(null, wakeKind("claude"))
        // a stop is never read back for confirmation: it is not one of the things to apply
        assertEquals(null, recapSentence(parse("instruction aiwa stop")))
        assertEquals("J'applique : push main. Tu confirmes ?", recapSentence(parse("instruction aiwa stop push main")))
    }

    @Test fun nonCestPasBonArreteCancelsAtTheEndOfWhatWasHeard() {
        assertTrue(endsWithCancelPhrase("fais une page non c'est pas bon arrête"))
        assertTrue(endsWithCancelPhrase("non c'est pas bon"))
        assertTrue(endsWithCancelPhrase("Non, ce n'est pas bon"))
        assertTrue(endsWithCancelPhrase("modèle opus annule"))
        assertTrue(endsWithCancelPhrase("modèle opus annule tout"))
        assertFalse(endsWithCancelPhrase("annule la commande de pizza et envoie le mail"))
        assertFalse(endsWithCancelPhrase("je dis non mais ce n'est pas mauvais du tout, c'est bon pour moi un jour"))
        assertFalse(endsWithCancelPhrase("fais une page"))
        assertFalse(endsWithCancelPhrase(""))
    }

    @Test fun theWakePhrasesAreMonAgentAndInstruction() {
        assertEquals(WakeKind.AGENT, wakeKind("mon agent"))
        assertEquals(WakeKind.AGENT, wakeKind("Mon agent, t'en es où là"))
        assertEquals(WakeKind.INSTRUCTION, wakeKind("instruction"))
        assertEquals(WakeKind.AGENT, wakeKind("instruction mon agent"))
        assertEquals(null, wakeKind("agent immobilier"))
        assertEquals(null, wakeKind("bonjour"))
        assertTrue(isWakeWord("mon agent"))
    }

    @Test fun theWakePhraseAndTheQuestionAreNotTextForClaude() {
        val d = parseDictation("mon agent t'en es où là modèle opus push main", models, sessions, repos, startInInstructions = true)
        assertEquals(listOf(VoiceCommand.Report, VoiceCommand.Model("claude-opus-5-5", "Opus 5.5"), VoiceCommand.PushMain(true)), d.commands)
        assertEquals("", d.message)
        val onlyQuestion = parseDictation("t'en es où", models, sessions, repos, startInInstructions = true)
        assertEquals(listOf<VoiceCommand>(VoiceCommand.Report), onlyQuestion.commands)
        assertEquals("", onlyQuestion.message)
        val agentWord = parseDictation("mon agent immobilier", models, sessions, repos, startInInstructions = true)
        assertEquals("immobilier", agentWord.message)
        val kept = parseDictation("fais une page pour mon agent", models, sessions, repos, startInInstructions = true)
        assertEquals("fais une page pour mon agent", kept.message)
    }

    @Test fun beforeAHandsFreeDictationIsAppliedTheWholeOfItIsReadBack() {
        val d = parse("fais une page d'accueil instruction aiwa modèle opus push main dépôt jobber")
        assertEquals(
            "J'applique : modèle Opus 5.5, push main, dépôt Jobber. J'envoie à Claude : fais une page d'accueil. Tu confirmes ?",
            recapSentence(d),
        )
        assertEquals(null, recapSentence(parse("instruction aiwa t'es sur quoi")))
        val problem = recapSentence(parse("instruction aiwa modèle opus 9.9 push main"))!!
        assertTrue(problem.contains("Je n'ai pas compris"))
        assertTrue(problem.startsWith("J'applique : push main"))
    }
}
