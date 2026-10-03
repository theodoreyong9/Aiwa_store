package com.aiwa.bridge

import java.io.File
import java.security.MessageDigest
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// A release signed by apps/web/release.mjs (Node, @noble/curves) with the seed 01 02 03 … 20: the Kotlin side must accept what the
// Node side signs. The files are the ones the release lists.
private const val NODE_PUBLIC_KEY = "79b5562e8fe654f94078b112e8a98ba7901f853ae695bed7e0e3910bad049664"
private const val NODE_INDEX = "<!doctype html><title>Aiwa</title>"
private const val NODE_APP = "console.log(\"aiwa\")"
private const val NODE_RELEASE =
    "{\"format\":\"aiwa-site/1\",\"version\":\"2026-01-01T00:00:00Z-abc1234\",\"createdAt\":1767225600000,\"files\":{\"index.html\":\"f3513afaa733fb788e06bdeb4025e65df85f53a7241f195ad994d1bcb35149bf\",\"app.js\":\"18ff9413e8c24d7186a276e7621af0747d3eaeea9ae35e4f177ea4ee4dc3d192\"}}"
private const val NODE_SIGNATURE =
    "851a4dfdfd2231d8ce464d68a58b7285734a248390d1cd722f12bfee6acaa813943db465eb22bb452763d2904f1cac146e64eca880c74f93353755e04221790a"

private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
private fun sha256(bytes: ByteArray) = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

/** A key pair made here, to sign releases of all kinds (the Node vector above only covers the honest one). */
private class Signer(seedByte: Int) {
    private val key = Ed25519PrivateKeyParameters(ByteArray(32) { seedByte.toByte() }, 0)
    val publicKey: String = hex(key.generatePublicKey().encoded)
    fun sign(bytes: ByteArray): String {
        val signer = Ed25519Signer()
        signer.init(true, key)
        signer.update(bytes, 0, bytes.size)
        return hex(signer.generateSignature())
    }
}

/** What a site is made of in these tests: the release's bytes, its signature, and the files. */
private class Site(val releaseBytes: ByteArray, val signature: String, val files: Map<String, ByteArray>)

private fun site(signer: Signer, createdAt: Long, files: Map<String, String>, listed: Map<String, String> = files.mapValues { sha256(it.value.toByteArray()) }, format: String = "aiwa-site/1"): Site {
    val json = JSONObject().put("format", format).put("version", "v$createdAt").put("createdAt", createdAt)
    json.put("files", JSONObject(listed))
    val bytes = json.toString().toByteArray()
    return Site(bytes, signer.sign(bytes), files.mapValues { it.value.toByteArray() })
}

private val PAGE = mapOf("index.html" to "<html>one</html>", "app.js" to "var one = 1")

/** Serves a Site the way the Pages site does: release.json, release.sig and each file. Every address asked is recorded. */
private class FakeSite(var site: Site) : SiteFetcher {
    val asked = mutableListOf<String>()
    override fun get(url: String, maxBytes: Int): ByteArray {
        asked += url
        val path = url.removePrefix("https://aiwa.example/site/")
        return when (path) {
            "release.json" -> site.releaseBytes
            "release.sig" -> (site.signature + "\n").toByteArray()
            else -> site.files[path] ?: throw java.io.IOException("404 $url")
        }
    }
}

private fun rejected(block: () -> Unit): SiteRejected {
    try { block() } catch (err: SiteRejected) { return err }
    fail("expected the release to be rejected")
    throw AssertionError()
}

class SiteReleaseTest {
    @get:Rule val tmp = TemporaryFolder()

    private val key = Signer(7)
    private fun signed(s: Site) = verifyRelease(s.releaseBytes, s.signature, key.publicKey)
    private fun store() = SiteStore(File(tmp.root, "site"))
    private fun updater(fetcher: SiteFetcher, store: SiteStore, publicKey: String = key.publicKey) =
        SiteUpdater("https://aiwa.example/site/", publicKey, fetcher, store)

    // ---- the signature -------------------------------------------------------------------------------------------------

    @Test fun `a release signed by the Node side is accepted, and its files are checked against it`() {
        val release = verifyRelease(NODE_RELEASE.toByteArray(), NODE_SIGNATURE, NODE_PUBLIC_KEY)
        assertEquals(1767225600000L, release.createdAt)
        assertEquals("2026-01-01T00:00:00Z-abc1234", release.version)
        assertEquals(setOf("index.html", "app.js"), release.files.keys)
        checkSiteFile(release, "index.html", NODE_INDEX.toByteArray())
        checkSiteFile(release, "app.js", NODE_APP.toByteArray())
        rejected { checkSiteFile(release, "app.js", "console.log(\"evil\")".toByteArray()) }
        rejected { checkSiteFile(release, "other.js", NODE_APP.toByteArray()) }
    }

    @Test fun `one changed byte, another key, or a bad signature is refused`() {
        val bytes = NODE_RELEASE.toByteArray()
        val tampered = bytes.copyOf().also { it[it.size - 5] = (it[it.size - 5] + 1).toByte() }
        rejected { verifyRelease(tampered, NODE_SIGNATURE, NODE_PUBLIC_KEY) }
        rejected { verifyRelease(bytes, NODE_SIGNATURE, Signer(9).publicKey) }
        rejected { verifyRelease(bytes, "00".repeat(64), NODE_PUBLIC_KEY) }
        rejected { verifyRelease(bytes, "not hex", NODE_PUBLIC_KEY) }
        rejected { verifyRelease(bytes, "", NODE_PUBLIC_KEY) }
        rejected { verifyRelease(bytes, NODE_SIGNATURE, "") }
    }

    @Test fun `a release the site key signed is still refused if it is malformed`() {
        val ok = "0".repeat(64)
        fun refuse(s: Site) = rejected { verifyRelease(s.releaseBytes, s.signature, key.publicKey) }
        // not the Aiwa format
        refuse(site(key, 5, PAGE, format = "something-else/1"))
        // a file name that climbs out, or is absolute, or hides
        for (name in listOf("../evil.js", "a/../../evil.js", "/etc/passwd", ".hidden", "a//b.js", "a\\b.js", "a b.js", "")) {
            refuse(site(key, 5, PAGE, listed = mapOf("index.html" to ok, name to ok)))
        }
        // a hash that is not a SHA-256
        refuse(site(key, 5, PAGE, listed = mapOf("index.html" to "abc")))
        refuse(site(key, 5, PAGE, listed = mapOf("index.html" to "A".repeat(64))))
        // no index.html, or nothing at all
        refuse(site(key, 5, mapOf("app.js" to "x")))
        refuse(site(key, 5, emptyMap()))
        // no date
        refuse(site(key, 0, PAGE))
        // too many files
        refuse(site(key, 5, PAGE, listed = (0..SITE_MAX_FILES).associate { "f$it.js" to ok } + ("index.html" to ok)))
    }

    @Test fun `a file larger than the limit is refused even if the release lists it`() {
        val big = ByteArray(SITE_MAX_FILE_BYTES + 1)
        val release = signed(site(key, 5, mapOf("index.html" to "x"), listed = mapOf("index.html" to sha256(big))))
        rejected { checkSiteFile(release, "index.html", big) }
    }

    // ---- the update ----------------------------------------------------------------------------------------------------

    @Test fun `a signed release is downloaded, checked and kept for the next start`() {
        val store = store()
        assertNull(store.latest())
        val result = updater(FakeSite(site(key, 100, PAGE)), store).update(null)
        assertTrue(result is SiteUpdate.Updated)
        assertEquals("v100", (result as SiteUpdate.Updated).version)
        assertNull("the page being served is not changed while the app runs", store.current())
        assertEquals(100L, store.pending()?.createdAt)
        // the next start serves it
        val choice = store.choose(null)
        assertTrue(choice.cached)
        assertNull(store.pending())
        for ((path, content) in PAGE) assertArrayEquals(content.toByteArray(), File(store.currentDir, path).readBytes())
        assertTrue(File(store.currentDir, "release.json").exists())
    }

    @Test fun `a newer release replaces the whole of the old one, at the next start`() {
        val store = store()
        updater(FakeSite(site(key, 100, PAGE + ("old.js" to "gone"))), store).update(null)
        store.choose(null)
        assertTrue(File(store.currentDir, "old.js").exists())
        val result = updater(FakeSite(site(key, 200, mapOf("index.html" to "<html>two</html>", "app.js" to "var two = 2"))), store).update(store.latest())
        assertTrue(result is SiteUpdate.Updated)
        // running: the files of the release being served are exactly as they were
        assertEquals(100L, store.current()?.createdAt)
        assertEquals("<html>one</html>", File(store.currentDir, "index.html").readText())
        assertEquals("var one = 1", File(store.currentDir, "app.js").readText())
        assertEquals(200L, store.latest()?.createdAt)
        // next start
        assertEquals(200L, store.choose(null).have?.createdAt)
        assertEquals("<html>two</html>", File(store.currentDir, "index.html").readText())
        assertFalse("a file the new release does not list is not kept", File(store.currentDir, "old.js").exists())
        for (leftover in listOf("incoming", "pending", "old")) assertFalse(File(tmp.root, "site/$leftover").exists())
    }

    @Test fun `an older release, or the same files again, is not an update`() {
        val store = store()
        updater(FakeSite(site(key, 100, PAGE)), store).update(null)
        store.choose(null)
        // a signed release from the past, replayed
        assertTrue(updater(FakeSite(site(key, 50, mapOf("index.html" to "<html>old</html>"))), store).update(store.latest()) is SiteUpdate.UpToDate)
        // newer by the clock, but the very same files: nothing to download
        val same = FakeSite(site(key, 300, PAGE))
        assertTrue(updater(same, store).update(store.latest()) is SiteUpdate.UpToDate)
        assertEquals("only release.json and release.sig were read", listOf("https://aiwa.example/site/release.json", "https://aiwa.example/site/release.sig"), same.asked)
        assertNull(store.pending())
        assertEquals(100L, store.current()?.createdAt)
        assertEquals("<html>one</html>", File(store.currentDir, "index.html").readText())
    }

    @Test fun `a release not signed by the site key installs nothing and downloads no file`() {
        val store = store()
        val fetcher = FakeSite(site(Signer(9), 100, PAGE))
        rejected { updater(fetcher, store).update(null) }
        assertNull(store.latest())
        assertFalse(File(tmp.root, "site").exists())
        assertEquals(2, fetcher.asked.size)
    }

    @Test fun `one file that is not the one the release lists installs nothing at all`() {
        val store = store()
        updater(FakeSite(site(key, 100, PAGE)), store).update(null)
        store.choose(null)
        // the release is signed and says one thing; the server serves another for app.js
        val lying = site(key, 200, PAGE.mapValues { it.value + " // new" }, listed = PAGE.mapValues { sha256((it.value + " // new").toByteArray()) })
        val fetcher = FakeSite(Site(lying.releaseBytes, lying.signature, lying.files + ("app.js" to "var evil = 1".toByteArray())))
        rejected { updater(fetcher, store).update(store.latest()) }
        assertNull(store.pending())
        assertEquals("the old release is untouched", 100L, store.current()?.createdAt)
        assertEquals("<html>one</html>", File(store.currentDir, "index.html").readText())
        assertEquals("var one = 1", File(store.currentDir, "app.js").readText())
        assertFalse(File(tmp.root, "site/incoming").exists())
    }

    @Test fun `a file that cannot be read installs nothing`() {
        val store = store()
        val full = site(key, 100, PAGE)
        val partial = Site(full.releaseBytes, full.signature, mapOf("index.html" to PAGE.getValue("index.html").toByteArray()))
        try { updater(FakeSite(partial), store).update(null); fail("the update should have failed") } catch (err: java.io.IOException) { }
        assertNull(store.latest())
        assertFalse(File(tmp.root, "site/pending").exists())
    }

    // ---- which copy to start from --------------------------------------------------------------------------------------

    private fun release(createdAt: Long) = signed(site(key, createdAt, PAGE))

    @Test fun `the page of the APK is used until a newer one has been downloaded`() {
        val store = store()
        val bundled = release(100)
        assertFalse(store.choose(bundled).cached)
        assertEquals(100L, store.choose(bundled).have?.createdAt)
        updater(FakeSite(site(key, 200, mapOf("index.html" to "<html>two</html>"))), store).update(bundled)
        val choice = store.choose(bundled)
        assertTrue(choice.cached)
        assertEquals(200L, choice.have?.createdAt)
    }

    @Test fun `after the app itself is updated, an older downloaded copy is dropped`() {
        val store = store()
        updater(FakeSite(site(key, 200, PAGE)), store).update(null)
        store.choose(null)
        assertNotNull(store.current())
        val choice = store.choose(release(300))        // the new APK carries a page made after the one downloaded
        assertFalse(choice.cached)
        assertEquals(300L, choice.have?.createdAt)
        assertNull(store.current())
        assertFalse(File(tmp.root, "site/current").exists())
    }

    @Test fun `a download older than the page of the APK is not used either`() {
        val store = store()
        updater(FakeSite(site(key, 200, PAGE)), store).update(null)
        assertFalse(store.choose(release(300)).cached)
        assertNull(store.latest())
    }

    @Test fun `without a dated page in the APK, a downloaded one is used`() {
        val store = store()
        updater(FakeSite(site(key, 200, PAGE)), store).update(null)
        assertTrue(store.choose(null).cached)
    }

    @Test fun `a damaged downloaded copy is not used`() {
        val store = store()
        updater(FakeSite(site(key, 200, PAGE)), store).update(null)
        store.choose(null)
        File(store.currentDir, "release.json").writeText("not json")
        assertFalse(store.choose(release(100)).cached)
    }
}
