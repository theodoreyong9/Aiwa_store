package com.aiwa.store
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.glance.appwidget.updateAll
import com.aiwa.bridge.LocalClaudeBridge
import com.aiwa.bridge.WakeKind
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model

/** Whether the person turned the always-on listening on (off until they do, in the app's screen). */
object ListenSettings {
    private const val PREFS = "aiwa_listen"
    fun enabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on", false)
    fun setEnabled(context: Context, on: Boolean) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", on).apply() }
}

/** The wake word the person chose (WakeWordEnrollActivity): a name for the screen, and the spellings the engine heard (com.aiwa.bridge.WakeWords). Empty = only the words of origin. */
object WakeWordSettings {
    private const val PREFS = "aiwa_listen"
    // Two words can be chosen: slot "agent" (the wake word of the dictation, « mon agent » by default) and slot "liaison" (the call of the super agent, « mon super agent » by default).
    private fun keys(slot: String) = when (slot) {
        "liaison" -> "liaison_custom" to "liaison_name"
        "agent" -> "custom" to "custom_name"
        else -> "v_$slot" to "n_$slot"      // the other recognitions (Recognitions.SLOTS)
    }
    fun variants(context: Context, slot: String = "agent"): List<String> =
        (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(keys(slot).first, "") ?: "").split('|').map { it.trim() }.filter { it.isNotEmpty() }
    fun name(context: Context, slot: String = "agent"): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(keys(slot).second, "") ?: ""
    fun save(context: Context, name: String, variants: List<String>, slot: String = "agent") {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(keys(slot).first, variants.joinToString("|")).putString(keys(slot).second, name.trim()).apply()
    }
    fun clear(context: Context, slot: String = "agent") { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(keys(slot).first).remove(keys(slot).second).apply() }

    /** The words WRITTEN for a recognition (a single word or a phrase, plain), read in text by the parser and by the agent's conversation. */
    fun written(context: Context, slot: String): List<String> =
        (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("w_$slot", "") ?: "").split('|').map { it.trim() }.filter { it.isNotEmpty() }
    fun saveWritten(context: Context, slot: String, words: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("w_$slot", words.joinToString("|") { com.aiwa.bridge.WakeWords.plain(it) }).apply()
    }
    fun clearWritten(context: Context, slot: String) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("w_$slot").apply() }

    /** What the parser reads: the written words of every recognition, set where the bridge's parser can see them. Called at start and after every change. */
    fun applyVocabulary(context: Context) {
        com.aiwa.bridge.VoiceVocabulary.written = com.aiwa.bridge.Recognitions.SLOTS.associate { it.id to written(context, it.id).toSet() }.filterValues { it.isNotEmpty() }
    }

    /** The spellings of the wake-level words chosen by voice (instruction, stop, éteins, renvoi). */
    fun customs(context: Context) = com.aiwa.bridge.WakeCustoms(
        instruction = variants(context, "instruction"), stop = variants(context, "stop"), off = variants(context, "eteins"), resend = variants(context, "resend"), where = variants(context, "where"),
    )

    /** Whether listening waits while a sound or a video plays (default: yes: a video is not paused by the microphone, and its sound is not heard as a word). */
    fun pauseOnMedia(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("pause_on_media", true)
    fun setPauseOnMedia(context: Context, on: Boolean) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("pause_on_media", on).apply() }
}

/**
 * Listens for "mon agent" and "instruction" all the time (a foreground service of the microphone type: the phone shows its green microphone dot for as
 * long as it runs), and when one is heard opens a dictation as if the mic of the widget had been touched, with the words that follow read as
 * instructions to Aiwa from the start ("mon agent, t'es sur quoi … modèle Opus, push main … c'est bon vas-y"). Where things stand is read aloud only when asked
 * ("t'es sur quoi ?", "t'en es où ?"). Only a change of repository is read back and has to be confirmed (VoiceDictation.confirmRepo). Works with the screen locked as long as
 * Android keeps the service alive.
 *
 * What it is not: it is off until the person turns it on; the speech model runs on the phone (VoskModelStore), nothing is sent anywhere;
 * and Android does not let a service of this kind start by itself after a restart of the phone: opening the app once starts it again.
 */
// Said to the agent (already plain: lowercase, no accent): « repasse en manuel », and « vas-y dis-lui » (the recognizer also writes « dit lui »).
private val RESEND_REGEX = Regex("""\brenvoi(?:e|es|ez|yer)?\b.*\binstructions?\b""")
private val WHERE_REGEX = Regex("""\b(?:tu\s+es|tes|t\s+es)\s+ou\s+la\b""")
private val REPORT_REGEX = Regex("""\brapport\b|\bfais\s+(?:le|ton)\s+point\b""")
private val OFF_REGEX = Regex("""\beteins\b""")
private val STOP_AGENT_REGEX = Regex("""(?:stop|arrete)(?:\s+(?:l\s+)?(?:super\s+)?(?:agent|claude))?""")
private val MANUAL_REGEX = Regex("""\b(?:repasse|reviens|retourne|passe)\s+(?:en|au)\s+(?:mode\s+)?manuel\b|\b(?:ferme|quitte)\s+(?:l\s+)?agent\b""")
// What the phone says (its own light voice, at once) while the agent session thinks; it stops as soon as the real answer is there.
private val FILLERS = listOf("Je regarde.", "Je réfléchis.", "Un instant.", "Toujours là, je vérifie.")

private val APPLY_REGEX = Regex("""\bfais[\s-]?le\b|\bapplique\b""")
private val SEND_REGEX = Regex("""\bdis?t?[\s-]?lui\b""")

class WakeWordService : Service() {
    companion object {
        private const val CHANNEL_ID = "aiwa_listen"
        private const val NOTIFICATION_ID = 7
        private const val ACTION_STOP = "com.aiwa.store.LISTEN_STOP"
        private const val ACTION_OPEN_AGENT = "com.aiwa.store.OPEN_AGENT"

        /** The running service, for the code of this same process that needs the microphone for itself (the widget's mic window). */
        @Volatile var instance: WakeWordService? = null

        /** From a visible screen only: Android refuses to start a microphone service from the background. */
        fun start(context: Context) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                toastOnMain(context, "Écoute permanente : la permission du micro manque.")
                return
            }
            try {
                ContextCompat.startForegroundService(context, Intent(context, WakeWordService::class.java))
            } catch (err: Exception) {
                toastOnMain(context, "Écoute permanente impossible : ${err.message}")
            }
        }

        /** From a visible screen: opens the second listening (the liaison agent); the first one is paused while it is open. Starts the service if needed. */
        fun openAgentMode(context: Context) {
            ListenSettings.setEnabled(context, true)
            val running = instance
            if (running != null) { running.openAgent(); return }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                toastOnMain(context, "Écoute permanente : la permission du micro manque.")
                return
            }
            try {
                ContextCompat.startForegroundService(context, Intent(context, WakeWordService::class.java).setAction(ACTION_OPEN_AGENT))
            } catch (err: Exception) {
                toastOnMain(context, "Écoute permanente impossible : ${err.message}")
            }
        }

        /** Closes the second listening: the first one starts again (it was never closed, only paused). Without a running service, only the agent is told. */
        fun closeAgentMode(context: Context) {
            val running = instance
            if (running != null) running.closeAgent()
            else CoroutineScope(Dispatchers.IO).launch {
                try { LocalClaudeBridge().agent("set", mapOf("enabled" to false)) } catch (err: Exception) { }
                AiwaRepository.update { it.copy(agent = it.agent?.copy(enabled = false)) }
                AiwaWidget().updateAll(context.applicationContext)
            }
        }

        fun stop(context: Context) { context.stopService(Intent(context, WakeWordService::class.java)) }

        /** The words changed: the small model listens for the new ones (the detector is made again with them). */
        fun reload(context: Context) { instance?.reloadWords() }
    }

    /** Makes the detector again with the words as they are now (only while the first listening is the one that listens). */
    fun reloadWords() {
        main.post {
            if (agentMode || paused || dictation != null || model == null || detector == null) return@post
            detector?.stop()
            detector = null
            listen()
        }
    }

    /** « éteins »: no more permanent listening, and the super agent closes with it. */
    private fun switchOff() {
        scope.launch {
            waitToken++
            agentListener?.stop(); agentListener = null
            detector?.stop(); detector = null
            agentCall("set", mapOf("enabled" to false))
            AiwaRepository.update { it.copy(agent = it.agent?.copy(enabled = false), listening = false) }
            ListenSettings.setEnabled(applicationContext, false)
            AiwaWidget().updateAll(applicationContext)
            Speaker.speakNatural(applicationContext, "J'éteins l'écoute permanente.")
            stopSelf()
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var preparing: Job? = null
    private var model: Model? = null
    private var detector: WakeWordDetector? = null
    private var dictation: VoiceDictation? = null
    private var lock: PowerManager.WakeLock? = null
    private var paused = false
    private var agentMode = false
    private var agentBusy = false
    private var waitToken = 0
    private var openAgentWhenReady = false
    private var agentListener: AgentListener? = null
    private var lastAgentSaid: String? = null
    private var agentWatch: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ListenSettings.setEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val notification = notification("Je prépare l'écoute…")
            if (Build.VERSION.SDK_INT >= 30) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) else startForeground(NOTIFICATION_ID, notification)
        } catch (err: Exception) {
            fail("Écoute permanente impossible : ${err.message}")
            return START_NOT_STICKY
        }
        instance = this
        if (intent?.action == ACTION_OPEN_AGENT) { if (model != null) openAgent() else openAgentWhenReady = true }
        AiwaRepository.update { it.copy(listening = true) }
        CoroutineScope(Dispatchers.Default).launch { AiwaWidget().updateAll(applicationContext) }
        if (preparing?.isActive != true && detector == null && dictation == null && !agentMode) preparing = scope.launch { prepare() }
        // Not sticky on purpose: restarted by the system from the background, a microphone service is refused, and it would loop.
        return START_NOT_STICKY
    }

    private suspend fun prepare() {
        if (!VoskModelStore.isReady(this)) {
            say("Je télécharge le modèle vocal (environ 41 Mo)…")
            val failure = withContext(Dispatchers.IO) { VoskModelStore.download(this@WakeWordService) { percent -> say("Je télécharge le modèle vocal : $percent %") } }
            if (failure != null) { fail("Le modèle vocal n'a pas pu être installé : $failure"); return }
        }
        val loaded = withContext(Dispatchers.IO) {
            try {
                LibVosk.setLogLevel(LogLevel.WARNINGS)
                Model(VoskModelStore.dir(this@WakeWordService).absolutePath)
            } catch (err: Throwable) {
                // an Error too: the native library of the model may not load on this phone
                null
            }
        }
        if (loaded == null) {
            VoskModelStore.dir(this).deleteRecursively()
            fail("Le modèle vocal ne se charge pas sur ce téléphone (supprimé : il sera retéléchargé au prochain essai).")
            return
        }
        model = loaded
        agentWatch?.cancel()
        agentWatch = scope.launch { AiwaRepository.state.collect { maybeSpeakAgent() } }
        if (openAgentWhenReady) { openAgentWhenReady = false; openAgent() } else listen()
    }

    // ---- The second listening: the liaison agent. "Mon super agent" (heard by the first listening) or the button of the widget opens it. The first listening is
    // only paused meanwhile, and "repasse en manuel" (or closing the agent) gives it back. Everything said goes to the agent; what it says, the phone says. ----

    fun openAgent() {
        main.post {
            if (agentMode || model == null) return@post
            if (AiwaRepository.state.value.agent.let { it?.session == null && it?.llm != "ready" }) {
                detector?.stop(); detector = null
                scope.launch {
                    Speaker.speakNatural(applicationContext, "L'agent n'existe pas encore. Crée-le avec le bouton agent du widget.")
                    if (!agentMode) listen()
                }
                return@post
            }
            detector?.stop()
            detector = null
            agentMode = true
            agentBusy = true
            holdCpu(true)
            AiwaRepository.update { it.copy(agent = it.agent?.copy(enabled = true)) }
            scope.launch {
                agentCall("set", mapOf("enabled" to true))
                // The natural voice is installed once (about 80 MB), in the backend; until it is ready the phone's own voice speaks.
                if (AiwaRepository.state.value.agent?.voice == "none") try { withContext(Dispatchers.IO) { LocalClaudeBridge().voiceSetup() } } catch (err: Exception) { }
                say("Agent de liaison : je t'écoute (dis « repasse en manuel » pour revenir)")
                Speaker.speakNatural(applicationContext, "Agent ouvert. Je t'écoute.")
                agentBusy = false
                agentListen()
                maybeSpeakAgent()
                CoroutineScope(Dispatchers.Default).launch { AiwaWidget().updateAll(applicationContext) }
            }
        }
    }

    fun closeAgent() {
        main.post {
            if (!agentMode) return@post
            agentMode = false
            waitToken++
            agentListener?.stop()
            agentListener = null
            AiwaRepository.update { it.copy(agent = it.agent?.copy(enabled = false)) }
            scope.launch {
                agentCall("set", mapOf("enabled" to false))
                CoroutineScope(Dispatchers.Default).launch { AiwaWidget().updateAll(applicationContext) }
                Speaker.speakNatural(applicationContext, "Agent éteint.")
                // the microphone of the widget IS the super agent: when it is turned off nothing keeps listening (the small model of the wake word used to start again by itself,
                // and the settings had to be opened to close it)
                if (!agentMode) {
                    ListenSettings.setEnabled(applicationContext, false)
                    stopSelf()
                }
            }
        }
    }

    private suspend fun agentCall(action: String, fields: Map<String, Any> = emptyMap()): String? =
        try { withContext(Dispatchers.IO) { LocalClaudeBridge().agent(action, fields) } } catch (err: Exception) { err.message ?: "?" }

    private fun agentListen() {
        if (!agentMode || paused || agentListener != null) return
        agentListener = AgentListener(this) { text -> main.post { heardForAgent(text) } }.also { it.start() }
    }

    private fun heardForAgent(text: String) {
        agentListener?.stop()
        agentListener = null
        if (!agentMode) return
        val plain = com.aiwa.bridge.WakeWords.plain(text)
        agentBusy = true
        waitToken++   // what was waited for is over: the person spoke
        scope.launch {
            when {
                OFF_REGEX.containsMatchIn(plain) || com.aiwa.bridge.VoiceVocabulary.phraseHeard("eteins", plain) -> { agentBusy = false; switchOff(); return@launch }
                (WHERE_REGEX.containsMatchIn(plain) || com.aiwa.bridge.VoiceVocabulary.phraseHeard("where", plain)) -> readPillsReport(applicationContext)
                (REPORT_REGEX.containsMatchIn(plain) || com.aiwa.bridge.VoiceVocabulary.phraseHeard("report", plain)) -> {
                    val error = agentCall("report")
                    say(if (error == null) "Je demande son rapport au super agent." else "Le super agent n'a pas reçu : $error")
                }
                (RESEND_REGEX.containsMatchIn(plain) || com.aiwa.bridge.VoiceVocabulary.phraseHeard("resend", plain)) -> {
                    val ok = resendInstructions(applicationContext)
                    say(if (ok) "Instructions renvoyées à la session." else "Instructions non renvoyées.")
                }
                (STOP_AGENT_REGEX.matches(plain) || com.aiwa.bridge.VoiceVocabulary.phraseHeard("stop", plain)) -> {
                    // « stop », CONFIRMED first: what waits is dropped, and the sessions the agent last spoke to are asked to forget its last comment
                    if (!askAloud(applicationContext, "Tu veux que j'arrête ?") { Speaker.speakNatural(applicationContext, it) }) {
                        say("Stop non confirmé : rien n'est arrêté.")
                    } else {
                        val told = agentCall("stop")
                        say(if (told == null) "Stop : les sessions touchées en dernier oublient son dernier commentaire." else "Stop (les sessions n'ont pas été prévenues : $told)")
                    }
                }
                MANUAL_REGEX.containsMatchIn(plain) -> { agentBusy = false; closeAgent(); return@launch }
                AiwaRepository.state.value.agent?.command != null && APPLY_REGEX.containsMatchIn(plain) -> applyAgentCommand()
                AiwaRepository.state.value.agent?.proposal != null && SEND_REGEX.containsMatchIn(plain) -> sendAgentProposal()
                else -> {
                    say("Toi → l'agent : $text")
                    val error = agentCall("ask", mapOf("text" to text))
                    if (error != null) { say("L'agent n'a pas reçu : $error"); toastOnMain(applicationContext, "L'agent n'a pas reçu : $error") }
                }
            }
            agentBusy = false
            agentListen()
            maybeSpeakAgent()
            if (agentMode && AiwaRepository.state.value.agent?.say?.id.let { it == null || it == lastAgentSaid }) startFiller()
        }
    }

    // Waiting for the agent: a short phrase now, then another every 12 s (four at most). Stops the moment an answer is there, or the person speaks again, or the agent is closed.
    private fun startFiller() {
        val token = ++waitToken
        scope.launch {
            // With the person's own key the answer comes in a second or two: no filler unless it really takes longer.
            if (AiwaRepository.state.value.agent?.llm == "ready") delay(4_000)
            for (phrase in FILLERS) {
                if (!agentMode || token != waitToken) return@launch
                if (AiwaRepository.state.value.agent?.say?.id.let { it != null && it != lastAgentSaid }) return@launch   // the answer is there
                if (!agentBusy) {
                    agentBusy = true
                    agentListener?.stop()
                    agentListener = null
                    Speaker.speakAndWait(applicationContext, phrase)
                    agentBusy = false
                    agentListen()
                    maybeSpeakAgent()
                }
                delay(12_000)
            }
        }
    }

    // « Vas-y, fais-le »: the settings the agent asked for (model, push, repository, session…) are read back and applied by the same code as the spoken instructions, after « c'est bon vas-y ».
    private suspend fun applyAgentCommand() {
        val command = AiwaRepository.state.value.agent?.command ?: return
        say(command.readBack)
        if (!askAloud(applicationContext, command.readBack) { Speaker.speakNatural(applicationContext, it) }) {
            toastOnMain(applicationContext, "Pas confirmé : rien n'est appliqué. Les réglages attendent.")
            Speaker.speakNatural(applicationContext, "Pas appliqué. Les réglages attendent.")
            return
        }
        if (command.instruction.isNotBlank()) VoiceDictation(context = this, startInInstructions = true, onFinished = {}).applyInstruction(command.instruction)
        command.cast?.let { applyCast(it) }
        command.open?.let { openProduction(applicationContext, it) }
        agentCall("cancel", mapOf("id" to command.id))   // applied: it must not be applied twice
        Speaker.speakNatural(applicationContext, "C'est fait.")
        try { withContext(Dispatchers.IO) { BackendSync.refresh(LocalClaudeBridge()) } } catch (err: Exception) { }
    }

    /** The cast asked by the super agent: the latest video goes to the TV now, or the wish waits at the brain for the video that is about to be made (it starts when the video exists). */
    private suspend fun applyCast(cast: com.aiwa.bridge.AgentCast) {
        val bridge = LocalClaudeBridge()
        if (cast.wait) {
            try { withContext(Dispatchers.IO) { bridge.castPending(cast.tv) } } catch (err: Exception) { toastOnMain(applicationContext, "Envoi à la TV non enregistré : ${err.message ?: "?"}") }
            return
        }
        val latest = try { withContext(Dispatchers.IO) { bridge.videos().videos.firstOrNull() } } catch (err: Exception) { null }
        if (latest == null) toastOnMain(applicationContext, "Aucune vidéo à envoyer à la TV.")
        else try { CastService.start(applicationContext, latest.url, cast.tv, latest.name) } catch (err: Exception) { toastOnMain(applicationContext, "Envoi à la TV impossible : ${err.message ?: "?"}") }
    }

    // « Vas-y dis-lui »: the phone reads where the proposal goes and what it says, and only « c'est bon vas-y » sends it.
    private suspend fun sendAgentProposal() {
        val proposal = AiwaRepository.state.value.agent?.proposal ?: return
        say(proposal.readBack)
        if (!askAloud(applicationContext, proposal.readBack) { Speaker.speakNatural(applicationContext, it) }) {
            toastOnMain(applicationContext, "Pas confirmé : rien n'est envoyé. La proposition attend.")
            Speaker.speakNatural(applicationContext, "Pas envoyé. La proposition attend.")
            return
        }
        val error = agentCall("confirm", mapOf("id" to proposal.id))
        toastOnMain(applicationContext, if (error == null) "Envoyé à ${proposal.sessionTitle}." else "Pas envoyé : $error")
        Speaker.speakNatural(applicationContext, if (error == null) "C'est envoyé." else "Je n'ai pas pu l'envoyer.")
        try { withContext(Dispatchers.IO) { BackendSync.refresh(LocalClaudeBridge()) } } catch (err: Exception) { }
    }

    // What the agent says is said only while its listening is open, and never over the person: the listening lets go, the phone speaks, the backend is told, the listening returns.
    private fun maybeSpeakAgent() {
        if (!agentMode || agentBusy || paused) return
        val say = AiwaRepository.state.value.agent?.say ?: return
        if (say.id == lastAgentSaid) return
        lastAgentSaid = say.id
        waitToken++   // the answer is there: no more filler
        agentBusy = true
        agentListener?.stop()
        agentListener = null
        scope.launch {
            say("L'agent : " + say.text)
            Speaker.speakNatural(applicationContext, say.text)
            agentCall("said", mapOf("id" to say.id))
            agentBusy = false
            agentListen()
            maybeSpeakAgent()
        }
    }

    private fun listen() {
        val current = model ?: return
        if (paused || dictation != null) return
        val own = WakeWordSettings.name(this)
        say("À l'écoute : dis « " + (if (own.isNotEmpty()) own else "mon agent") + " » (t'es sur quoi ?), « instruction » ou « stop Claude »")
        holdCpu(true)
        detector = WakeWordDetector(
            model = current,
            custom = WakeWordSettings.variants(this),
            liaison = WakeWordSettings.variants(this, "liaison"),
            more = WakeWordSettings.customs(this),
            onWake = { kind -> main.post { heardWakeWord(kind) } },
            onFailure = { reason -> main.post { fail("Écoute permanente arrêtée : $reason") } },
            mediaPlaying = { (getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager).isMusicActive },
            pauseOnMedia = WakeWordSettings.pauseOnMedia(this),
        ).also { it.start() }
    }

    private fun heardWakeWord(kind: WakeKind) {
        detector = null
        if (paused) return
        if (kind == WakeKind.LIAISON) { openAgent(); return }
        if (kind == WakeKind.OFF) { switchOff(); return }
        if (kind == WakeKind.WHERE) {
            say("Je lis les pastilles")
            scope.launch { readPillsReport(applicationContext); listen() }
            return
        }
        if (kind == WakeKind.RESEND) {
            say("Je renvoie les instructions à la session")
            scope.launch {
                val ok = resendInstructions(applicationContext)
                Speaker.speakAndWait(applicationContext, if (ok) "J'ai renvoyé les instructions à la session." else "Je n'ai pas pu renvoyer les instructions.")
                listen()
            }
            return
        }
        if (kind == WakeKind.STOP) {
            // A stop is CONFIRMED (a word heard by mistake must not stop the work): the phone asks, and only « c'est bon vas-y » stops Claude.
            say("Stop ? Dis « c'est bon vas-y » pour arrêter Claude")
            scope.launch {
                if (askAloud(applicationContext, "Tu veux que j'arrête Claude ?")) {
                    val sent = stopClaude(applicationContext)
                    Speaker.speakAndWait(applicationContext, if (sent) "J'ai demandé à Claude de s'arrêter." else "Je n'ai pas pu envoyer l'arrêt à Claude.")
                } else {
                    Speaker.speakAndWait(applicationContext, "D'accord, je n'arrête rien.")
                }
                listen()
            }
            return
        }
        say("Je t'écoute… dis « c'est bon vas-y » pour finir")
        // Woken by a phrase, nobody is looking at a screen: before anything is applied or sent, the phone reads back what it will do AND every pill as it
        // will be, and waits for "c'est bon vas-y" (VoiceDictation, confirmAll). "Non" or silence: nothing is applied, nothing is sent, the message is copied.
        // Where things stand is read on its own only when asked ("t'en es où", "t'es sur quoi"); "mon agent" alone does not.
        dictation = VoiceDictation(
            context = this,
            startInInstructions = true,
            reportFirst = false,
            confirmAll = true,
            onUnderstood = { if (it.isNotBlank()) say(it) },
            onQuestion = { if (!it.isNullOrBlank()) say(it) },
            onFinished = { main.post { dictation = null; listen() } },
        ).also { it.start() }
    }

    /** The widget's mic window takes the microphone: this one lets go until it is done. */
    fun pause() {
        paused = true
        detector?.stop()
        detector = null
        agentListener?.stop()
        agentListener = null
        dictation?.cancel()
        dictation = null
        holdCpu(false)
    }

    fun resume() {
        paused = false
        if (agentMode) agentListen()
        else if (model != null && detector == null && dictation == null) listen()
    }

    private fun holdCpu(on: Boolean) {
        try {
            if (on && lock == null) {
                lock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "aiwa:listen").also { it.acquire() }
            } else if (!on) {
                lock?.let { if (it.isHeld) it.release() }
                lock = null
            }
        } catch (err: Exception) { /* the listening goes on without it */ }
    }

    private fun fail(text: String) {
        toastOnMain(this, text)
        ListenSettings.setEnabled(this, false)
        stopSelf()
    }

    private fun say(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    private fun notification(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Écoute permanente", NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 3, Intent(this, WakeWordService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Aiwa écoute")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_store)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_store), "Arrêter", stop).build())
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }

    override fun onDestroy() {
        instance = null
        AiwaRepository.update { it.copy(listening = false) }
        CoroutineScope(Dispatchers.Default).launch { AiwaWidget().updateAll(applicationContext) }
        detector?.stop()
        detector = null
        agentListener?.stop()
        agentListener = null
        if (agentMode) {
            agentMode = false
            AiwaRepository.update { it.copy(agent = it.agent?.copy(enabled = false)) }
            CoroutineScope(Dispatchers.IO).launch { try { LocalClaudeBridge().agent("set", mapOf("enabled" to false)) } catch (err: Exception) { } }
        }
        dictation?.cancel()
        dictation = null
        holdCpu(false)
        try { model?.close() } catch (err: Exception) { /* already closed */ }
        scope.cancel()
        super.onDestroy()
    }
}
