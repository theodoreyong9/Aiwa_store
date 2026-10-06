package com.aiwa.store
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * The widget's site button when the site does not answer (a 404: GitHub Pages is not turned on for this repository, or has not
 * published yet): a no-UI trampoline that opens the repository's Pages settings on GitHub, with a line saying what to choose there.
 * Enabling Pages needs the repository's administrator, which Aiwa and Claude's session are not.
 */
class OpenPagesSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        val repo = AiwaRepository.state.value.repo
        if (repo == null) {
            toastOnMain(this, "Choisis d'abord un dépôt.")
        } else {
            toastOnMain(this, "Pas de site pour l'instant : dans Source, choisis « GitHub Actions » (Deploy from Actions).")
            if (!openUrl(this, "https://github.com/$repo/settings/pages")) toastOnMain(this, "Impossible d'ouvrir le navigateur.")
        }
        finish()
    }
}
