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
        else if (!openUrl(this, "https://github.com/$repo/tree/HEAD/aiwa-documents")) toastOnMain(this, "Impossible d'ouvrir le navigateur.")
        finish()
    }
}
