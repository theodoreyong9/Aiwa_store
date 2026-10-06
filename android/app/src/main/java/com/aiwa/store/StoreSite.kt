package com.aiwa.store
import android.content.Context
import com.aiwa.bridge.HttpSiteFetcher
import com.aiwa.bridge.SiteStore
import com.aiwa.bridge.SiteUpdate
import com.aiwa.bridge.SiteUpdater
import com.aiwa.bridge.parseRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

private var storeRepositoryRead = false
private var storeRepository: String? = null

/**
 * Whether `repo` is the repository the Store itself is published from (deployment.json: repository, part of the APK). Its site is
 * then the very page this app serves: opened from the widget, it opens in the app, not in a browser, which would be another copy
 * of the page with a wallet of its own.
 */
fun isStoreRepository(context: Context, repo: String?): Boolean {
    if (repo == null) return false
    return registryRepository(context)?.equals(repo, ignoreCase = true) == true
}

/** The repository the Store and its registry live in (deployment.json: repository, part of the APK): where publications are checked. */
fun registryRepository(context: Context): String? {
    if (!storeRepositoryRead) {
        storeRepository = try {
            JSONObject(context.assets.open("deployment.json").use { String(it.readBytes(), Charsets.UTF_8) }).optString("repository").ifEmpty { null }
        } catch (err: Exception) {
            null
        }
        storeRepositoryRead = true
    }
    return storeRepository
}

private fun bundledRelease(context: Context) =
    try { context.assets.open("web/release.json").use { parseRelease(it.readBytes()) } } catch (err: Exception) { null }

/** Whether a release of the Store's page that is newer than the one in use (and than the copy inside the APK) is downloaded and waits. */
fun storeUpdateWaiting(context: Context): Boolean {
    return try {
        val store = SiteStore(File(context.filesDir, "site"))
        val pending = store.pending() ?: return false
        val newest = listOfNotNull(store.current(), bundledRelease(context)).maxOfOrNull { it.createdAt }
        newest == null || pending.createdAt > newest
    } catch (err: Exception) {
        false
    }
}

/**
 * Looks at the site for a newer release of the Store's page and keeps it, checked whole, as the pending one (the same steps the Store makes
 * when it opens). Null when there is nothing new, no network, or a release that does not check out.
 */
suspend fun downloadSiteRelease(context: Context): SiteUpdate? = withContext(Dispatchers.IO) {
    try {
        val url = JSONObject(context.assets.open("deployment.json").use { String(it.readBytes(), Charsets.UTF_8) }).optString("siteUrl")
        if (!url.startsWith("https://") || !url.endsWith("/")) return@withContext null
        val store = SiteStore(File(context.filesDir, "site"))
        val have = listOfNotNull(store.latest(), bundledRelease(context)).maxByOrNull { it.createdAt }
        SiteUpdater(url, HttpSiteFetcher(), store).update(have)
    } catch (err: Exception) {
        null
    }
}
