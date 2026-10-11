package com.aiwa.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordsTest {
    @Test fun aWordSaidThreeTimesKeepsTheDistinctSpellingsTheEngineHeard() {
        val c = WakeWords.check(listOf("ail oua", "ai oua", "Ail oua !"))
        assertEquals(WakeWords.Check.Ok(listOf("ail oua", "ai oua")), c)
    }

    @Test fun theUnknownMarkAndEmptyTextsAreNotAnAttempt() {
        val c = WakeWords.check(listOf("[unk]", "", "ail oua", "ai oua"))
        assertEquals(WakeWords.Check.Ok(listOf("ail oua", "ai oua")), c)
        assertTrue(WakeWords.check(listOf("[unk]", "", "ail oua")) is WakeWords.Check.Rejected)
        assertTrue(WakeWords.check(emptyList()) is WakeWords.Check.Rejected)
    }

    @Test fun aTooShortOrTooCommonWordIsRefusedWithTheReason() {
        val short = WakeWords.check(listOf("ail", "ail")) as WakeWords.Check.Rejected
        assertTrue(short.reason, short.reason.contains("trop court"))
        val common = WakeWords.check(listOf("bonjour merci", "bonjour merci")) as WakeWords.Check.Rejected
        assertTrue(common.reason, common.reason.contains("trop courants"))
        val origin = WakeWords.check(listOf("mon agent", "mon agent")) as WakeWords.Check.Rejected
        assertTrue(origin.reason, origin.reason.contains("déjà"))
    }

    @Test fun theGrammarHoldsTheOriginPhrasesTheVariantsAndTheUnknownMark() {
        assertEquals("[\"mon agent\", \"instruction\", \"stop claude\", \"arrête claude\", \"[unk]\"]", WakeWords.grammar(emptyList()))
        val g = WakeWords.grammar(listOf("ail oua", "Ai oua"))
        assertTrue(g, g.contains("\"ail oua\"") && g.contains("\"ai oua\"") && g.endsWith("\"[unk]\"]"))
    }

    @Test fun theChosenWordWakesLikeMonAgentAndOnlyAsWholeWords() {
        val v = listOf("ail oua")
        assertEquals(WakeKind.AGENT, wakeKind("ail oua", v))
        assertEquals(WakeKind.AGENT, wakeKind("Ail oua, t'en es où", v))
        assertEquals(null, wakeKind("ail ouaté", v))
        assertEquals(null, wakeKind("ail oua"))                       // without the variants: not a wake word
        assertEquals(WakeKind.STOP, wakeKind("ail oua stop claude", v))
        assertEquals(WakeKind.INSTRUCTION, wakeKind("instruction", v))
        assertFalse(WakeWords.heardCustom("", v))
    }
}
