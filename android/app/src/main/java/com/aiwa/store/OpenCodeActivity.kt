package com.aiwa.store
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.aiwa.bridge.storeCodeUrl

/**
 * The widget's </> button: no UI of its own. It opens the Store on the PUBLISHED code of the app Claude last sent: the version the registry
 * lists, checked again by the Store before it is shown (its author's signature, and for an Aiwa contract every file against the signed manifest).
 * The id is the name of the file Claude wrote (`name.aiwa.html`), which is the id the publish sheet proposes. Before the app is published, the
 * Store says so, and its draft is read in the publish sheet (the Store button).
 */
class OpenCodeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        val name = AiwaRepository.state.value.sentApp?.name?.removeSuffix(".app.html")?.removeSuffix(".aiwa.html")
        val url = name?.let { storeCodeUrl(it) }
        if (url == null) {
            toastOnMain(this, "Pas d'app à relire pour l'instant : demande-en une à Claude.")
        } else {
            startActivity(
                Intent(this, StoreActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(StoreActivity.EXTRA_URL, url),
            )
        }
        finish()
    }
}
