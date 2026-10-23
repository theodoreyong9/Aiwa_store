package com.aiwa.bridge

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionsTest {
    @After fun clean() { VoiceVocabulary.written = emptyMap() }

    @Test fun theRecognitionsAreInTheOrderOfTheBrief() {
        assertEquals(listOf("Réveil", "Envoi", "Validation", "Stop", "Éteins", "Pause", "Retour", "Efface"), Recognitions.SLOTS.filter { it.group == Recognitions.MINE }.map { it.label })
        assertEquals(listOf("Accomplissement", "Rapport", "Question", "Veille", "Écoute"), Recognitions.SLOTS.filter { it.group == Recognitions.AIWA }.map { it.label })
        assertEquals(WakeKind.LIAISON, wakeKind("heille wa"))
        assertEquals(WakeKind.LIAISON, wakeKind("haye wa"))
        assertEquals(WakeKind.STOP, wakeKind("stop la commande"))
        assertEquals(WakeKind.OFF, wakeKind("éteins toi"))
    }

    @Test fun theWakeAndStopWordsAreFoundInsideASentenceAndACommandIsNotAWake() {
        assertEquals(WakeKind.LIAISON, wakeKind("oh heille wa"))
        assertEquals(WakeKind.STOP, wakeKind("stop la commande tout de suite"))
        assertNull(wakeKind("la commande est prête"))
        assertTrue(endsWithCancelPhrase("annule"))
        assertTrue(!endsWithCancelPhrase("annule la commande de pizza et envoie la facture"))
    }

    @Test fun resendWinsOverInstructionAndStopAndOffAreKnown() {
        assertEquals(WakeKind.RESEND, wakeKind("renvoie les instructions"))
        assertEquals(WakeKind.INSTRUCTION, wakeKind("instruction"))
        assertEquals(WakeKind.OFF, wakeKind("éteins"))
        assertEquals(WakeKind.STOP, wakeKind("stop claude"))
    }

    @Test fun aChosenSpellingWakesTheMatchingKind() {
        val more = WakeCustoms(stop = listOf("alt stop"), off = listOf("dodo la"), resend = listOf("redis tout"), instruction = listOf("ordre alors"))
        assertEquals(WakeKind.STOP, wakeKind("alt stop", more = more))
        assertEquals(WakeKind.OFF, wakeKind("dodo la", more = more))
        assertEquals(WakeKind.RESEND, wakeKind("redis tout", more = more))
        assertEquals(WakeKind.INSTRUCTION, wakeKind("ordre alors", more = more))
        assertNull(wakeKind("bonjour tout le monde", more = more))
    }

    @Test fun theWordsWrittenForThePillsAndTheInstructionAreReadByTheParser() {
        val repos = listOf("theodoreyong9/test")
        VoiceVocabulary.written = mapOf("repo" to setOf("projet"), "instruction" to setOf("ordre"))
        val d = parseDictation("ordre projet test", emptyList(), emptyList(), repos, false)
        assertTrue(d.instructions)
        assertTrue(d.commands.any { it is VoiceCommand.Repo })
    }

    @Test fun aWrittenWordIsCheckedForLengthAndCommonness() {
        assertNotNull(Recognitions.checkWritten("repo", "ok"))
        assertNotNull(Recognitions.checkWritten("repo", "deux mots"))
        assertNull(Recognitions.checkWritten("repo", "projet"))
        assertNull(Recognitions.checkWritten("validation", "c'est parti"))
        assertNotNull(Recognitions.checkWritten("validation", ""))
    }

    @Test fun aWrittenPhraseIsHeardAsWholeWords() {
        VoiceVocabulary.written = mapOf("validation" to setOf("c est parti"))
        assertTrue(VoiceVocabulary.phraseHeard("validation", "ok c est parti merci"))
        assertTrue(!VoiceVocabulary.phraseHeard("validation", "c est partit"))
    }

    @Test fun aQuestionIsToBeAnsweredAloudAndACommandIsSentToASession() {
        assertTrue(isQuestion("explique moi le projet"))
        assertTrue(isQuestion("Explique-moi le projet"))
        assertTrue(isQuestion("c'est quoi ce dépôt"))
        assertTrue(isQuestion("pourquoi la vidéo est floue"))
        assertTrue(isQuestion("t'en es où"))
        assertTrue(!isQuestion("fais une vidéo du projet"))
        assertTrue(!isQuestion("ajoute une page contact"))
        assertTrue(!isQuestion("mets-toi sur le repo test"))
        assertTrue(!isQuestion(""))
    }
}
