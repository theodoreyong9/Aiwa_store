package com.aiwa.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProposalMailTest {
    private val p = Proposal("cafe", "Café du Port", "contact@cafe.ch", "Une maquette pour votre site", "Bonjour,\nVoici une idée.", "900 CHF",
        "https://github.com/o/r/releases/download/videos/a.mp4", "https://o.github.io/r/aiwa-proposals/cafe/")

    @Test fun theMailCarriesThePriceTheLinksAndAlwaysAWayToSayNo() {
        val d = ProposalMail.draft(p, "850 CHF", test = false)
        assertEquals("contact@cafe.ch", d.to)
        assertEquals("Une maquette pour votre site", d.subject)
        assertTrue(d.body.contains("Prix proposé : 850 CHF"))
        assertTrue(d.body.contains("pas le site officiel"))
        assertTrue(d.body.contains("https://o.github.io/r/aiwa-proposals/cafe/"))
        assertTrue(d.body.contains("a.mp4"))
        assertTrue(d.body.endsWith(ProposalMail.STOP_LINE))
    }

    @Test fun theTestModeSendsToNobodyAndSaysSo() {
        val d = ProposalMail.draft(p, "", test = true)
        assertEquals("", d.to)
        assertTrue(d.subject.startsWith("[TEST] "))
        assertFalse(d.body.contains("Prix proposé"))
        assertTrue(d.body.endsWith(ProposalMail.STOP_LINE))
    }

    @Test fun aMissingSubjectOrLinkIsNotWrittenAsEmptyText() {
        val d = ProposalMail.draft(p.copy(subject = "", demoUrl = "", videoUrl = ""), "", test = false)
        assertEquals("Proposition pour Café du Port", d.subject)
        assertFalse(d.body.contains("Maquette"))
        assertFalse(d.body.contains("Vidéo"))
    }
}
