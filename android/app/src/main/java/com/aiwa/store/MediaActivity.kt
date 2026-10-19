package com.aiwa.store

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.aiwa.bridge.ImageItem
import com.aiwa.bridge.LocalClaudeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The widget's « Média »: two folders of the repository in use, the videos and the images. */
class MediaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            val entries = listOf(
                PickerEntry("Vidéos", false, lines = 1, button = true) { startActivity(Intent(this, VideosActivity::class.java)); finish() },
                PickerEntry("Images", false, lines = 1, button = true) { startActivity(Intent(this, ImagesActivity::class.java)); finish() },
            )
            PickerSheet(entries) { finish() }
        }
    }
}

/**
 * Deleting a file of a media folder, from a line of a list: first the line asks « Supprimer X ? » (a tap confirms, another one cancels), then the brain deletes it with the person's own `gh`
 * (a commit). Without `gh` connected, the current session is asked to do it. Nothing is deleted without the confirming tap.
 */
internal suspend fun deleteMedia(context: Context, kind: String, name: String, path: String? = null): Boolean {
    val bridge = LocalClaudeBridge()
    val result = withContext(Dispatchers.IO) { bridge.mediaDelete(kind, name, path) }
    if (result.ok) { toastOnMain(context, "« $name » supprimé."); return true }
    if (result.needsSession) {
        val folder = if (kind == "image") "docs/images" else if (path == "docs") "docs" else "docs/videos"
        val sent = sendAndTrack(context, bridge, "Supprime le fichier `$folder/$name` du dépôt en cours (un commit qui le retire) et pousse-le avec la consigne Push.", toastErrors = true)
        if (sent) toastOnMain(context, "gh n'est pas connecté ici : la session est chargée de supprimer « $name ».")
        return false
    }
    toastOnMain(context, "Suppression impossible : ${result.error}")
    return false
}

/** The pictures of the repository in use (docs/images/), the latest first; a tap opens one, ↗ shares it, 🗑 deletes it after a confirmation. */
class ImagesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        if (AiwaRepository.state.value.repo == null) { toastOnMain(this, "Choisis d'abord un dépôt."); finish(); return }
        setContent {
            val context = this
            val scope = rememberCoroutineScope()
            var images by remember { mutableStateOf<List<ImageItem>?>(null) }
            var asking by remember { mutableStateOf<String?>(null) }
            suspend fun load() { images = withContext(Dispatchers.IO) { LocalClaudeBridge().images().second } }
            LaunchedEffect(Unit) { load() }
            val list = images
            val entries = buildList {
                val sure = asking
                if (sure != null) {
                    add(PickerEntry("Supprimer « $sure » du dépôt ?", false, header = true, lines = 2) { })
                    add(PickerEntry("Oui, supprimer", false, lines = 1, button = true) {
                        asking = null
                        scope.launch { if (deleteMedia(context, "image", sure)) load() }
                    })
                    add(PickerEntry("Non, garder", false, lines = 1, button = true) { asking = null })
                } else if (list == null) add(PickerEntry("Chargement…", false, lines = 1) { })
                else if (list.isEmpty()) add(PickerEntry("Aucune image dans ${AiwaRepository.state.value.repo} (ou dépôt privé). Demande-en une : elle ira dans docs/images/.", false, lines = 3) { })
                else list.forEach { img ->
                    val when_ = dateTimeLabel(img.ts)
                    add(PickerEntry(img.name + if (when_.isNotEmpty()) "\n$when_" else "", false, lines = 2, side = "🗑", onSide = { asking = img.name }, side2 = "↗", onSide2 = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, img.name).putExtra(Intent.EXTRA_TEXT, img.url)
                        try { startActivity(Intent.createChooser(send, "Partager l'image").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (err: Exception) { toastOnMain(context, "Partage impossible.") }
                    }) {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(img.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); finish()
                    })
                }
            }
            PickerSheet(entries) { finish() }
        }
    }
}
