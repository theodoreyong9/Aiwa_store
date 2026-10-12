package com.aiwa.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProposalShareTest {
    private val p = Proposal("cafe", "Café du Port", "", "Une maquette", "Bonjour & merci", "900 CHF", "https://x.test/v.mp4", "https://o.github.io/r/aiwa-proposals/cafe/", "+41 79 123 45 67")

    @Test fun onlyAnInternationalNumberOpensAChat() {
        assertEquals("41791234567", ProposalShare.digits("+41 79 123 45 67"))
        assertNull(ProposalShare.digits("079 123 45 67"))
        assertNull(ProposalShare.digits("+0123"))
        assertNull(ProposalShare.digits(""))
    }

    @Test fun whatsappOpensTheChatOfTheNumberWithTheTextEncoded() {
        val url = ProposalShare.whatsapp(p, "850 CHF", test = false)!!
        assertTrue(url, url.startsWith("https://wa.me/41791234567?text=Une%20maquette"))
        assertTrue(url.contains("%26"))                       // the & of the text does not cut the address
        assertTrue(url.contains(java.net.URLEncoder.encode(ProposalMail.STOP_LINE, "UTF-8").replace("+", "%20")))
    }

    @Test fun withoutANumberNothingOpensExceptForATest() {
        val bare = p.copy(phone = "")
        assertNull(ProposalShare.whatsapp(bare, "", test = false))
        assertNull(ProposalShare.smsAddress(bare, test = false))
        assertTrue(ProposalShare.whatsapp(bare, "", test = true)!!.startsWith("https://wa.me/?text=%5BTEST%5D"))
        assertEquals("smsto:", ProposalShare.smsAddress(bare, test = true))
        assertEquals("smsto:+41791234567", ProposalShare.smsAddress(p, test = false))
    }

    @Test fun telegramOpensItsShareWindow() {
        assertTrue(ProposalShare.telegram(p, "", test = false).startsWith("https://t.me/share/url?url=https%3A%2F%2Fo.github.io"))
    }

    @Test fun xOpensAPostOnTheWallWithTheLinks() {
        val url = ProposalShare.x(p, "850 CHF", test = false)
        assertTrue(url, url.startsWith("https://x.com/intent/post?text=Une%20maquette"))
        assertTrue(url, url.contains(java.net.URLEncoder.encode("https://o.github.io/r/aiwa-proposals/cafe/", "UTF-8")))
        assertTrue(url, url.contains(java.net.URLEncoder.encode("https://x.test/v.mp4", "UTF-8")))
    }

    @Test fun anXPostIsCutToItsLengthAndAlwaysKeepsItsLinks() {
        val long = p.copy(subject = "mot ".repeat(200))
        val url = ProposalShare.x(long, "", test = false)
        val text = java.net.URLDecoder.decode(url.substringAfter("text="), "UTF-8")
        val counted = text.replace(Regex("https://\\S+"), "x".repeat(23)).length
        assertTrue("$counted characters", counted <= 280)
        assertTrue(text.endsWith("https://x.test/v.mp4"))
        assertTrue(text.contains("…"))
    }
}
