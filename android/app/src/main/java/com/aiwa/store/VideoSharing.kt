package com.aiwa.store

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.aiwa.bridge.looksLikeVideo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Hands a video to the sharing sheet of the phone (YouTube, Instagram, anything that takes a video) instead of publishing it: Aiwa has no account and no key
 * of any network. The file is fetched into the app's cache first (the sharing sheet needs the file itself, not its address), the title and description are
 * copied so that the person pastes them where the video is posted, and the person publishes from their own application.
 */
object VideoSharing {
    fun share(context: Context, url: String, title: String, description: String) {
        val app = context.applicationContext
        if (!url.startsWith("https://")) { toastOnMain(app, "Cette vidéo n'a pas d'adresse sûre."); return }
        CoroutineScope(Dispatchers.Main).launch {
            try {
                copyToClipboard(app, "$title\n\n$description")
                toastOnMain(app, "Téléchargement de la vidéo… Le titre et la description sont copiés : colle-les dans l'application.")
                val file = withContext(Dispatchers.IO) {
                    val dir = File(app.cacheDir, "share").apply { deleteRecursively(); mkdirs() }
                    val target = File(dir, "video.mp4")
                    val connection = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 60_000; instanceFollowRedirects = true }
                    connection.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
                    val head = target.inputStream().use { s -> ByteArray(12).also { s.read(it) } }
                    check(looksLikeVideo(head, target.length())) { "le fichier téléchargé n'est pas une vidéo" }
                    target
                }
                val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", file)
                val send = Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, title)
                    .putExtra(Intent.EXTRA_TEXT, description).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                app.startActivity(Intent.createChooser(send, "Partager la vidéo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                toastOnMain(app, "Partage impossible : ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }
}
