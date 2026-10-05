package com.aiwa.store
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * What a tap on the APK button does when the app declared it needs Termux (`aiwa-android.json` in its repository, written by Claude):
 * the line to paste there is copied, a toast says so and shows the line, and the APK's download opens. Nothing is run: the person pastes
 * the line in Termux once the app is installed. No UI of its own.
 */
class ApkDownloadActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = applicationContext
        val state = AiwaRepository.state.value
        val line = state.siteTermux
        if (line != null) {
            copyToClipboard(app, line)
            toastOnMain(app, "Cette app a besoin de Termux : la ligne est copiée (« ${line.take(70)}${if (line.length > 70) "…" else ""} »). Installe l'APK, puis colle-la dans Termux.")
        }
        val address = state.siteUrl
        if (address == null || !openUrl(app, address)) toastOnMain(app, "Impossible d'ouvrir le téléchargement.")
        finish()
    }
}
