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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * A profile (the file `Profile/index.html` of a repository, read from GitHub as it is) in full screen, and the others of the same list one swipe away (the list comes from « Voir »: the people near, the favourites, a link). The pages are
 * plain html: the view runs no script, and what a page links (a mail, a number, a site) opens in the app that handles it. The widget of the home screen cannot be put inside an app: a slim bar at the bottom takes its place (previous, next, close).
 */
class ProfileActivity : ComponentActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val urls = try { JSONArray(intent.getStringExtra(EXTRA_URLS) ?: "[]").let { a -> (0 until a.length()).map { a.getString(it) } } } catch (err: Exception) { emptyList() }
            .filter { ProfileView.valid(it) }.take(60)
        if (urls.isEmpty()) { finish(); return }
        val start = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, urls.size - 1)
        setContent { ProfileScreen(urls, start, onClose = { finish() }) }
    }

    companion object {
        const val EXTRA_URLS = "urls"
        const val EXTRA_INDEX = "index"
        fun open(context: android.content.Context, repos: List<String>, index: Int = 0) =
            context.startActivity(Intent(context, ProfileActivity::class.java).putExtra(EXTRA_URLS, JSONArray(repos).toString()).putExtra(EXTRA_INDEX, index).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun ProfileScreen(urls: List<String>, start: Int, onClose: () -> Unit) {
    val pager = rememberPagerState(initialPage = start) { urls.size }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().background(Color(0xFF10132E))) {
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            AndroidView(factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(0xFF10132E.toInt())
                    settings.javaScriptEnabled = false          // the pages are plain html: nothing of a profile runs
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mediaPlaybackRequiresUserGesture = false      // the video behind a profile starts by itself (muted)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val url = request.url
                            if (url.scheme in setOf("https", "mailto", "tel", "sms")) try { context.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (err: Exception) { }
                            return true
                        }
                    }
                    ProfileView.load(this, urls[page])
                }
            }, modifier = Modifier.fillMaxSize(), onRelease = { it.destroy() })
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
            key("✕", 1f) { onClose() }
        }
    }
}
