package com.aiwa.store
import android.os.Bundle
import androidx.activity.ComponentActivity

/** The widget's « Mes documents » (Média > Documents): the folder `docs` of the repository in use, on GitHub, where the documents of a project live (the ones Claude creates too). No UI of its own. */
class DocumentsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        val repo = AiwaRepository.state.value.repo
        if (repo == null) toastOnMain(this, "Choisis d'abord un dépôt.")
        else if (!openUrl(this, "https://github.com/$repo/tree/HEAD/docs")) toastOnMain(this, "Impossible d'ouvrir le navigateur.")
        finish()
    }
}
