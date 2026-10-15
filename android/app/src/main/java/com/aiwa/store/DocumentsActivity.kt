package com.aiwa.store
import android.os.Bundle
import androidx.activity.ComponentActivity

/** The widget's « Mes documents » (Média > Documents): the folder `aiwa-documents` of the repository in use, on GitHub, where Claude puts the documents it creates. No UI of its own. */
class DocumentsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        val repo = AiwaRepository.state.value.repo
        if (repo == null) toastOnMain(this, "Choisis d'abord un dépôt.")
        else {
            // the documents Claude created (aiwa-documents), else the papers of the project (docs), else nothing yet
            val folder = when {
                Productions.has(this, repo, "documents") -> "aiwa-documents"
                Productions.has(this, repo, "papers") -> "docs"
                else -> null
            }
            if (folder == null) toastOnMain(this, "Pas encore de documents : demande-en un à Claude (mode Documents).")
            else if (!openUrl(this, "https://github.com/$repo/tree/HEAD/$folder")) toastOnMain(this, "Impossible d'ouvrir le navigateur.")
        }
        finish()
    }
}
