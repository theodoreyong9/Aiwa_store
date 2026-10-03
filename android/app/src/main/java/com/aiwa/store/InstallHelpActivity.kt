package com.aiwa.store
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.aiwa.bridge.BOOTSTRAP_COMMAND
import com.aiwa.bridge.TERMUX_DOWNLOAD_URL

/**
 * What a tap on "Installation manquante" does. The backend lives in Termux, and one app cannot install another or write into it,
 * so the person pastes one line, once. This copies it, then opens Termux (where it is pasted) or, when Termux is not there, the
 * page it is downloaded from. No UI of its own: a toast says what to do.
 */
class InstallHelpActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = applicationContext
        if (!isTermuxInstalled(app)) {
            copyToClipboard(app, BOOTSTRAP_COMMAND)
            toastOnMain(app, "Termux n'est pas installé : installe-le depuis F-Droid, puis colle la ligne (déjà copiée) dedans.")
            if (!openUrl(app, TERMUX_DOWNLOAD_URL)) toastOnMain(app, "Impossible d'ouvrir le navigateur : va sur $TERMUX_DOWNLOAD_URL")
        } else {
            copyToClipboard(app, BOOTSTRAP_COMMAND)
            toastOnMain(app, "Ligne d'installation copiée : colle-la dans Termux, puis valide.")
            packageManager.getLaunchIntentForPackage("com.termux")?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        finish()
    }
}
