package com.aiwa.bridge

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater

/** The Store's web app, as the Android app serves it from its own assets (StoreActivity). */
const val STORE_APP_URL = "https://appassets.androidplatform.net/assets/web/index.html"

/** The same page, as served from the copy downloaded from the site (SiteRelease.kt) instead of the one inside the APK. */
const val SITE_APP_URL = "https://appassets.androidplatform.net/site/index.html"

/** An address of the Store, as the widget writes it (always the page inside the APK), moved to the copy that is actually served. */
fun servedAddress(url: String, downloaded: Boolean): String =
    if (downloaded && url.startsWith(STORE_APP_URL)) SITE_APP_URL + url.removePrefix(STORE_APP_URL) else url

// Well under what a WebView accepts in an address and what an Intent can carry (about 1 MB in all). The Store's own limit
// (512 KB of code) packs far below it; a file whose packed form is longer is over that limit anyway.
private const val MAX_FRAGMENT_CHARS = 300_000

// The name as the Store's fragment reader accepts it.
private val FRAGMENT_NAME = Regex("[A-Za-z0-9_.-]{1,60}")

/**
 * The code, raw-deflated (java's Deflater with nowrap = the browser's `deflate-raw`), then base64url
 * without padding. Null when the packed form is too long for an address.
 */
private fun pack(code: String): String? {
    val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
    val packed = ByteArrayOutputStream()
    try {
        deflater.setInput(code.toByteArray(Charsets.UTF_8))
        deflater.finish()
        val buffer = ByteArray(8192)
        while (!deflater.finished()) packed.write(buffer, 0, deflater.deflate(buffer))
    } finally {
        deflater.end()
    }
    val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(packed.toByteArray())
    return if (payload.length > MAX_FRAGMENT_CHARS) null else payload
}

// The kinds of app the Store takes: its code in the package ("code": one HTML file), or published through Aiwa ("aiwa").
private val KINDS = setOf("code", "aiwa")

/**
 * The address that makes the Store open its publish sheet with the app's name and code in it — and nothing else: reading
 * it, trying it and pressing Publish, which signs with the user's identity, stay theirs:
 *
 *     https://appassets.androidplatform.net/assets/web/index.html#publish=<kind>;<name>;<code>
 *
 * The code sits in the URL FRAGMENT, which is never sent anywhere. Null when the name is not a plain file name, the kind
 * is unknown, or the packed app is too long for an address.
 */
fun storePublishUrl(name: String, code: String, kind: String = "code", base: String = STORE_APP_URL): String? {
    if (kind !in KINDS || !FRAGMENT_NAME.matches(name)) return null
    val payload = pack(code) ?: return null
    return "$base#publish=$kind;$name;$payload"
}
