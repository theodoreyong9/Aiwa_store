package com.aiwa.store
import android.os.Bundle
import androidx.glance.appwidget.updateAll
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.aiwa.bridge.LocalClaudeBridge
import com.aiwa.bridge.VideoList
import kotlinx.coroutines.launch

/**
 * "Vidéos": the videos of the creation, the video files of `docs/videos/` of its GitHub repository (public: Aiwa asks for no token). At the top, the LAST production: a tap plays it in the app,
 * with its buttons cast, download and share; the folder of the videos is one button away. Under it, a new video: the documents of `docs/` to tick (or none), the quality, and the format; the request
 * goes to Claude in the current session (the instruction that tells it how comes with the message: the standard, the tools, the honesty rule).
 */
class VideosActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent { VideosWindow(onClose = { finish() }) }
    }
}

private fun sizeLabel(bytes: Long) = if (bytes <= 0) "" else if (bytes >= 1_000_000) "%.1f Mo".format(bytes / 1_000_000.0) else "${bytes / 1000} Ko"

/** The file goes to the Downloads folder of the phone (Android's download manager: a notification shows it). */
private fun download(context: Context, url: String, name: String) {
    try {
        val request = android.app.DownloadManager.Request(Uri.parse(url))
            .setTitle(name)
            .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, name)
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager).enqueue(request)
        toastOnMain(context, "Téléchargement de $name lancé (dossier Téléchargements).")
    } catch (err: Exception) {
        toastOnMain(context, "Téléchargement impossible : ${err.message ?: "?"}")
        openUrl(context, url)
    }
}

private fun share(context: Context, url: String, name: String) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
        .putExtra(android.content.Intent.EXTRA_SUBJECT, name).putExtra(android.content.Intent.EXTRA_TEXT, url)
    try { context.startActivity(android.content.Intent.createChooser(send, "Partager la vidéo").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (err: Exception) { toastOnMain(context, "Partage impossible.") }
}

private val QUALITY_CYCLE = listOf("simple", "moyen", "extra", "max")
private val FORMAT_CYCLE = listOf("9:16", "16:9", "1:1")

/** « 19 oct. 14:32 » from epoch seconds (empty when the date is not known). */
internal fun dateTimeLabel(seconds: Long): String =
    if (seconds <= 0) "" else java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.FRANCE).format(java.util.Date(seconds * 1000))

/**
 * The videos of the repository in use: ONE scrolling list, with date and time. Two general settings at the top — the quality and the dimension — are what a spoken request for a video
 * uses when it does not say them; nothing is asked from here.
 */
@Composable
private fun VideosWindow(onClose: () -> Unit) {
    val context = LocalContext.current
    val bridge = remember { LocalClaudeBridge() }
    val scope = rememberCoroutineScope()
    val state by AiwaRepository.state.collectAsState()
    var list by remember { mutableStateOf<VideoList?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf<com.aiwa.bridge.VideoItem?>(null) }

    LaunchedEffect(Unit) {
        try {
            list = bridge.videos()
            val repo = list?.repo
            val had = Productions.has(context, repo, "video")
            val has = list?.videos?.isNotEmpty() == true
            if (repo != null && had != has) { Productions.set(context, repo, "video", has); AiwaWidget().updateAll(context) }
        } catch (err: Exception) { error = err.message ?: "Liste indisponible." }
    }

    fun play(video: com.aiwa.bridge.VideoItem) = context.startActivity(
        android.content.Intent(context, VideoPlayerActivity::class.java)
            .putExtra(VideoPlayerActivity.EXTRA_URL, video.url).putExtra(VideoPlayerActivity.EXTRA_TITLE, video.name),
    )
    fun cast(video: com.aiwa.bridge.VideoItem) = context.startActivity(
        android.content.Intent(context, CastActivity::class.java).putExtra(CastActivity.EXTRA_URL, video.url).putExtra(CastActivity.EXTRA_TITLE, video.name),
    )

    val sure = asking
    // Nothing is deleted without the confirming tap
    val confirm = if (sure == null) null else listOf(
        PickerEntry("Supprimer « ${sure.name} » ?", false, header = true, lines = 2) { },
        PickerEntry("Oui, supprimer", false, lines = 1, button = true) {
            asking = null
            scope.launch {
                val where = if (sure.url.contains("/releases/download/")) "release" else if (!sure.url.contains("/docs/videos/")) "docs" else null
                if (deleteMedia(context, "video", sure.name, where)) { list = null; list = try { bridge.videos() } catch (err: Exception) { null } }
            }
        },
        PickerEntry("Non, garder", false, lines = 1, button = true) { asking = null },
    )
    val entries = confirm ?: buildList {
        // The two general settings: a tap goes to the next value
        add(PickerEntry("Qualité : ${state.videoQuality}", false, lines = 1, button = true) {
            val next = QUALITY_CYCLE[(QUALITY_CYCLE.indexOf(state.videoQuality) + 1).mod(QUALITY_CYCLE.size)]
            scope.launch { switchOptions(context, bridge, videoQuality = next) }
        })
        add(PickerEntry("Dimension : ${state.videoFormat}", false, lines = 1, button = true) {
            val next = FORMAT_CYCLE[(FORMAT_CYCLE.indexOf(state.videoFormat) + 1).mod(FORMAT_CYCLE.size)]
            scope.launch { switchOptions(context, bridge, videoFormat = next) }
        })
        val current = list
        when {
            error != null -> add(PickerEntry(error ?: "", false, lines = 3) { })
            current == null -> add(PickerEntry("Chargement…", false, lines = 1) { })
            current.repo == null -> add(PickerEntry("Choisis d'abord un dépôt (le bouton ⎇ du widget).", false, lines = 2) { })
            current.videos.isEmpty() -> add(PickerEntry("Aucune vidéo pour l'instant dans ${current.repo}.", false, lines = 2) { })
            else -> current.videos.forEach { v ->
                val when_ = dateTimeLabel(v.ts.toLongOrNull() ?: 0L)
                add(PickerEntry(v.name + if (when_.isNotEmpty()) "\n$when_" else "", false, lines = 2, side = "📺", onSide = { cast(v) }, side2 = "↗", onSide2 = { share(context, v.url, v.name) }, side3 = "🗑", onSide3 = { asking = v }) { play(v) })
            }
        }
    }
    PickerSheet(entries) { onClose() }
}
