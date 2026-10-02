package com.aiwa.store
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.aiwa.bridge.STORE_APP_URL
import org.json.JSONObject

/**
 * The Store: the web app (apps/web) in a WebView, served from this APK's own assets at
 * https://appassets.androidplatform.net/assets/web/ — never from a file:// address, so that the page has a real origin
 * (IndexedDB for the wallet, module scripts, fetch). The wallet, the ranked list and the sandbox that apps run in are
 * the web app's; this activity only hosts it.
 *
 * What the page may ask of the phone goes through ONE channel, a web message listener restricted to the page's own
 * origin: the sandboxed frame an app runs in has another (opaque) origin, so it can neither see this channel nor
 * use it (an object added with addJavascriptInterface would be injected in every frame, apps included).
 * The commands: "save" (a text file into Downloads), "dictation" (open the dictation module's screen, which is optional).
 *
 * Links that leave the Store open in the browser; the Store itself never navigates away.
 */
class StoreActivity : ComponentActivity() {
    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#F3F2FA"))
        val root = FrameLayout(this)
        root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
        // Edge-to-edge is the default from Android 15: keep the page out of the status and navigation bars.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        val assets = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true          // IndexedDB: the wallet's journal
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(false)
        }
        web.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assets.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == "appassets.androidplatform.net") return false
                try { startActivity(Intent(Intent.ACTION_VIEW, request.url)) } catch (err: Exception) { }
                return true
            }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(web, "AiwaHost", setOf("https://appassets.androidplatform.net")) { _, message, _, isMainFrame, _ ->
                if (isMainFrame) handle(message)
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // The page closes what is open on top (an app) first; only then does the activity.
                web.evaluateJavascript("(window.aiwaHostBack ? window.aiwaHostBack() : false)") { handled ->
                    if (handled != "true") finish()
                }
            }
        })
        web.loadUrl(intent.getStringExtra(EXTRA_URL) ?: STORE_APP_URL)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_URL)?.let { web.loadUrl(it) }
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    private fun handle(message: WebMessageCompat) {
        val json = try { JSONObject(message.data ?: return) } catch (err: Exception) { return }
        when (json.optString("cmd")) {
            "save" -> saveToDownloads(json.optString("name"), json.optString("mime", "application/json"), json.optString("text"))
            "dictation" -> startActivity(Intent(this, MainActivity::class.java))
        }
    }

    // A text file into the Downloads folder (MediaStore, no storage permission on Android 10+).
    private fun saveToDownloads(name: String, mime: String, text: String) {
        if (!Regex("[A-Za-z0-9._-]{1,100}").matches(name) || Build.VERSION.SDK_INT < 29) {
            Toast.makeText(this, "Impossible d'enregistrer ce fichier : copie-le plutôt.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri: Uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IllegalStateException("refusé")
            contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: throw IllegalStateException("refusé")
            Toast.makeText(this, "Enregistré dans Téléchargements : $name", Toast.LENGTH_LONG).show()
        } catch (err: Exception) {
            Toast.makeText(this, "Impossible d'enregistrer : ${err.message}. Copie le fichier plutôt.", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val EXTRA_URL = "url"
    }
}
