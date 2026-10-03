package com.aiwa.bridge

import java.util.Base64
import java.util.Random
import java.util.zip.Inflater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// The same decoding the Store does in the browser: base64url, then `deflate-raw` (apps/web/src/publish-ui.js).
private fun unpack(pattern: Regex, url: String): Pair<String, String> {
    val match = pattern.matchEntire(url)
    assertNotNull("not an address the page's fragment reader accepts: ${url.take(120)}", match)
    val packed = Base64.getUrlDecoder().decode(match!!.groupValues[2])
    val inflater = Inflater(true)
    inflater.setInput(packed)
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (!inflater.finished()) {
        val n = inflater.inflate(buffer)
        if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
        out.write(buffer, 0, n)
    }
    inflater.end()
    return match.groupValues[1] to out.toString("UTF-8")
}

// The exact pattern of the Store's fragment reader (apps/web/src/publish-ui.js).
private val STORE = Regex("^https://appassets\\.androidplatform\\.net/assets/web/index\\.html#publish=(?:code|aiwa);([A-Za-z0-9_.-]{1,60});([A-Za-z0-9_-]+)$")

class StoreLinkTest {
    private val app = "<!doctype html><title>Demo</title><script>/* é ✓ */</script>\n" + "<!-- pad -->\n".repeat(400)

    @Test fun theAddressCarriesTheNameAndTheCodeBackExactly() {
        val (name, code) = unpack(STORE, storePublishUrl("demo-app", app)!!)
        assertEquals("demo-app", name)
        assertEquals(app, code)
    }

    @Test fun theCodeIsPackedSmallerThanItIs() {
        assertTrue(storePublishUrl("demo-app", app)!!.length < app.length / 2)
    }

    @Test fun aNameThatIsNotAPlainFileNameIsRefused() {
        for (bad in listOf("", "../x", "a b", "a;b", "x".repeat(61), "é")) {
            assertNull("accepted: $bad", storePublishUrl(bad, app))
        }
    }

    @Test fun somethingTooLongForAnAddressIsLeftToTheClipboard() {
        val noise = Base64.getEncoder().encodeToString(ByteArray(400_000).also { Random(7).nextBytes(it) })
        assertNull(storePublishUrl("noise", noise))
    }

    @Test fun anotherBaseAddressCanBeGiven() {
        assertTrue(storePublishUrl("a", "x", base = "http://localhost:8080/")!!.startsWith("http://localhost:8080/#publish=code;a;"))
    }

    @Test fun theKindIsInTheAddress_andOnlyKnownKindsAreAccepted() {
        assertTrue(storePublishUrl("a", "x", kind = "aiwa")!!.contains("#publish=aiwa;a;"))
        assertTrue(storePublishUrl("a", "x")!!.contains("#publish=code;a;"))
        assertNull(storePublishUrl("a", "x", kind = "sphere"))
    }

    @Test fun theWidgetsAddressFollowsTheCopyThatIsServed() {
        val url = storePublishUrl("demo-app", app)!!
        assertEquals(url, servedAddress(url, downloaded = false))
        val moved = servedAddress(url, downloaded = true)
        assertTrue(moved.startsWith("https://appassets.androidplatform.net/site/index.html#publish=code;demo-app;"))
        assertEquals(url.substringAfter('#'), moved.substringAfter('#'))
        // an address that is not the Store's page is left alone
        assertEquals("https://example.com/", servedAddress("https://example.com/", downloaded = true))
    }
}
