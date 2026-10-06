package com.aiwa.store
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import com.aiwa.bridge.Dictation
import com.aiwa.bridge.LocalClaudeBridge
import com.aiwa.bridge.VoiceCommand
import com.aiwa.bridge.VoiceModel
import com.aiwa.bridge.VoiceSession
import com.aiwa.bridge.describeCommand
import com.aiwa.bridge.parseDictation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.font.FontWeight
import kotlin.coroutines.resume
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The widget's own mic, now backed by StopPhraseListener instead of a
 * one-shot RecognizerIntent — reported live, twice, insistently: "le
 * micro enregistre et envoie lorsqu'il entend 'c'est bon vas-y'", not
 * as soon as a natural pause is detected. StopPhraseListener's own
 * HONEST LIMIT applies directly here: raw SpeechRecognizer has no
 * default visible dialog, so this Activity — previously fully invisible
 * on purpose (reported live: opening the full app before dictation
 * looked slow and "moche") — now shows a small translucent indicator
 * instead of nothing, since silently listening with no feedback at all
 * would leave the user unsure whether they're still being heard. It is
 * NOT the main app UI: same Theme.Dictate translucent/no-title-bar
 * window as before, just no longer fully blank inside it.
 */
class DictateActivity : ComponentActivity() {
    private var listener: StopPhraseListener? = null
    private var confirmListener: ConfirmListener? = null
    private var work: Job? = null
    private var repoNames: List<String> = emptyList()

    private val partial = mutableStateOf("")
    private val understood = mutableStateOf("")
    private val question = mutableStateOf<String?>(null)

    private val micPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A visible activity may always start a foreground service: using
        // the widget's mic once is enough to keep the process warm from
        // then on, even if the app itself was never opened.
        try {
            ContextCompat.startForegroundService(this, Intent(this, KeepAliveService::class.java))
        } catch (err: Exception) {
            // Not fatal: dictation itself doesn't depend on it.
        }
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) startListening() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private var lastPartial = ""

    private fun voiceModels() = MODEL_CHOICES.map { VoiceModel(it.id, it.label) }
    private fun voiceSessions() = AiwaRepository.state.value.cloudSessions.map { VoiceSession(it.id, it.title) }

    // What the words so far mean to Aiwa ("instruction Aiwa …"), said as the person speaks so that a mistake shows before it is applied.
    private fun understoodLine(text: String): String {
        val dictation = parseDictation(text, voiceModels(), voiceSessions(), repoNames)
        if (!dictation.instructions) return ""
        val said = dictation.commands.map { describeCommand(it) } + dictation.problems.map { "⚠ $it" }
        return "Instructions Aiwa : " + if (said.isEmpty()) "rien compris pour l'instant" else said.joinToString(" · ")
    }

    private fun startListening() {
        setContent { ListeningOverlay(partial.value, understood.value, question.value, onCancel = { finishListening() }, onSendNow = { finishListening(force = true) }) }
        // The repositories a spoken name can be one of: read once, in the background.
        CoroutineScope(Dispatchers.IO).launch {
            repoNames = try { LocalClaudeBridge().githubRepos().map { it.name } } catch (err: Exception) { emptyList() }
        }
        listener = StopPhraseListener(
            context = this,
            onPartial = { partial.value = it; lastPartial = it; understood.value = understoodLine(it) },
            onFinalText = { text -> handleFinal(text) },
            onGiveUp = { finish() },
        )
        listener?.start()
    }

    private fun finishListening(force: Boolean = false) {
        // "Envoyer maintenant" is the fallback for when speech
        // recognition mangles the stop phrase — reported concern was
        // premature sending, not "no way out" if the phrase just isn't
        // recognized; cancel just discards whatever was heard so far.
        listener?.cancel()
        confirmListener?.cancel()
        work?.cancel()
        if (force && lastPartial.isNotBlank()) handleFinal(lastPartial) else finish()
    }

    // What was said, once the stop phrase ended it: a message for Claude, plus the settings said after "instruction Aiwa" when there are.
    private fun handleFinal(text: String) {
        val dictation = parseDictation(text, voiceModels(), voiceSessions(), repoNames)
        if (!dictation.instructions) {
            sendAndFinish(text)
            return
        }
        work = CoroutineScope(Dispatchers.Main).launch {
            val wantsRepo = dictation.commands.any { it is VoiceCommand.Repo }
            if (wantsRepo && repoNames.isEmpty()) repoNames = withContext(Dispatchers.IO) { try { LocalClaudeBridge().githubRepos().map { it.name } } catch (err: Exception) { emptyList() } }
            // Said again with the list of repositories, if it was not there yet when the words were heard.
            runInstructions(if (wantsRepo || dictation.problems.isNotEmpty()) parseDictation(text, voiceModels(), voiceSessions(), repoNames) else dictation)
        }
    }

    // Applied at the end, in an order that makes sense (the session, then the repository it works on, then the rest), then the message.
    private suspend fun runInstructions(dictation: Dictation) {
        val app = applicationContext
        val bridge = LocalClaudeBridge()
        val done = mutableListOf<String>()
        var repoRefused = false
        val ordered = dictation.commands.sortedBy {
            when (it) {
                is VoiceCommand.NewSession, is VoiceCommand.SelectSession -> 0
                is VoiceCommand.Repo -> 1
                is VoiceCommand.Model -> 2
                is VoiceCommand.PushMain -> 3
                is VoiceCommand.Deploy -> 4
            }
        }
        for (command in ordered) {
            when (command) {
                is VoiceCommand.NewSession -> switchCloud(app, bridge, "new")
                is VoiceCommand.SelectSession -> switchCloud(app, bridge, command.id)
                is VoiceCommand.Model -> switchModel(app, bridge, command.id)
                is VoiceCommand.PushMain -> switchOptions(app, bridge, pushMain = command.direct)
                is VoiceCommand.Deploy -> switchOptions(app, bridge, deploy = command.mode)
                is VoiceCommand.Repo -> {
                    // A repository is never changed on a guess: the phone asks aloud and the answer is the same phrase.
                    if (confirmRepo(command.name)) {
                        switchRepo(app, bridge, command.name)
                    } else {
                        repoRefused = true
                        continue
                    }
                }
            }
            done.add(describeCommand(command))
        }
        if (done.isNotEmpty()) toastOnMain(app, "Aiwa : " + done.joinToString(" · "))
        if (dictation.problems.isNotEmpty()) toastOnMain(app, "⚠ " + dictation.problems.joinToString(" · "))
        if (repoRefused) toastOnMain(app, "Dépôt non confirmé : il ne change pas.")
        val message = dictation.message
        if (message.isNotBlank()) {
            if (repoRefused) {
                // The message was meant for the other repository: it does not go to this one. It is not lost.
                copyToClipboard(app, message)
                toastOnMain(app, "Le message n'est pas parti (dépôt non confirmé) : il est copié.")
            } else {
                sendAndTrack(app, bridge, message, toastErrors = true)
            }
        }
        AiwaWidget().updateAll(app)
        finish()
    }

    // The phone says the question, then listens for "c'est bon vas-y" and nothing else: silence or any other words are a no.
    private suspend fun confirmRepo(name: String): Boolean {
        val spoken = name.substringAfter('/').replace('_', ' ').replace('-', ' ')
        question.value = "Dépôt $spoken ? Dis « c'est bon vas-y » pour confirmer"
        val asked = Speaker.speakAndWait(this, "Dépôt $spoken. Tu confirmes ? Dis : c'est bon, vas-y.")
        if (!asked) {
            question.value = null
            toastOnMain(applicationContext, "Pas de voix sur ce téléphone pour poser la question : le dépôt ne change pas.")
            return false
        }
        delay(400)   // the end of the voice must not be heard as the answer
        val yes = suspendCancellableCoroutine<Boolean> { cont ->
            val confirm = ConfirmListener(this) { answer -> if (cont.isActive) cont.resume(answer) }
            confirmListener = confirm
            cont.invokeOnCancellation { confirm.cancel() }
            confirm.start()
        }
        question.value = null
        return yes
    }

    private fun sendAndFinish(text: String) {
        val appContext = applicationContext
        if (text.isNotBlank()) {
            CoroutineScope(Dispatchers.Default).launch {
                sendAndTrack(appContext, LocalClaudeBridge(), text, toastErrors = true)
                AiwaWidget().updateAll(appContext)
            }
        }
        finish()
    }

    override fun onDestroy() {
        listener?.cancel()
        confirmListener?.cancel()
        work?.cancel()
        super.onDestroy()
    }
}

@Composable
private fun ListeningOverlay(partialText: String, understood: String, question: String?, onCancel: () -> Unit, onSendNow: () -> Unit) {
    MaterialTheme {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 6.dp, modifier = Modifier.padding(24.dp)) {
                Column(Modifier.padding(16.dp)) {
                    if (question != null) {
                        Text("🎙️ " + question, fontWeight = FontWeight.Bold)
                    } else {
                        Text("🎙️ Écoute… dis « c'est bon vas-y » pour envoyer")
                        if (partialText.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(partialText)
                        }
                        if (understood.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(understood, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.Start) {
                        TextButton(onClick = onCancel) { Text("Annuler") }
                        if (question == null) TextButton(onClick = onSendNow) { Text("Envoyer maintenant") }
                    }
                }
            }
        }
    }
}
