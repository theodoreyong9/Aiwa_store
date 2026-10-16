package com.aiwa.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignRequestTest {
    @Test fun aNameBecomesAPlainFolderName() {
        assertEquals("sites-depasses-lausanne", CampaignRequest.slug("Sites dépassés — Lausanne"))
        assertEquals("campagne", CampaignRequest.slug("  ✨ "))
        assertTrue(CampaignRequest.slug("a".repeat(120)).length <= 50)
    }

    @Test fun theRequestNamesTheFolderAndWhatWasTicked() {
        val text = CampaignRequest.message("Démo", "demo", false, listOf("cv.pdf"), listOf("o/site"), listOf("video", "page"), listOf("app", "aiwa"), "fais court")
        assertTrue(text.contains("docs/campagnes/demo/"))
        assertTrue(text.contains("docs/cv.pdf"))
        assertTrue(text.contains("o/site"))
        assertTrue(text.contains("une vidéo"))
        assertTrue(text.contains("app, aiwa"))
        assertTrue(text.contains("une page"))
        assertTrue(text.contains("stop campagne"))
        assertTrue(text.contains("Ma demande : fais court"))
        assertTrue(!text.contains("TEST"))
        val test = CampaignRequest.message("Démo", "demo", true, emptyList(), emptyList(), emptyList(), emptyList(), "")
        assertTrue(test.contains("TEST"))
        assertTrue(test.contains("Aucun document"))
    }

    @Test fun theMailAndTheChatCarryTheNewLinks() {
        val p = Proposal("a", "Ana", "a@b.ch", "S", "Bonjour", "", "", "", docsUrl = "https://d", appUrl = "https://a", aiwaUrl = "https://w")
        val body = ProposalMail.draft(p, "", false).body
        assertTrue(body.contains("Documents : https://d") && body.contains("Application : https://a") && body.contains("App Aiwa : https://w"))
        assertTrue(ProposalShare.message(p, "", false).contains("https://w"))
    }
}
