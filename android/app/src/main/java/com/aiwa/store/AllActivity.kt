package com.aiwa.store
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import com.aiwa.bridge.AllResult
import com.aiwa.bridge.LocalClaudeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * « Tous »: one kind (Docs, Vidéo, Page, App, Aiwa) across every repository of the list. The brain reads them itself with git (the layout is the same in
 * each), so the first opening can take a moment; a repository it cannot read is counted at the end, not hidden.
 */
class AllActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        val kind = intent.getStringExtra("kind") ?: "video"
        setContent {
            var result by remember { mutableStateOf<AllResult?>(null) }
            // the brain answers at once with what it has read and goes on reading: asked again every two seconds (at most three minutes) while it is
            LaunchedEffect(kind) {
                val bridge = LocalClaudeBridge()
                var tries = 0
                // what the brain already read is shown at once; asked with refresh, it reads again in the background what is older than a minute, and the view is asked again to pick up what is new
                var first = true
                while (true) {
                    val got = withContext(Dispatchers.IO) { bridge.all(kind, refresh = first) }
                    first = false
                    result = got
                    if (!got.ok || ++tries > 90) break
                    if (got.pending == 0) { if (tries >= 3) break }
                    kotlinx.coroutines.delay(if (got.pending == 0) 4000 else 2000)
                }
            }
            val r = result
            val entries = buildList {
                when {
                    r == null -> add(PickerEntry("Lecture de tes dépôts…", false) { })
                    !r.ok -> add(PickerEntry("Lecture impossible : " + r.error, false, lines = 3) { })
                    else -> {
                        if (r.pending > 0) add(PickerEntry("Lecture en cours : ${r.repos - r.pending - r.unreadable} dépôts lus sur ${r.repos}…", false) { })
                        else if (r.items.isEmpty()) add(PickerEntry("Rien de ce genre dans tes ${r.repos} dépôts.", false) { })
                        r.items.forEach { item ->
                            add(PickerEntry(item.name + "\n" + item.repo, false) {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); finish()
                            })
                        }
                        if (r.unreadable > 0) add(PickerEntry("${r.unreadable} dépôt(s) illisible(s) sans connexion GitHub (privés ou hors ligne).", false, lines = 2) { })
                    }
                }
            }
            PickerSheet(entries) { finish() }
        }
    }
}
