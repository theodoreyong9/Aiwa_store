package com.aiwa.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PillInstructionsTest {
    private val sessions = listOf(VoiceSession("session_A", "Widget du matin"))
    private val repos = listOf("theodoreyong9/Aiwa_store", "theodoreyong9/test")
    private val pills = Pills(repo = "theodoreyong9/Aiwa_store", model = "automatique", effort = "automatique", pushMain = true, deploy = "pages", videoQuality = "moyen", session = "Widget du matin")

    @Test fun theInstructionModeKeepsOnlyTheSessionAndRepositoryPills() {
        val all = parseDictation("instruction modèle Opus push branche mets-toi sur le dépôt test", emptyList(), sessions, repos, true)
        val kept = pillCommandsOnly(all.commands)
        assertTrue(kept.all { it is VoiceCommand.Repo || it is VoiceCommand.SelectSession || it is VoiceCommand.NewSession })
        assertTrue(kept.none { it is VoiceCommand.PushMain || it is VoiceCommand.Model || it is VoiceCommand.Effort })
    }

    @Test fun theRecapReadsBackBothPillsAndAsksToValidateOrCorrect() {
        val sentence = pillsRecapSentence(pills, listOf(VoiceCommand.Repo("theodoreyong9/test")))!!
        assertTrue(sentence.contains("Session : Widget du matin"))
        assertTrue(sentence.contains("Dépôt : test"))
        assertTrue(sentence.endsWith("Tu valides, ou tu corriges ?"))
        assertNull(pillsRecapSentence(pills, emptyList()))
    }

    @Test fun aCorrectionReplacesThePillOfTheSameKindOnly() {
        val first = listOf(VoiceCommand.Repo("theodoreyong9/test"), VoiceCommand.NewSession)
        val fixed = correctPills(first, listOf(VoiceCommand.Repo("theodoreyong9/Aiwa_store")))
        assertEquals(2, fixed.size)
        assertTrue(fixed.contains(VoiceCommand.NewSession))
        assertEquals("theodoreyong9/Aiwa_store", fixed.filterIsInstance<VoiceCommand.Repo>().single().name)
    }

    @Test fun theSessionIsToldWhereItIsNow() {
        assertEquals("[Aiwa — pastilles] Dépôt : o/r. Session : Mon site.", pillInfoMessage("o/r", "Mon site"))
        assertTrue(pillInfoMessage("o/r", null, "fais une page").endsWith("\n\nfais une page"))
        assertTrue(pillInfoMessage(null, null).contains("nouvelle session"))
    }
}
