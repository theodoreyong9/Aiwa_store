package com.aiwa.store
import android.os.Bundle
import androidx.activity.ComponentActivity

/** The widget's Actions button: the repository's latest run (or its Actions page) in the browser, and the news is seen. */
class OpenActionsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        val state = AiwaRepository.state.value
        val repo = state.repo
        val registry = if (state.deploy == "aiwa") registryRepository(this) else null
        if (state.ciHintTitle != null) {
            startActivity(android.content.Intent(this, PagesHelpActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        if (registry != null) {
            // An Aiwa contract is not built in the person's repository: what checks it is the registry's workflow, on the pull request the Store opened.
            toastOnMain(this, "Le workflow du registre vérifie ta publication : cherche ta pull request dans la liste.")
            if (!openUrl(this, "https://github.com/$registry/actions/workflows/registry.yml")) toastOnMain(this, "Impossible d'ouvrir le navigateur.")
        } else if (repo == null) {
            toastOnMain(this, "Choisis d'abord un dépôt.")
        } else {
            acknowledgeNews(this)
            if (!openUrl(this, state.ciUrl ?: "https://github.com/$repo/actions")) toastOnMain(this, "Impossible d'ouvrir le navigateur.")
        }
        finish()
    }
}
