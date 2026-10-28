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
import android.webkit.GeolocationPermissions
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
import com.aiwa.bridge.LocalClaudeBridge
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
    // « Voir » (?page=voir) asks the page for a coarse position: only the Store's own page gets it, and only after the person agreed (Android's question comes first).
    private var geoAsk: Pair<String, GeolocationPermissions.Callback>? = null
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        geoAsk?.let { (origin, callback) -> callback.invoke(origin, granted, false) }
        geoAsk = null
    }

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
        // The application is not usable yet: a cover over the page says so, and offers the widget (the one thing that works) while it is not on the home screen.
        if (UNDER_CONSTRUCTION && !isPage(intent.getStringExtra(EXTRA_URL))) {
            cover = constructionCover()
            root.addView(cover, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
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
        assetLoader = loader
        rootFrame = root
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true          // IndexedDB: the wallet's journal
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(false)
            mediaPlaybackRequiresUserGesture = false      // the camera's preview starts by itself
            setGeolocationEnabled(true)
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

            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                if (!origin.startsWith("https://appassets.androidplatform.net")) { callback.invoke(origin, false, false); return }
                runOnUiThread {
                    if (ContextCompat.checkSelfPermission(this@StoreActivity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) callback.invoke(origin, true, false)
                    else { geoAsk?.let { (o, c) -> c.invoke(o, false, false) }; geoAsk = origin to callback; locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
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
                    if (handled == "true") return@evaluateJavascript
                    val home = cover
                    if (home != null && home.visibility != android.view.View.VISIBLE) { home.visibility = android.view.View.VISIBLE; startHomeWatch() } else finish()
                }
            }
        })
        ChamberHost.runner = { cmd -> runOnUiThread { chamberCommand(cmd) } }
        ChamberHost.drain().forEach { chamberCommand(it) }
        openPage(intent.getStringExtra(EXTRA_URL) ?: STORE_APP_URL)
    }

    /** An order for the chamber (open, visit, close, begin, end, danger, ear): it waits for the page of the connection to be loaded. */
    private fun chamberCommand(cmd: JSONObject) {
        ensureChamberWeb()
        if (chamberReady) chamberWeb?.evaluateJavascript("(window.aiwaChamber&&window.aiwaChamber.cmd($cmd))", null) else chamberQueue.add(cmd)
    }

    private fun ensureChamberWeb() {
        if (chamberWeb != null) return
        val loader = assetLoader ?: return
        val view = WebView(this)
        with(view.settings) { javaScriptEnabled = true; domStorageEnabled = true; allowFileAccess = false; allowContentAccess = false; mediaPlaybackRequiresUserGesture = false }
        view.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(v: WebView, request: WebResourceRequest): WebResourceResponse? = loader.shouldInterceptRequest(request.url)
            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean = request.url.host != "appassets.androidplatform.net"
            override fun onPageFinished(v: WebView, url: String?) {
                chamberReady = true
                chamberQueue.forEach { v.evaluateJavascript("(window.aiwaChamber&&window.aiwaChamber.cmd($it))", null) }
                chamberQueue.clear()
            }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(view, "AiwaHost", setOf("https://appassets.androidplatform.net")) { _, message, _, isMainFrame, replyProxy -> if (isMainFrame) handle(message, replyProxy) }
        }
        rootFrame?.addView(view, FrameLayout.LayoutParams(2, 2))
        chamberWeb = view
        val repo = ProfileSettings.repo(this) ?: ""
        val name = repo.substringBefore('/')
        view.loadUrl(servedAddress("$STORE_APP_URL?page=chambre&me=${Uri.encode(repo)}&name=${Uri.encode(name)}", pageIsDownloaded))
    }

    // The words of the chamber: the brain makes what our agent says; if the person listens, both sides are said aloud (the other's voice lower).
    private fun chamberTurn(json: JSONObject, reply: JavaScriptReplyProxy, id: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val heard = json.optJSONObject("incoming")?.optString("text").orEmpty()
                if (ChamberState.ear && heard.isNotBlank()) Speaker.speakAndWait(this@StoreActivity, heard, 0.8f)
                val state = AiwaRepository.state.value
                val files = org.json.JSONArray().also { list -> lightKinds(state, this@StoreActivity).filter { it.found }.forEach { list.put(it.word) } }
                val body = JSONObject(json.toString()).put("project", Projects.nameOf(this@StoreActivity, state.repo, state.cloudSessionId) ?: "").put("files", files)
                val answer = LocalClaudeBridge().chamberCall("/api/chamber/turn", body)
                val say = answer.optString("say")
                if (ChamberState.ear && answer.optBoolean("ok", false) && say.isNotBlank()) Speaker.speakAndWait(this@StoreActivity, say, 1.05f)
                post(reply, id, "result", answer)
            } catch (err: Exception) {
                post(reply, id, "result", JSONObject().put("ok", false).put("error", err.message ?: "?"))
            }
        }
    }

    // Opens the page from the copy in use, and remembers the address and the moment (see applyUpdateNow).
    private fun openPage(address: String) {
        loadedAddress = address
        loadedAt = SystemClock.elapsedRealtime()
        web.loadUrl(servedAddress(address, pageIsDownloaded))
    }

    private var assetLoader: WebViewAssetLoader? = null
    private var rootFrame: FrameLayout? = null
    private var chamberWeb: WebView? = null                 // the connection of the chamber: a page of the Store without a screen (2 pixels), made when the first order comes
    private var chamberReady = false
    private val chamberQueue = ArrayList<JSONObject>()
    private var cover: android.view.View? = null
    private var homeWeb: WebView? = null                 // the home: the page of the person (her `Profile/`), under the Store
    private var homeWatch: ProfileWatch? = null           // follows her Profile/ on GitHub: a new commit replaces the page on the screen
    private var homeRepo: String? = null
    private fun isPage(address: String?) = address != null && address.contains("page=")


    /** What the application shows for now, outside of the widget: the person's own profile, as someone who discovers her would see it (the page `Profile/` of the repository chosen in the settings): her home. */
    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private fun constructionCover(): android.view.View {
        val frame = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#10132E")); isClickable = true }
        val view = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#10132E"))
            settings.javaScriptEnabled = true           // her OWN page may run script (WebGL, her own design); it has no file access and opens nothing but https / mail / tel links; the buttons of its slots are the application's (HomeBridge.kt)
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.allowFileAccess = false
            settings.mediaPlaybackRequiresUserGesture = false      // the video behind a profile starts by itself (muted)
            webViewClient = object : android.webkit.WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean = HomeRuntime.openExternal(this@StoreActivity, request)
                override fun onPageFinished(v: WebView, url: String?) { HomeRuntime.inject(v) }
            }
        }
        HomeRuntime.attach(view, true) { ProfileSettings.repo(this) }
        homeWeb = view
        // the page of the person fills the screen, and scrolls if she or her visitor wants it to (no button of the widget here: the Widget button is IN the page)
        frame.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        ProfileView.load(view, ProfileSettings.repo(this), own = true)
        return frame
    }

    // The wake word listens whenever Aiwa is opened and the microphone is allowed; it asks for the microphone once, at the first opening
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) WakeWordService.ensureStarted(applicationContext) }

    private fun startWakeWord() {
        if (!ListenSettings.enabled(this)) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { WakeWordService.ensureStarted(applicationContext); return }
        val prefs = getSharedPreferences("aiwa_hints", MODE_PRIVATE)
        if (!prefs.getBoolean("mic_asked", false)) { prefs.edit().putBoolean("mic_asked", true).apply(); micPermission.launch(Manifest.permission.RECORD_AUDIO) }
    }

    /** The home follows the page of the person: while it is on the screen, a new commit of her `Profile/` replaces it (a change asked to the super agent shows within seconds). */
    private fun startHomeWatch() {
        val web = homeWeb ?: return
        val repo = ProfileSettings.repo(this)
        if (cover?.visibility != android.view.View.VISIBLE || !ProfileView.valid(repo)) { homeWatch?.stop(); homeWatch = null; return }
        if (homeWatch != null && homeRepo == repo) { return }
        homeWatch?.stop()
        if (homeRepo != repo) { homeRepo = repo; ProfileView.load(web, repo, own = true) }       // another repository chosen in the settings
        homeWatch = ProfileWatch(repo!!) { sha -> runOnUiThread { homeWeb?.let { ProfileView.load(it, repo, own = true, sha = sha) } } }.also { it.start() }
    }

    override fun onPause() {
        homeWatch?.stop(); homeWatch = null
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        startHomeWatch()
        startWakeWord()
        // The widget is asked for at every opening while there is none (the system's window, one tap on « Ajouter »). Asked once the screen is really in front and has settled:
        // a request made while the window was still appearing (right after an installation) was lost without a word.
        window.decorView.postDelayed({ if (!isFinishing && !isDestroyed) WidgetPin.offerOnce(this) }, 800)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val asked = intent.getStringExtra(EXTRA_URL)
        if (asked != null) {
            if (isPage(asked)) cover?.visibility = android.view.View.GONE          // a page of the Store goes over the home; Back brings the home back
            openPage(asked)
        } else if (cover != null) { cover?.visibility = android.view.View.VISIBLE; startHomeWatch() }       // the icon of the application again: the home
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
        homeWatch?.stop()
        ChamberHost.runner = null
        chamberWeb?.let { it.destroy() }
        homeWeb?.let { HomeRuntime.release(it); it.destroy() }
        web.destroy()
        super.onDestroy()
    }

    private fun handle(message: WebMessageCompat, reply: JavaScriptReplyProxy) {
        val json = try { JSONObject(message.data ?: return) } catch (err: Exception) { return }
        val id = json.optInt("id", 0)
        when (json.optString("cmd")) {
            "save" -> saveToDownloads(json.optString("name"), json.optString("mime", "application/json"), json.optString("text"))
            "dictation" -> startActivity(Intent(this, MainActivity::class.java))
            "profile" -> {
                val list = json.optJSONArray("repos")
                val repos = if (list == null) emptyList() else (0 until list.length()).map { list.optString(it) }.filter { ProfileView.valid(it) }
                if (repos.isNotEmpty()) ProfileActivity.open(this, repos, json.optInt("index", 0))
            }
            "secret-get" -> respond(reply, id) { JSONObject().put("value", secrets.get(secretName(json)) ?: JSONObject.NULL) }
            "secret-set" -> respond(reply, id) { secrets.set(secretName(json), json.getString("value")); JSONObject() }
            "secret-delete" -> respond(reply, id) { secrets.delete(secretName(json)); JSONObject() }
            "chamber-status" -> json.optJSONObject("status")?.let { ChamberState.update(this, it) }
            "chamber-active", "chamber-deliver" -> CoroutineScope(Dispatchers.IO).launch {
                val path = if (json.optString("cmd") == "chamber-active") "/api/chamber/active" else "/api/chamber/deliver"
                post(reply, id, "result", LocalClaudeBridge().chamberCall(path, JSONObject(json.toString())))
            }
            "chamber-turn" -> chamberTurn(json, reply, id)
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
        /** The application itself is not usable yet: a cover shows the own profile over the page. */
        const val UNDER_CONSTRUCTION = true
        const val EXTRA_APPLY_UPDATE = "apply_update"
        private const val FRESH_START_MS = 10_000L      // a release that arrives this soon after the page opened replaces it at once
    }
}
