package com.aiwa.store

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.statusBarsPadding
import com.aiwa.bridge.LocalClaudeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * A profile (the file `Profile/index.html` of a repository, read from GitHub as it is) in full screen, and the others of the same list one swipe away (the list comes from « Voir »: the people near, the favourites, a link). The page
 * shows ITS design and everything it says (its files, how to reach her); it runs no script of its own, and wherever it has a slot the application puts the buttons of the VISITOR (HomeBridge.kt). What a page links (a mail, a number, a site) opens in the app that handles it. The widget of the home screen cannot be put inside an app: a slim bar at the bottom takes its place (previous, next, close).
 */
class ProfileActivity : ComponentActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val urls = try { JSONArray(intent.getStringExtra(EXTRA_URLS) ?: "[]").let { a -> (0 until a.length()).map { a.getString(it) } } } catch (err: Exception) { emptyList() }
            .filter { ProfileView.valid(it) }.take(60)
        if (urls.isEmpty()) { finish(); return }
        val start = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, urls.size - 1)
        val pubs = try { JSONArray(intent.getStringExtra(EXTRA_PUBS) ?: "[]").let { a -> (0 until urls.size).map { a.optString(it) } } } catch (err: Exception) { urls.map { "" } }
        setContent { ProfileScreen(urls, pubs, start, onClose = { finish() }) }
    }

    companion object {
        const val EXTRA_URLS = "urls"
        const val EXTRA_INDEX = "index"
        const val EXTRA_PUBS = "pubs"
        fun open(context: android.content.Context, repos: List<String>, index: Int = 0, pubs: List<String> = emptyList()) =
            context.startActivity(Intent(context, ProfileActivity::class.java).putExtra(EXTRA_URLS, JSONArray(repos).toString()).putExtra(EXTRA_PUBS, JSONArray(pubs).toString()).putExtra(EXTRA_INDEX, index).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun ProfileScreen(urls: List<String>, pubs: List<String>, start: Int, onClose: () -> Unit) {
    val pager = rememberPagerState(initialPage = start) { urls.size }
    val scope = rememberCoroutineScope()
    // The score of the profile on the screen for the person who looks, made by her own super agent from the public page (the brain keeps it): « Pro 72 · Perso 40 ». Nothing is sent to the other person.
    val shown = urls[pager.currentPage]
    var score by remember(shown) { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(shown) {
        val answer = withContext(Dispatchers.IO) { try { LocalClaudeBridge().chamberCall("/api/match", JSONObject().put("repo", shown)) } catch (err: Exception) { null } }
        if (answer != null && answer.optBoolean("ok", false)) score = answer.optInt("pro") to answer.optInt("perso")
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF10132E))) {
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            AndroidView(factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(0xFF10132E.toInt())
                    settings.javaScriptEnabled = true           // only for the runtime of the application (evaluateJavascript, not bound by the page's rules): the page's own scripts are forbidden by a policy put first in it (HomeRuntime.lockScripts)
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mediaPlaybackRequiresUserGesture = false      // the video behind a profile starts by itself (muted)
                    HomeRuntime.attach(this, false) { urls[page] }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = HomeRuntime.openExternal(context, request)
                        override fun onPageFinished(view: WebView, url: String?) { HomeRuntime.inject(view) }
                    }
                    ProfileView.load(this, urls[page])
                }
            }, modifier = Modifier.fillMaxSize(), onRelease = { HomeRuntime.release(it); it.destroy() })
        }
        score?.let { (pro, perso) ->
            Text("Pro $pro · Perso $perso", color = Color.White, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp).background(Color(0x99000000), androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 6.dp))
        }
        // the slim bar at the bottom: previous, where we are, next, close (in the cut of the widget's cells)
        Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp).fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            @Composable fun key(label: String, weight: Float, onClick: () -> Unit) {
                Box(Modifier.weight(weight).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x1AFFFFFF))), SlantShape(10.dp, 8.dp))
                    .border(1.dp, GlassEdge, SlantShape(10.dp, 8.dp)).clickable { onClick() }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) { Text(label, color = Color.White, fontSize = 15.sp) }
            }
            key("‹", 1f) { if (pager.currentPage > 0) scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }
            key("${pager.currentPage + 1} / ${urls.size}", 2f) { }
            key("›", 1f) { if (pager.currentPage < urls.size - 1) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }
            val pub = pubs.getOrNull(pager.currentPage).orEmpty()
            val context = androidx.compose.ui.platform.LocalContext.current
            if (pub.isNotEmpty() && CoffeeSettings.on(context)) key("☕", 1f) {
                // the wallet page of the Store, with the card « Offrir un café » (two taps to send): the profile says nothing, the key comes from the signed card the person gave out in « Voir »
                context.startActivity(storePageIntent(context, "wallet", urls[pager.currentPage]).also { it.putExtra(StoreActivity.EXTRA_URL, com.aiwa.bridge.STORE_APP_URL + "?page=wallet&coffee=" + pub + "&repo=" + android.net.Uri.encode(urls[pager.currentPage])) })
            }
            key("✕", 1f) { onClose() }
        }
    }
}
