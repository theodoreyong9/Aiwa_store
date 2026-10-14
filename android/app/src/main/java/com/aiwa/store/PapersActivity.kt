package com.aiwa.store
import android.os.Bundle
import androidx.activity.ComponentActivity

/** The widget's « papers » button: the index of the papers the critic of the loops re-reads at every turn (folder `aiwa-papers`), on GitHub. No UI of its own. */
class PapersActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        if (!openUrl(this, PAPERS_URL)) toastOnMain(this, "Impossible d'ouvrir le navigateur.")
        finish()
    }
}

private const val PAPERS_URL = "https://github.com/theodoreyong9/Aiwa_store/tree/main/aiwa-papers"
