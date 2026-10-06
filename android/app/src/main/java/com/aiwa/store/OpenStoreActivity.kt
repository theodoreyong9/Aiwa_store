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
 * The widget's ▦ button (and the notification of a new app): opens the Store on its publish sheet with the app Claude sent
 * already in it — and does nothing else: the user reads it, tries it and publishes it themself. A no-UI trampoline, like
 * the other buttons of the widget.
 *
 * The draft is read in the sheet this opens; the code of an app already published is the job of the "</>" button next to it (OpenCodeActivity).
 *
 * The code also goes to the clipboard, always. An app too big for an address is over the Store's own limit (512 KB): the
 * user is told so, and asks Claude for a smaller one.
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
            val name = sent.name.removeSuffix(".app.html").removeSuffix(".aiwa.html")
            val url = storePublishUrl(name, sent.code, sent.kind)
            startActivity(
                Intent(this@OpenStoreActivity, StoreActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(StoreActivity.EXTRA_URL, url),
            )
            toastOnMain(
                app,
                if (url == null) "App « $name » trop grosse pour être ouverte dans le Store (512 Ko au plus) : demande à Claude de la réduire."
                else "App « $name » ouverte dans le Store : essaie-la, puis publie-la (code copié aussi).",
            )
            withContext(Dispatchers.IO) { try { bridge.sentAppSeen() } catch (err: Exception) { } }
            BackendSync.refresh(bridge)
            AiwaWidget().updateAll(app)
            finish()
        }
    }
}
