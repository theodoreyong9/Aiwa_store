package com.aiwa.bridge

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONObject

/*
 * The Store's page is not run from wherever it is served: the app downloads it and serves it from its own storage, under its own origin
 * (so the wallet's storage is the same whichever copy is served).
 *
 * A release is `release.json`: every file of the page with its SHA-256. The app reads each file and compares it with its hash, so that
 * what it serves is one whole release, never a mix of two and never a file cut short. There is no signature: what the site publishes
 * is what the phones run, and the site's owner is the GitHub account that publishes it (HTTPS carries it).
 * This is the Kotlin side of apps/web/release.mjs; both are tested against the same release.
 */

/** What a verified release.json says. */
class SiteRelease(val version: String, val createdAt: Long, val files: Map<String, String>)

const val SITE_MAX_FILES = 400
const val SITE_MAX_FILE_BYTES = 5 * 1024 * 1024
const val SITE_MAX_TOTAL_BYTES = 24 * 1024 * 1024
const val SITE_FORMAT = "aiwa-site/1"

private val SAFE_PATH = Regex("[A-Za-z0-9][A-Za-z0-9._@-]*(/[A-Za-z0-9][A-Za-z0-9._@-]*)*")
private val SHA256_HEX = Regex("[0-9a-f]{64}")

class SiteRejected(message: String) : SecurityException(message)

private fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** What release.json says, if it is well formed; otherwise it throws SiteRejected. */
fun parseRelease(bytes: ByteArray): SiteRelease {
    val json = try { JSONObject(String(bytes, Charsets.UTF_8)) } catch (err: Exception) { throw SiteRejected("the release is not JSON") }
    if (json.optString("format") != SITE_FORMAT) throw SiteRejected("the release is not $SITE_FORMAT")
    val version = json.optString("version")
    val createdAt = json.optLong("createdAt", -1)
    if (version.isEmpty() || createdAt <= 0) throw SiteRejected("the release has no version or date")
    val list = json.optJSONObject("files") ?: throw SiteRejected("the release lists no files")
    val files = LinkedHashMap<String, String>()
    for (path in list.keys()) {
        val hash = list.optString(path)
        if (!SAFE_PATH.matches(path) || path.split("/").contains("..")) throw SiteRejected("the file name \"$path\" is not allowed")
        if (!SHA256_HEX.matches(hash)) throw SiteRejected("the hash of $path is not a SHA-256")
        files[path] = hash
    }
    if (files.isEmpty() || files.size > SITE_MAX_FILES) throw SiteRejected("a release has between 1 and $SITE_MAX_FILES files")
    if (!files.containsKey("index.html")) throw SiteRejected("a release has an index.html")
    return SiteRelease(version, createdAt, files)
}

/** Throws SiteRejected unless `content` is exactly the file `path` of `release`. */
fun checkSiteFile(release: SiteRelease, path: String, content: ByteArray) {
    val wanted = release.files[path] ?: throw SiteRejected("$path is not in the release")
    if (content.size > SITE_MAX_FILE_BYTES) throw SiteRejected("$path is too large")
    if (sha256Hex(content) != wanted) throw SiteRejected("$path is not what the release says")
}

/** How the app reads the site: one GET, at most `maxBytes` long. */
fun interface SiteFetcher {
    fun get(url: String, maxBytes: Int): ByteArray
}

/** The real one: HTTPS, with timeouts, never more than `maxBytes`. */
class HttpSiteFetcher : SiteFetcher {
    override fun get(url: String, maxBytes: Int): ByteArray {
        require(url.startsWith("https://")) { "only https" }
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            if (connection.responseCode != 200) throw IOException("$url answered ${connection.responseCode}")
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            connection.inputStream.use { input ->
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > maxBytes) throw IOException("$url is larger than $maxBytes bytes")
                }
            }
            return out.toByteArray()
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * The copy of the site that this phone holds, in `root`.
 *
 * `current/` is the one the page is being served from. It is never touched while the app runs: a page that loads a script later
 * (the Store splits its code) must find the files of its own release. A download goes to `pending/` as a whole, complete or absent,
 * and replaces `current/` at the next start (`choose`).
 */
class SiteStore(private val root: File) {
    val currentDir: File get() = File(root, "current")
    private val pendingDir: File get() = File(root, "pending")

    private fun read(dir: File): SiteRelease? = try { parseRelease(File(dir, "release.json").readBytes()) } catch (err: Exception) { null }

    /** The release being served, or null when there is none (or it is unreadable). */
    fun current(): SiteRelease? = read(currentDir)

    /** A release downloaded and waiting for the next start. */
    fun pending(): SiteRelease? = read(pendingDir)

    fun clear() { root.deleteRecursively() }

    /** What a new download is compared with: the newest release held. */
    fun latest(): SiteRelease? = listOfNotNull(current(), pending()).maxByOrNull { it.createdAt }

    /** Writes the (already checked) files as the pending release; `current/` is not touched. */
    fun install(releaseBytes: ByteArray, files: Map<String, ByteArray>) {
        val incoming = File(root, "incoming")
        incoming.deleteRecursively()
        for ((path, content) in files) {
            val target = File(incoming, path)
            target.parentFile?.mkdirs()
            target.writeBytes(content)
        }
        File(incoming, "release.json").writeBytes(releaseBytes)
        pendingDir.deleteRecursively()
        if (!incoming.renameTo(pendingDir)) throw IOException("could not keep the new release")
    }

    /** The pending release becomes the current one. */
    fun promote() {
        if (pending() == null) { pendingDir.deleteRecursively(); return }
        val old = File(root, "old")
        old.deleteRecursively()
        if (currentDir.exists() && !currentDir.renameTo(old)) throw IOException("could not set the old release aside")
        if (!pendingDir.renameTo(currentDir)) throw IOException("could not start the new release")
        old.deleteRecursively()
    }
}

/** Which copy of the page to start from, and what an update is compared with. */
class SiteChoice(val cached: Boolean, val have: SiteRelease?)

/**
 * At the start: a downloaded release that is waiting becomes the current one; the current one wins over the page inside the APK only
 * if it is newer. When the APK is the newer (the app was updated), the downloaded copy has no use any more and is removed.
 */
fun SiteStore.choose(bundled: SiteRelease?): SiteChoice {
    try { promote() } catch (err: IOException) { /* the current one stays */ }
    val downloaded = current() ?: return SiteChoice(false, bundled)
    if (bundled == null || downloaded.createdAt > bundled.createdAt) return SiteChoice(true, downloaded)
    clear()
    return SiteChoice(false, bundled)
}

sealed class SiteUpdate {
    object UpToDate : SiteUpdate()
    class Updated(val version: String) : SiteUpdate()
}

/**
 * Looks for a release newer than `have` at `base`, downloads it, checks every file against its hash, and keeps it. Nothing is kept
 * unless all of it checks out. A release that is not newer, or that lists exactly the files already held, is not an update.
 */
class SiteUpdater(private val base: String, private val fetcher: SiteFetcher, private val store: SiteStore) {
    fun update(have: SiteRelease?): SiteUpdate {
        require(base.startsWith("https://") && base.endsWith("/")) { "the site is an https address ending with /" }
        val releaseBytes = fetcher.get("${base}release.json", 512 * 1024)
        val release = parseRelease(releaseBytes)
        if (have != null && (release.createdAt <= have.createdAt || release.files == have.files)) return SiteUpdate.UpToDate
        val files = LinkedHashMap<String, ByteArray>()
        var total = 0
        for (path in release.files.keys) {
            val content = fetcher.get("$base$path", SITE_MAX_FILE_BYTES)
            checkSiteFile(release, path, content)
            total += content.size
            if (total > SITE_MAX_TOTAL_BYTES) throw SiteRejected("the release is larger than $SITE_MAX_TOTAL_BYTES bytes")
            files[path] = content
        }
        store.install(releaseBytes, files)
        return SiteUpdate.Updated(release.version)
    }
}
