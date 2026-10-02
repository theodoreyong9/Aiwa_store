package com.aiwa.store
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.glance.appwidget.updateAll
import com.aiwa.bridge.LocalClaudeBridge
import com.aiwa.bridge.storePublishUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The widget's ▦ button (and the notification of a new app): opens the Store on its Publish tab with the app Claude sent
 * already in the code field — and does nothing else: the user reads it, tries it and signs it themself. A no-UI
 * trampoline, like the other buttons of the widget.
 *
 * Reading the code first is the job of the "</>" button next to it (CodeViewActivity); this one goes straight to the Store.
 *
 * The code also goes to the clipboard, always: if the address is too long for the Store to be opened on it, the app is
 * one paste away.
 */
class OpenStoreActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        val app = applicationContext
        CoroutineScope(Dispatchers.Main).launch {
            val bridge = LocalClaudeBridge()
            val sent = try {
                withContext(Dispatchers.IO) { bridge.sentAppCode() }
            } catch (err: Exception) {
                val message = if (isBackendUnreachable(err)) autoStartBackendMessage(app) else err.message ?: "aucune app reçue"
                toastOnMain(app, "Pas d'app à ouvrir : $message")
                finish()
                return@launch
            }
            copyToClipboard(this@OpenStoreActivity, sent.code)
            val name = sent.name.removeSuffix(".app.html")
            val url = storePublishUrl(name, sent.code)
            startActivity(
                Intent(this@OpenStoreActivity, StoreActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(StoreActivity.EXTRA_URL, url),
            )
            toastOnMain(
                app,
                if (url == null) "App « $name » copiée (trop grosse pour l'adresse) : dans le Store, onglet Publier, colle-la."
                else "App « $name » envoyée à l'onglet Publier du Store : connecte ton wallet si besoin, essaie-la, puis prépare la soumission (code copié aussi).",
            )
            withContext(Dispatchers.IO) { try { bridge.sentAppSeen() } catch (err: Exception) { } }
            BackendSync.refresh(bridge)
            AiwaWidget().updateAll(app)
            finish()
        }
    }
}
