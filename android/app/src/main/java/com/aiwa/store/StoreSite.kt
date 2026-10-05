package com.aiwa.store
import android.content.Context
import org.json.JSONObject

private var storeRepositoryRead = false
private var storeRepository: String? = null

/**
 * Whether `repo` is the repository the Store itself is published from (deployment.json: repository, part of the APK). Its site is
 * then the very page this app serves: opened from the widget, it opens in the app, not in a browser, which would be another copy
 * of the page with a wallet of its own.
 */
fun isStoreRepository(context: Context, repo: String?): Boolean {
    if (repo == null) return false
    if (!storeRepositoryRead) {
        storeRepository = try {
            JSONObject(context.assets.open("deployment.json").use { String(it.readBytes(), Charsets.UTF_8) }).optString("repository").ifEmpty { null }
        } catch (err: Exception) {
            null
        }
        storeRepositoryRead = true
    }
    return storeRepository?.equals(repo, ignoreCase = true) == true
}
