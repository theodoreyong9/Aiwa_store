package com.aiwa.store
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * The widget's site button (the globe, or the download for an app): a no-UI trampoline, because what it opens depends on the state at the
 * moment of the tap, and a tap is also looking at the news the red dot was about.
 *  - a failed deployment that says which setting of GitHub to change by hand: the window that explains it (PagesHelpActivity);
 *  - an app that declared it needs Termux: the line to paste there is copied as the download starts;
 *  - the site does not answer (a 404: GitHub Pages is not turned on, or has not published yet): the repository's Pages settings;
 *  - the Store's own site: the page this app serves, in the app;
 *  - otherwise the address, in the browser.
 */
class SiteButtonActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        val state = AiwaRepository.state.value
        val repo = state.repo
        val site = state.siteUrl
        acknowledgeNews(this)
        val next = when {
            state.ciHintTitle != null -> PagesHelpActivity::class.java
            state.siteKind == "apk" && state.siteTermux != null -> ApkDownloadActivity::class.java
            state.siteKind == "site" && repo != null && isStoreRepository(this, repo) -> StoreActivity::class.java
            state.siteKind == "site" && state.siteState != "live" && repo != null -> OpenPagesSettingsActivity::class.java
            else -> null
        }
        if (next != null) {
            startActivity(android.content.Intent(this, next).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        } else if (site == null || !openUrl(this, site)) {
            toastOnMain(this, "Rien à ouvrir pour l'instant.")
        }
        finish()
    }
}
