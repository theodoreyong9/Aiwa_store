package com.aiwa.store
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.aiwa.bridge.TERMUX_DOWNLOAD_URL

/**
 * What a tap on the APK button does when the app declared it needs Termux (`aiwa-android.json` in its repository, written by Claude):
 * the line to paste there is copied and Termux is opened (or, if it is not on the phone, the page it is downloaded from). The line is the
 * one that does everything, the APK's download included, as this project's own does: so no browser download is opened here. Nothing is run:
 * the person pastes the line and validates it. No UI of its own: a toast says what to do.
 */
class ApkDownloadActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = applicationContext
        val line = AiwaRepository.state.value.siteTermux
        if (line == null) {
            // Nothing declared any more: what the button always did.
            val address = AiwaRepository.state.value.siteUrl
            if (address == null || !openUrl(app, address)) toastOnMain(app, "Impossible d'ouvrir le téléchargement.")
            finish()
            return
        }
        copyToClipboard(app, line)
        val shown = "« ${line.take(70)}${if (line.length > 70) "…" else ""} »"
        if (!isTermuxInstalled(app)) {
            toastOnMain(app, "Termux n'est pas installé : installe-le depuis F-Droid, puis colle la ligne (déjà copiée) dedans : $shown")
            if (!openUrl(app, TERMUX_DOWNLOAD_URL)) toastOnMain(app, "Impossible d'ouvrir le navigateur : va sur $TERMUX_DOWNLOAD_URL")
        } else {
            toastOnMain(app, "Ligne copiée : colle-la dans Termux et valide, elle installe tout, l'APK compris. $shown")
            packageManager.getLaunchIntentForPackage("com.termux")?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        finish()
    }
}
