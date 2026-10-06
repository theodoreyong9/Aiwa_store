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
        if (repo == null) {
            toastOnMain(this, "Choisis d'abord un dépôt.")
        } else {
            acknowledgeNews(this)
            if (!openUrl(this, state.ciUrl ?: "https://github.com/$repo/actions")) toastOnMain(this, "Impossible d'ouvrir le navigateur.")
        }
        finish()
    }
}
