package com.aiwa.store

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.util.Base64
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aiwa.bridge.FilmJob
import com.aiwa.bridge.FilmStatus
import com.aiwa.bridge.LocalClaudeBridge
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// « Film de moi »: a film of the person, in situations and with words that were never filmed, made through fal.ai with HER key (brain: aiwa_film.py). Settings (key, voice, cap), the form, the quote, the follow-up.

private fun dollars(value: Double) = String.format(Locale.FRANCE, "%.2f $", value)

/** A photo read from the phone, reduced (1280 px at most, JPEG) so that it travels light. */
internal fun shrinkJpeg(context: Context, uri: Uri): ByteArray? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > 2560 || bounds.outHeight / sample > 2560) sample *= 2
    val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    if (bitmap == null) null else {
        val scale = minOf(1f, 1280f / maxOf(bitmap.width, bitmap.height))
        val small = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
        ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, 88, it) }.toByteArray()
    }
} catch (err: Exception) { null }

@androidx.compose.runtime.Composable
private fun FilmSheet(onClose: () -> Unit, content: @androidx.compose.runtime.Composable () -> Unit) {
    DarkSheetTheme {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp), color = SheetBg, border = androidx.compose.foundation.BorderStroke(1.dp, SheetLine),
                modifier = Modifier.padding(16.dp).fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            ) {
                Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
            }
        }
    }
}

/** Settings → « Film de moi »: the fal key, my voice, the cap per film. */
class FilmSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            var status by remember { mutableStateOf<FilmStatus?>(null) }
            val scope = rememberCoroutineScope()
            LaunchedEffect(Unit) { status = withContext(Dispatchers.IO) { LocalClaudeBridge().filmStatus() } }
            val st = status
            val entries = buildList {
                if (st == null) { add(PickerEntry("Cerveau injoignable ou trop ancien (il faut la version 150).", false, lines = 2) { }); return@buildList }
                add(PickerEntry("Clé fal : ${if (st.key) "en place" else "absente"}", false, lines = 1, button = true) { startActivity(Intent(this@FilmSettingsActivity, FalKeyActivity::class.java)); finish() })
                add(PickerEntry("Ma voix : ${if (st.voice) "enregistrée" else "à enregistrer"}", false, lines = 1, button = true) { startActivity(Intent(this@FilmSettingsActivity, VoiceSampleActivity::class.java)); finish() })
                add(PickerEntry("Plafond par film : ${dollars(st.cap)}", false, lines = 1, button = true) {
                    val cycle = listOf(10.0, 20.0, 50.0, 100.0)
                    val next = cycle.firstOrNull { it > st.cap } ?: cycle.first()
                    scope.launch { withContext(Dispatchers.IO) { LocalClaudeBridge().filmCap(next) }; status = withContext(Dispatchers.IO) { LocalClaudeBridge().filmStatus() } }
                })
                add(PickerEntry("Un film coûte environ ${dollars(st.perMinute)} la minute. Facturé par fal.ai sur ton compte, jamais par Aiwa.", false, lines = 3) { })
            }
            PickerSheet(entries) { finish() }
        }
    }
}

/** The fal key: saved in the brain's Linux only (owner-only file), sent to fal only. */
class FalKeyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            val scope = rememberCoroutineScope()
            var key by remember { mutableStateOf("") }
            var has by remember { mutableStateOf<Boolean?>(null) }
            var note by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) { has = withContext(Dispatchers.IO) { LocalClaudeBridge().filmStatus()?.key } }
            FilmSheet({ finish() }) {
                Text("Clé fal.ai", style = MaterialTheme.typography.titleMedium)
                Text("Pour fabriquer ton film (l'image, la voix, la bouche). Tu crées un compte sur fal.ai, tu y mets des crédits, et tu colles ici ta clé. Chaque film est facturé par fal.ai, sur ton compte : Aiwa ne touche jamais à l'argent.", style = MaterialTheme.typography.bodySmall)
                Text(if (has == true) "En place." else if (has == false) "Absente." else "…", style = MaterialTheme.typography.titleSmall)
                if (has != true) {
                    OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("Ta clé fal") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                    Text("Elle reste dans le cerveau Termux de ce téléphone (fichier lisible par toi seul) et ne va qu'à fal.ai : jamais dans un dépôt, jamais à une session.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = {
                        scope.launch {
                            val error = withContext(Dispatchers.IO) { LocalClaudeBridge().filmKey(key.trim()) }
                            if (error == null) { key = ""; has = true; note = null } else note = error
                        }
                    }, enabled = key.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Enregistrer la clé") }
                } else {
                    OutlinedButton(onClick = { scope.launch { withContext(Dispatchers.IO) { LocalClaudeBridge().filmKey(null) }; has = false } }, modifier = Modifier.fillMaxWidth()) { Text("Retirer la clé") }
                }
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                OutlinedButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
            }
        }
    }
}

/** My voice: twelve seconds read from a sentence on the screen; the brain keeps them (to speak my words in my voice). Only my own voice, or that of someone who agrees. */
class VoiceSampleActivity : ComponentActivity() {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            val scope = rememberCoroutineScope()
            var sentence by remember { mutableStateOf("…") }
            var state by remember { mutableStateOf("idle") }
            var note by remember { mutableStateOf<String?>(null) }
            var agreed by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { sentence = withContext(Dispatchers.IO) { LocalClaudeBridge().filmStatus()?.sampleText } ?: "" }
            FilmSheet({ if (state != "recording") finish() }) {
                Text("Ma voix", style = MaterialTheme.typography.titleMedium)
                Text("Lis cette phrase à voix haute, d'une voix naturelle, dans le calme :", style = MaterialTheme.typography.bodySmall)
                Text(sentence, style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = agreed, onCheckedChange = { agreed = it })
                    Text("C'est ma voix (ou celle de quelqu'un qui est d'accord).", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = {
                    if (ContextCompatGranted(this@VoiceSampleActivity)) {
                        state = "recording"; note = null
                        scope.launch {
                            WakeWordService.instance?.let { s -> withContext(Dispatchers.Main) { s.pause() } }
                            val ok = recordTwelveSeconds()
                            WakeWordService.instance?.let { s -> withContext(Dispatchers.Main) { s.resume() } }
                            if (!ok) { state = "idle"; note = "L'enregistrement a échoué."; return@launch }
                            state = "sending"
                            val bytes = file?.readBytes()
                            val error = if (bytes == null) "fichier vide" else withContext(Dispatchers.IO) { LocalClaudeBridge().filmVoice(Base64.encodeToString(bytes, Base64.NO_WRAP), "m4a") }
                            file?.delete()
                            if (error == null) { state = "done" } else { state = "idle"; note = error }
                        }
                    } else permission.launch(Manifest.permission.RECORD_AUDIO)
                }, enabled = agreed && state == "idle", modifier = Modifier.fillMaxWidth()) {
                    Text(when (state) { "recording" -> "J'écoute… (12 s)"; "sending" -> "Envoi…"; "done" -> "Voix enregistrée ✓"; else -> "Enregistrer (12 s)" })
                }
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                OutlinedButton(onClick = { finish() }, enabled = state != "recording", modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
            }
        }
    }

    private fun ContextCompatGranted(context: Context) = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private suspend fun recordTwelveSeconds(): Boolean = withContext(Dispatchers.Main) {
        try {
            val out = File(cacheDir, "voice_sample.m4a").also { file = it }
            val r = if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(this@VoiceSampleActivity) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44100)
            r.setAudioEncodingBitRate(128_000)
            r.setOutputFile(out.absolutePath)
            r.prepare(); r.start(); recorder = r
            delay(12_000)
            r.stop(); r.release(); recorder = null
            true
        } catch (err: Exception) {
            try { recorder?.release() } catch (e: Exception) { }
            recorder = null
            false
        }
    }
}

private const val EXTRA_DIRECTION = "direction"
private const val EXTRA_JOB = "job"
private const val EXTRA_KIND = "kind"

/** « Film de moi »: the photos, what I want, the words I say (mine, as I write them), the format; the quote comes next. */
class FilmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        setContent {
            val scope = rememberCoroutineScope()
            var status by remember { mutableStateOf<FilmStatus?>(null) }
            var photos by remember { mutableStateOf<List<Uri>>(emptyList()) }
            var direction by remember { mutableStateOf(intent.getStringExtra(EXTRA_DIRECTION).orEmpty()) }
            var says by remember { mutableStateOf("") }
            var aspect by remember { mutableStateOf("9:16") }
            var consent by remember { mutableStateOf(false) }
            var busy by remember { mutableStateOf(false) }
            var note by remember { mutableStateOf<String?>(null) }
            val product = intent.getStringExtra(EXTRA_KIND) == "product"
            LaunchedEffect(Unit) { status = withContext(Dispatchers.IO) { LocalClaudeBridge().filmStatus() } }
            val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> if (uris.isNotEmpty()) photos = uris.take(7) }
            val st = status
            FilmSheet({ if (!busy) finish() }) {
                Text(if (product) "Film de produit" else "Film de moi", style = MaterialTheme.typography.titleMedium)
                when {
                    st == null -> Text("Le cerveau ne répond pas (ou il est trop ancien : il faut la version 150).", style = MaterialTheme.typography.bodySmall)
                    !st.key -> {
                        Text("Il manque ta clé fal.ai.", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { startActivity(Intent(this@FilmActivity, FalKeyActivity::class.java)); finish() }, modifier = Modifier.fillMaxWidth()) { Text("Mettre ma clé") }
                    }
                    else -> {
                        Text(if (product) "Des photos de ton produit (1 à 7, sous plusieurs angles, nettes), ce que tu veux montrer, et la voix off si tu en veux une. Le devis vient avant toute dépense." else "Des photos de toi (1 à 7, de face, nettes), ce que tu veux voir, et les paroles que tu veux dire. Le devis vient avant toute dépense.", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { pick.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text(if (photos.isEmpty()) (if (product) "Choisir les photos du produit" else "Choisir mes photos") else "${photos.size} photo(s) — changer") }
                        OutlinedTextField(value = direction, onValueChange = { direction = it.take(1500) }, label = { Text(if (product) "Ce que je veux montrer du produit" else "Ce que je veux dans le film") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                        OutlinedTextField(value = says, onValueChange = { says = it.take(1500) }, label = { Text(if (product) "Voix off (facultatif, telle que je l'écris)" else "Mes paroles (facultatif, telles que je les dis)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        if (!product && says.isNotBlank() && !st.voice) Text("Pour parler, enregistre d'abord ta voix (Réglages → Film de moi).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = { aspect = when (aspect) { "9:16" -> "16:9"; "16:9" -> "1:1"; else -> "9:16" } }, modifier = Modifier.fillMaxWidth()) { Text("Format : $aspect") }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = consent, onCheckedChange = { consent = it })
                            Text(if (product) "C'est mon produit, ou j'ai le droit de l'utiliser. Le film sera marqué « généré par IA » : relis chaque plan, un logo ou un texte peut être déformé." else "C'est moi sur les photos, et c'est ma voix (ou celle de quelqu'un qui est d'accord). Le film sera marqué « généré par IA ».", style = MaterialTheme.typography.bodySmall)
                        }
                        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        Button(onClick = {
                            busy = true; note = null
                            val app = applicationContext
                            scope.launch {
                                val blobs = withContext(Dispatchers.IO) { photos.mapNotNull { shrinkJpeg(app, it) }.map { Base64.encodeToString(it, Base64.NO_WRAP) } }
                                val job = withContext(Dispatchers.IO) { LocalClaudeBridge().filmPrepare(direction.trim(), says.trim(), aspect, blobs, consent, if (product) "product" else "me") }
                                busy = false
                                if (job.ok) { startActivity(Intent(this@FilmActivity, FilmQuoteActivity::class.java).putExtra(EXTRA_JOB, job.id)); finish() } else note = job.error
                            }
                        }, enabled = !busy && photos.isNotEmpty() && direction.isNotBlank() && consent, modifier = Modifier.fillMaxWidth()) {
                            Text(if (busy) "Je prépare le devis…" else "Préparer le devis")
                        }
                    }
                }
                OutlinedButton(onClick = { finish() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
            }
        }
    }

    companion object {
        /** From the voice: the screen opens with what was said as the direction. */
        fun open(context: Context, direction: String, kind: String = "me") {
            context.startActivity(Intent(context, FilmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(EXTRA_DIRECTION, direction).putExtra(EXTRA_KIND, kind))
        }
    }
}

private val OK_WORDS = setOf("ok", "okay", "okey", "daccord", "valide", "lance", "go", "oui")

private fun saidOk(heard: String): String? {
    val words = plainWords(heard)
    return if (containsConfirmation(heard) || words.any { it in OK_WORDS } || (words.contains("vas") && words.contains("y"))) "ok" else null
}

/** The quote: the scenes and the price of each, the total; read aloud at the same time. A tap on « Lancer » or the word « OK » starts the production; nothing is paid before. */
class FilmQuoteActivity : ComponentActivity() {
    private var listener: ChoiceListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        val id = intent.getStringExtra(EXTRA_JOB).orEmpty()
        setContent {
            val scope = rememberCoroutineScope()
            var job by remember { mutableStateOf<FilmJob?>(null) }
            var editing by remember { mutableStateOf<Int?>(null) }
            var editSituation by remember { mutableStateOf("") }
            var editSays by remember { mutableStateOf("") }
            var started by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { job = withContext(Dispatchers.IO) { LocalClaudeBridge().filmGet(id) } }

            fun startFilm() {
                if (started) return
                started = true
                listener?.cancel(); listener = null
                scope.launch { job = withContext(Dispatchers.IO) { LocalClaudeBridge().filmStart(id) } }
            }

            // Read aloud, then listened to for « OK »: the same quote is spoken whenever it changes (a new total)
            val spoken = job?.takeIf { it.ok && it.state == "quoted" }
            LaunchedEffect(spoken?.total, spoken?.scenes?.size) {
                val q = spoken ?: return@LaunchedEffect
                if (started) return@LaunchedEffect
                WakeWordService.instance?.let { s -> withContext(Dispatchers.Main) { s.pause() } }
                val sentence = "Ton film : ${q.scenes.size} scènes, environ ${String.format(Locale.FRANCE, "%.0f", q.total)} dollars" + (if (q.overCap) ", plus que ton plafond" else "") + ". Dis OK pour lancer, ou touche le bouton."
                Speaker.speakNatural(applicationContext, sentence)
                if (!q.overCap && !started && editing == null) {
                    val heard = suspendListen()
                    if (heard == "ok") startFilm()
                }
                WakeWordService.instance?.let { s -> withContext(Dispatchers.Main) { s.resume() } }
            }

            // Follow-up while it is made
            val running = job?.state == "running"
            LaunchedEffect(running) {
                if (!running) return@LaunchedEffect
                while (true) {
                    delay(3_000)
                    val next = withContext(Dispatchers.IO) { LocalClaudeBridge().filmGet(id) }
                    job = next
                    if (next.state == "done") { Speaker.speakNatural(applicationContext, "Ton film est prêt."); break }
                    if (next.state == "error") break
                }
            }

            FilmSheet({ if (job?.state != "running") finish() }) {
                val j = job
                Text("Devis du film", style = MaterialTheme.typography.titleMedium)
                if (j == null) Text("…", style = MaterialTheme.typography.bodySmall)
                else if (!j.ok) Text(j.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                else {
                    j.scenes.forEachIndexed { i, s ->
                        Column(Modifier.fillMaxWidth().clickable(enabled = j.state == "quoted") { editing = i; editSituation = s.situation; editSays = s.says }) {
                            Text("${i + 1}. ${s.situation}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                (if (s.says.isNotBlank()) "Je dis : « ${s.says} » · " else "") + "${s.seconds} s · " + (if (s.done) "déjà fait" else dollars(s.price)),
                                style = MaterialTheme.typography.bodySmall, color = SheetDim,
                            )
                        }
                    }
                    Text("Total : ${dollars(j.total)}  (estimation, facturée par fal.ai)", style = MaterialTheme.typography.titleSmall)
                    if (j.overCap) Text("Au-dessus de ton plafond : monte-le dans les réglages ou enlève des scènes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    when (j.state) {
                        "running" -> Text("En cours : ${j.step}…", style = MaterialTheme.typography.bodyMedium)
                        "done" -> {
                            Text("Terminé ✓ Il est en tête de la liste des vidéos.", style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = { startActivity(Intent(this@FilmQuoteActivity, VideosActivity::class.java)); finish() }, modifier = Modifier.fillMaxWidth()) { Text("Voir mes vidéos") }
                        }
                        "error" -> {
                            Text("Échec : ${j.error}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            Button(onClick = { started = false; scope.launch { job = withContext(Dispatchers.IO) { LocalClaudeBridge().filmGet(id) }.copy(state = "quoted") } }, modifier = Modifier.fillMaxWidth()) { Text("Revoir le devis") }
                        }
                        else -> {
                            editing?.let { i ->
                                OutlinedTextField(value = editSituation, onValueChange = { editSituation = it }, label = { Text("Ce qu'on voit") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                                OutlinedTextField(value = editSays, onValueChange = { editSays = it }, label = { Text("Ce que je dis") }, modifier = Modifier.fillMaxWidth())
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { scope.launch { job = withContext(Dispatchers.IO) { LocalClaudeBridge().filmEdit(id, i, editSituation, editSays) }; editing = null } }) { Text("Appliquer") }
                                    OutlinedButton(onClick = { scope.launch { job = withContext(Dispatchers.IO) { LocalClaudeBridge().filmEdit(id, i, null, null, remove = true) }; editing = null } }) { Text("Enlever") }
                                    OutlinedButton(onClick = { editing = null }) { Text("Annuler") }
                                }
                            }
                            Button(onClick = { startFilm() }, enabled = !j.overCap && editing == null, modifier = Modifier.fillMaxWidth()) { Text("Lancer (${dollars(j.total)})") }
                        }
                    }
                }
                OutlinedButton(onClick = { listener?.cancel(); finish() }, enabled = job?.state != "running", modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
            }
        }
    }

    /** One listening for « OK »: "ok" or null (silence, anything else). */
    private suspend fun suspendListen(): String? = withContext(Dispatchers.Main) {
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val l = ChoiceListener(this@FilmQuoteActivity, classify = ::saidOk, onChoice = { if (cont.isActive) cont.resumeWith(Result.success(it)) })
            listener = l
            cont.invokeOnCancellation { l.cancel() }
            l.start()
        }
    }

    override fun onDestroy() {
        listener?.cancel()
        super.onDestroy()
    }
}
