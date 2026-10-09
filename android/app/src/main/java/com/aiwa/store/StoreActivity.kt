package com.aiwa.store
import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.glance.appwidget.updateAll
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.aiwa.bridge.GitHubDeviceFlow
import com.aiwa.bridge.HttpSiteFetcher
import com.aiwa.bridge.STORE_APP_URL
import com.aiwa.bridge.SiteRelease
import com.aiwa.bridge.SiteStore
import com.aiwa.bridge.SiteUpdate
import com.aiwa.bridge.SiteUpdater
import com.aiwa.bridge.choose
import com.aiwa.bridge.parseRelease
import com.aiwa.bridge.servedAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

/**
 * The Store: the web app (apps/web) in a WebView, served by this app itself at https://appassets.androidplatform.net/ — never
 * from a file:// address, so that the page has a real origin (IndexedDB for the wallet, module scripts, fetch). The wallet, the
 * ranked list and the sandbox that apps run in are the web app's; this activity only hosts it.
 *
 * The page comes from one of two places, under the same origin (so the wallet's storage and the channel below are the same):
 *   /assets/web/  the copy inside the APK: the first run, and always there offline;
 *   /site/        the copy this phone downloaded from the site (deployment.json: siteUrl) and checked (SiteRelease.kt): every
 *                 file must be the one the site's release.json lists, so that the page is one whole release. It is used from the
 *                 next start after the download (at once, when it comes in during the first seconds of a start), and only if it
 *                 is newer than the copy inside the APK.
 * Updating the Store needs no new APK. What the site publishes is what the phones run: the site's owner is the GitHub account that
 * publishes it (the page is read over HTTPS, and there is no signature of its own).
 *
 * What the page may ask of the phone goes through ONE channel, a web message listener restricted to the page's own
 * origin: the sandboxed frame an app runs in has another (opaque) origin, so it can neither see this channel nor
 * use it (an object added with addJavascriptInterface would be injected in every frame, apps included).
 * The commands, each with the id the page gave it (the answer carries the same id):
 *   "secret-get" / "secret-set" / "secret-delete"  the wallet's 12 words and the GitHub token, in the Keystore (SecretStore)
 *   "github-login"  GitHub's device flow: the code to type is sent as progress, the token as the result
 *   "save"          a text file into Downloads
 *   "dictation"     open the dictation module's screen, which is optional
 * The camera is not a command: the page reads a QR code with getUserMedia (an app's pairing, a payment received), which the
 * WebView asks of this activity as a permission request. It is granted to the page's own origin only, for video only, and only
 * once Android's own camera permission has been given.
 *
 * Links that leave the Store open in the browser; the Store itself never navigates away.
 */
class StoreActivity : ComponentActivity() {
    private lateinit var web: WebView
    private val secrets by lazy { SecretStore(this) }
    private val siteStore by lazy { SiteStore(File(filesDir, "site")) }
    private var bundled: SiteRelease? = null          // the copy inside the APK, as its release.json describes it
    private var pageIsDownloaded = false              // the page being served is the downloaded copy
    private var lastUpdateCheck = 0L
    private var loadedAddress: String = STORE_APP_URL        // what the page was last asked to open (the hand-off lives in it)
    private var loadedAt = 0L                                // when, in SystemClock.elapsedRealtime()

    // The site this app follows: deployment.json's siteUrl, which is part of the APK.
    private val siteUrl: String? by lazy {
        try {
            val url = JSONObject(assets.open("deployment.json").use { String(it.readBytes(), Charsets.UTF_8) }).optString("siteUrl")
            if (url.startsWith("https://") && url.endsWith("/")) url else null
        } catch (err: Exception) { null }
    }

    // The page asked for the camera before the phone had given it: the request waits here for the answer.
    private var cameraRequest: PermissionRequest? = null
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val request = cameraRequest ?: return@registerForActivityResult
        cameraRequest = null
        if (granted) request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) else request.deny()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Not for this account (AccessGate): the window that says why, and how to sign in, instead of the Store.
        if (accessNotice(AiwaRepository.state.value, 2) != null) {
            startActivity(Intent(this, AccessActivity::class.java))
            finish()
            return
        }
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

        bundled = try { assets.open("web/release.json").use { parseRelease(it.readBytes()) } } catch (err: Exception) { null }
        pageIsDownloaded = try { siteStore.choose(bundled).cached } catch (err: Exception) { false }
        AiwaRepository.update { it.copy(storeUpdateReady = false) }       // a waiting release has just been put in place
        val loaderBuilder = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
        try {
            loaderBuilder.addPathHandler("/site/", WebViewAssetLoader.InternalStoragePathHandler(this, siteStore.currentDir))
        } catch (err: IllegalArgumentException) {
            pageIsDownloaded = false
        }
        val loader = loaderBuilder.build()
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true          // IndexedDB: the wallet's journal
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(false)
            mediaPlaybackRequiresUserGesture = false      // the camera's preview starts by itself
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                // Only the Store's own page, only the camera. An app in its frame has another origin and gets nothing.
                val own = request.origin.toString().startsWith("https://appassets.androidplatform.net")
                val onlyCamera = request.resources.isNotEmpty() && request.resources.all { it == PermissionRequest.RESOURCE_VIDEO_CAPTURE }
                if (!own || !onlyCamera) { request.deny(); return }
                runOnUiThread {
                    if (ContextCompat.checkSelfPermission(this@StoreActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                    } else {
                        cameraRequest?.deny()
                        cameraRequest = request
                        cameraPermission.launch(Manifest.permission.CAMERA)
                    }
                }
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest) {
                if (cameraRequest == request) cameraRequest = null
            }
        }
        web.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // A frame inside the page (an app in its sandbox) navigates as a browser frame does: it never makes the
                // phone open anything. Only a link of the Store itself (the main frame) leaves for the browser, and only over https.
                if (!request.isForMainFrame || request.url.host == "appassets.androidplatform.net") return false
                if (request.url.scheme == "https") {
                    try { startActivity(Intent(Intent.ACTION_VIEW, request.url)) } catch (err: Exception) { }
                }
                return true
            }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(web, "AiwaHost", setOf("https://appassets.androidplatform.net")) { _, message, _, isMainFrame, replyProxy ->
                if (isMainFrame) handle(message, replyProxy)
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
        openPage(intent.getStringExtra(EXTRA_URL) ?: STORE_APP_URL)
    }

    // Opens the page from the copy in use, and remembers the address and the moment (see applyUpdateNow).
    private fun openPage(address: String) {
        loadedAddress = address
        loadedAt = SystemClock.elapsedRealtime()
        web.loadUrl(servedAddress(address, pageIsDownloaded))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_URL)?.let { openPage(it) }
        // Asked for by the widget's update button: the downloaded release becomes the page now.
        if (intent.getBooleanExtra(EXTRA_APPLY_UPDATE, false) && !applyUpdateNow()) openPage(loadedAddress)
    }

    override fun onStart() {
        super.onStart()
        // Back in front after a release came in while the app was away or open: this is the natural moment to put it in place (the widget has no
        // button for it any more). The page is reloaded, as at a start.
        if (AiwaRepository.state.value.storeUpdateReady && SystemClock.elapsedRealtime() - loadedAt >= FRESH_START_MS) applyUpdateNow()
        checkForUpdate()
    }

    // At most every two minutes, in the background. The release is downloaded and checked whole; it replaces the page at the next start
    // (or when the app comes back in front), never while this one is being used (a page that loads a script later must find the files of its own release).
    private fun checkForUpdate() {
        val url = siteUrl ?: return
        val now = SystemClock.elapsedRealtime()
        if (lastUpdateCheck != 0L && now - lastUpdateCheck < 2 * 60 * 1000L) return
        lastUpdateCheck = now
        val have = listOfNotNull(siteStore.latest(), bundled).maxByOrNull { it.createdAt }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = SiteUpdater(url, HttpSiteFetcher(), siteStore).update(have)
                if (result is SiteUpdate.Updated) {
                    runOnUiThread {
                        // Still in the first seconds of this start: nothing has been done on the page yet, so the new release becomes
                        // the page now, and the person never sees the old one. Later, it waits for the next start.
                        if (SystemClock.elapsedRealtime() - loadedAt < FRESH_START_MS && applyUpdateNow()) return@runOnUiThread
                        // Not a toast that is gone before it is read: a button of the widget, next to Claude's.
                        AiwaRepository.update { it.copy(storeUpdateReady = true) }
                        CoroutineScope(Dispatchers.Default).launch { AiwaWidget().updateAll(applicationContext) }
                    }
                }
            } catch (err: Exception) {
                // No network, no release yet, or a release that does not check out: the page in use stays.
                Log.w("AiwaSite", "no update: ${err.message}")
            }
        }
    }

    // The downloaded release that has just come in replaces the page being shown, by the same steps as a start (SiteStore.choose).
    private fun applyUpdateNow(): Boolean = try {
        val choice = siteStore.choose(bundled)
        if (choice.cached) {
            pageIsDownloaded = true
            AiwaRepository.update { it.copy(storeUpdateReady = false) }
            openPage(loadedAddress)
        }
        choice.cached
    } catch (err: Exception) {
        false
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    private fun handle(message: WebMessageCompat, reply: JavaScriptReplyProxy) {
        val json = try { JSONObject(message.data ?: return) } catch (err: Exception) { return }
        val id = json.optInt("id", 0)
        when (json.optString("cmd")) {
            "save" -> saveToDownloads(json.optString("name"), json.optString("mime", "application/json"), json.optString("text"))
            "dictation" -> startActivity(Intent(this, MainActivity::class.java))
            "secret-get" -> respond(reply, id) { JSONObject().put("value", secrets.get(secretName(json)) ?: JSONObject.NULL) }
            "secret-set" -> respond(reply, id) { secrets.set(secretName(json), json.getString("value")); JSONObject() }
            "secret-delete" -> respond(reply, id) { secrets.delete(secretName(json)); JSONObject() }
            "github-login" -> githubLogin(reply, id, json.optString("clientId"), json.optString("scope"))
        }
    }

    private fun secretName(json: JSONObject): String {
        val name = json.optString("key")
        require(Regex("[a-z0-9-]{1,40}").matches(name)) { "not a name this app keeps secrets under" }
        return name
    }

    // What goes back to the page, with the id it asked with: { id, result }, { id, error } or { id, progress }.
    private fun post(reply: JavaScriptReplyProxy, id: Int, field: String, value: Any) {
        val payload = JSONObject().put("id", id).put(field, value).toString()
        runOnUiThread { reply.postMessage(payload) }
    }

    // A failure goes back as an error, never silently.
    private fun respond(reply: JavaScriptReplyProxy, id: Int, work: () -> JSONObject) {
        try { post(reply, id, "result", work()) } catch (err: Exception) { post(reply, id, "error", err.message ?: "failed") }
    }

    // GitHub's device flow, here because GitHub's login endpoints send no CORS headers: a web page cannot call them. The page
    // is told the code to show (it is also copied, and github.com opened); the person types it once; the page then gets the token.
    private fun githubLogin(reply: JavaScriptReplyProxy, id: Int, clientId: String, scope: String) {
        if (!Regex("[A-Za-z0-9]{10,40}").matches(clientId) || scope != "public_repo") {
            post(reply, id, "error", "not a GitHub application of this deployment")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val flow = GitHubDeviceFlow()
                val code = flow.start(clientId, scope)
                runOnUiThread {
                    copyToClipboard(this@StoreActivity, code.userCode)
                    if (code.verificationUri.startsWith("https://github.com/")) {
                        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(code.verificationUri))) } catch (err: Exception) { }
                    }
                }
                post(reply, id, "progress", JSONObject().put("userCode", code.userCode).put("verificationUri", code.verificationUri))
                post(reply, id, "result", JSONObject().put("token", flow.awaitToken(clientId, code)))
            } catch (err: Exception) {
                post(reply, id, "error", err.message ?: "GitHub login failed")
            }
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
        const val EXTRA_APPLY_UPDATE = "apply_update"
        private const val FRESH_START_MS = 10_000L      // a release that arrives this soon after the page opened replaces it at once
    }
}
