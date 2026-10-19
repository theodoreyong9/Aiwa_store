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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model

/** Whether the person turned the always-on listening on (off until they do, in the app's screen). */
object ListenSettings {
    private const val PREFS = "aiwa_listen"
    // On by default (the wake word must work without a button, the screen locked): only « Arrêter » in the notification turns it off. "on_v2": the old flag, which closing the agent used to switch off, is ignored.
    fun enabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on_v2", true)
    fun setEnabled(context: Context, on: Boolean) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on_v2", on).apply() }
}

/** « Conversation fluide »: a question is answered at once (no send word), the answer starts after its first sentence, the listening stays open longer and the history of the talk is kept. On by default. */
object ConversationSettings {
    private const val PREFS = "aiwa_listen"
    fun fluid(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("fluid", true)
    fun setFluid(context: Context, on: Boolean) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("fluid", on).apply() }
    const val QUIET_MS = 30_000L
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
private val ERASE_REGEX = Regex("""efface(?:\s+(?:tout|ca|le|la|ce|message|commande))*""")
private val OFF_REGEX = Regex("""\beteins\b""")
private val STOP_AGENT_REGEX = Regex("""(?:stop|arrete)(?:\s+(?:l\s+|la\s+)?(?:super\s+)?(?:agent|claude|commande))?""")
private val MANUAL_REGEX = Regex("""\b(?:repasse|reviens|retourne|passe)\s+(?:en|au)\s+(?:mode\s+)?manuel\b|\b(?:ferme|quitte)\s+(?:l\s+)?agent\b""")
// What the phone says (its own light voice, at once) while the agent session thinks; it stops as soon as the real answer is there.
private val FILLERS = listOf("Je regarde.", "Je réfléchis.", "Un instant.", "Toujours là, je vérifie.")

private val FILM_REGEX = Regex("""\bfilm de moi\b|\bfilm avec moi\b|\bfilm ou je (?:suis|dis|parle)\b""")

private val APPLY_REGEX = Regex("""\bfais[\s-]?le\b|\bapplique\b""")
private val SEND_REGEX = Regex("""\bdis?t?[\s-]?lui\b""")

/** The agent as the widget sees it with the flag set: made from nothing when the brain has not described one yet (it used to stay null, and the widget never knew the super agent was open). */
internal fun agentFlag(agent: com.aiwa.bridge.AgentInfo?, on: Boolean): com.aiwa.bridge.AgentInfo =
    agent?.copy(enabled = on) ?: com.aiwa.bridge.AgentInfo(enabled = on, session = null, interval = 0, creating = false, error = null, say = null, proposal = null)

class WakeWordService : Service() {
    companion object {
        private const val CHANNEL_ID = "aiwa_listen"
        private const val NOTIFICATION_ID = 7
        private const val ACTION_STOP = "com.aiwa.store.LISTEN_STOP"
        private const val ACTION_OPEN_AGENT = "com.aiwa.store.OPEN_AGENT"

        /** The running service, for the code of this same process that needs the microphone for itself (the widget's mic window). */
        @Volatile var instance: WakeWordService? = null

        /** Called whenever Aiwa is on screen: the wake word listens again if it is wanted and is not running (after a restart of the phone or of the app, which Android does not allow to do from the background). */
        fun ensureStarted(context: Context) {
            if (!ListenSettings.enabled(context) || instance != null) return
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
            start(context, quiet = true)
        }

        /** From a visible screen only: Android refuses to start a microphone service from the background. */
        fun start(context: Context, quiet: Boolean = false) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                if (!quiet) toastOnMain(context, "Écoute permanente : la permission du micro manque.")
                return
            }
            try {
                ContextCompat.startForegroundService(context, Intent(context, WakeWordService::class.java))
            } catch (err: Exception) {
                // Refused when the app is not on screen (after a restart or an update): said only when the person asked for it (the robot button), not by the automatic starts
                if (!quiet) toastOnMain(context, "Écoute permanente impossible : ${err.message}")
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
                AiwaRepository.update { it.copy(agent = agentFlag(it.agent, false)) }
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
        keepPending()
        scope.launch {
            waitToken++
            agentListener?.stop(); agentListener = null
            detector?.stop(); detector = null
            agentCall("set", mapOf("enabled" to false))
            AiwaRepository.update { it.copy(agent = agentFlag(it.agent, false), listening = false) }
            ListenSettings.setEnabled(applicationContext, false)
            AiwaWidget().updateAll(applicationContext)
            Speaker.speakNatural(applicationContext, Vocabulary.spoken(applicationContext, "veille").replaceFirstChar { it.uppercase() } + ".")
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
    // The question of Claude that was read aloud and waits for its spoken answer, and the one already read (a question is read once; the next one only comes after this one was answered).
    private var answeringAsk: com.aiwa.bridge.AskInfo? = null
    private var draftOffered = false
    // What was said since the last send word (« s'il te plaît »): a command may come in several sentences; it is taken only when the send word is said.
    private val pendingCommand = StringBuilder()
    private var askRead: String? = null
    private var runningCommand: VoiceDictation? = null

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
            // Android refuses a microphone service started while the app is not on screen (the first opening, a restart): no message, and the wish is kept: it starts as soon as Aiwa is shown (ensureStarted)
            stopSelf()
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

    /** Is the super agent open (listening for a command) right now? The widget button trusts this, not the brain's flag. */
    fun isAgentOpen(): Boolean = agentMode

    fun openAgent() {
        main.post {
            if (agentMode || model == null) return@post
            detector?.stop()
            detector = null
            agentMode = true
            agentBusy = true
            answeringAsk = null
            pendingCommand.clear()
            holdCpu(true)
            AiwaRepository.update { it.copy(agent = agentFlag(it.agent, true)) }
            scope.launch {
                agentCall("set", mapOf("enabled" to true))
                // The natural voice is installed once (about 80 MB), in the backend; until it is ready the phone's own voice speaks.
                if (AiwaRepository.state.value.agent?.voice == "none") try { withContext(Dispatchers.IO) { LocalClaudeBridge().voiceSetup() } } catch (err: Exception) { }
                say("Super agent : dis ta commande, puis « " + Vocabulary.spoken(applicationContext, "envoi") + " »")
                Speaker.speakNatural(applicationContext, Vocabulary.spoken(applicationContext, "ecoute").replaceFirstChar { it.uppercase() } + ".")
                AgentDraft.get(applicationContext)?.let { draft ->
                    // a message that did not leave: read again in a few words, the validation sends it, anything else goes on from it, « annule » drops it
                    draftOffered = true
                    Speaker.speakNatural(applicationContext, "Il reste un message : " + draft.take(160) + ".")
                }
                agentBusy = false
                agentListen()
                maybeSpeakAgent()
                CoroutineScope(Dispatchers.Default).launch { AiwaWidget().updateAll(applicationContext) }
            }
        }
    }

    /** A command begun and not finished (no send word yet) is not lost: it becomes the draft. */
    private fun keepPending() {
        if (pendingCommand.isNotBlank()) AgentDraft.set(applicationContext, pendingCommand.toString())
        pendingCommand.clear()
    }

    /** Nothing heard for ten seconds: the super agent goes back to sleep (the wake word listens again). Its unfinished message is kept (AgentDraft), and offered when it opens again. */
    private fun standbyAgent(force: Boolean = false) {
        if (!agentMode || (!force && (agentBusy || paused))) return
        runningCommand?.cancel()
        runningCommand = null
        waitToken++
        agentMode = false
        agentListener?.stop()
        agentListener = null
        answeringAsk = null
        draftOffered = false
        keepPending()
        AiwaRepository.update { it.copy(agent = agentFlag(it.agent, false)) }
        scope.launch {
            agentCall("set", mapOf("enabled" to false))
            CoroutineScope(Dispatchers.Default).launch { AiwaWidget().updateAll(applicationContext) }
            Speaker.speakNatural(applicationContext, Vocabulary.spoken(applicationContext, "veille").replaceFirstChar { it.uppercase() } + ".")
            if (!agentMode) listen()
        }
    }

    /** The button of the widget (or « repasse en manuel » / « éteins toi »): the super agent goes back to sleep, the wake word keeps listening (only « Arrêter » in the notification stops it). */
    fun closeAgent() {
        main.post { if (agentMode) standbyAgent(force = true) }
    }

    private suspend fun agentCall(action: String, fields: Map<String, Any> = emptyMap()): String? =
        try { withContext(Dispatchers.IO) { LocalClaudeBridge().agent(action, fields) } } catch (err: Exception) { err.message ?: "?" }

    private fun agentListen() {
        if (!agentMode || paused || agentListener != null) return
        agentListener = AgentListener(this, onQuiet = { main.post { standbyAgent() } }, quietMs = if (ConversationSettings.fluid(this)) ConversationSettings.QUIET_MS else AgentListener.QUIET_MS) { text -> main.post { heardForAgent(text) } }.also { it.start() }
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
                OFF_REGEX.containsMatchIn(plain) || com.aiwa.bridge.VoiceVocabulary.phraseHeard("eteins", plain) -> { agentBusy = false; standbyAgent(force = true); return@launch }
                (STOP_AGENT_REGEX.matches(plain) || com.aiwa.bridge.VoiceVocabulary.phraseHeard("stop", plain)) -> {
                    // « stop », CONFIRMED first: what waits is dropped, and the sessions the agent last spoke to are asked to forget its last comment
                    answeringAsk = null
                    keepPending()
                    if (askAloud(applicationContext, "Tu veux que j'arrête ?") { Speaker.speakNatural(applicationContext, it) }) {
                        val told = agentCall("stop")
                        say(if (told == null) "Stop : les sessions touchées en dernier oublient son dernier commentaire." else "Stop (les sessions n'ont pas été prévenues : $told)")
                    } else say("Stop non confirmé : rien n'est arrêté.")
                }
                MANUAL_REGEX.containsMatchIn(plain) -> { agentBusy = false; closeAgent(); return@launch }
                // « efface »: what was said and has not left (the command being made, the message kept) is dropped
                ERASE_REGEX.matches(plain) || com.aiwa.bridge.VoiceVocabulary.phraseHeard("efface", plain) -> {
                    pendingCommand.clear()
                    draftOffered = false
                    AgentDraft.clear(applicationContext)
                    say("Effacé")
                }
                draftOffered -> {
                    draftOffered = false
                    val draft = AgentDraft.get(applicationContext)
                    when {
                        draft == null -> {}
                        containsConfirmation(text) -> runAgentCommand(draft, validated = true)
                        com.aiwa.bridge.endsWithCancelPhrase(text) || plain.startsWith("annule") || plain.startsWith("efface") -> { AgentDraft.clear(applicationContext); say("Message abandonné") }
                        else -> { AgentDraft.clear(applicationContext); pendingCommand.append(draft); addToCommand(text) }
                    }
                }
                answeringAsk != null -> {
                    // The question of Claude read aloud just before: what is said now is the answer. Nothing else is asked until it is given.
                    val ask = answeringAsk!!
                    answeringAsk = null
                    val reply = ask.options.firstOrNull { com.aiwa.bridge.WakeWords.plain(it) == plain } ?: text.trim()
                    val error = try { withContext(Dispatchers.IO) { LocalClaudeBridge().askAnswer(ask.id, reply); null } } catch (err: Exception) { err.message ?: "?" }
                    say(if (error == null) "Réponse envoyée : $reply" else "Réponse non partie : $error")
                    try { withContext(Dispatchers.IO) { BackendSync.refresh(LocalClaudeBridge()) } } catch (err: Exception) { }
                }
                else -> {
                    addToCommand(text)
                }
            }
            agentBusy = false
            agentListen()
        }
    }

    // A command is kept, sentence after sentence, until the send word. Nothing is spoken meanwhile; the summary comes with the repository.
    private suspend fun addToCommand(text: String) {
        // « fais-moi un film de moi … »: the film circuit (its own key, photos and quote), after a yes
        if (pendingCommand.isEmpty() && FILM_REGEX.containsMatchIn(plainWords(text).joinToString(" "))) {
            filmByVoice(withoutSend(text).trim())
            return
        }
        // Conversation fluide: a question said on its own (nothing waiting) is answered at once, without the send word
        if (pendingCommand.isEmpty() && ConversationSettings.fluid(this) && com.aiwa.bridge.isQuestion(withoutSend(text)) ) {
            answerQuestion(withoutSend(text).trim())
            return
        }
        if (pendingCommand.isNotEmpty()) pendingCommand.append(' ')
        pendingCommand.append(text.trim())
        say("Toi : " + pendingCommand)
        if (containsSend(pendingCommand.toString())) {
            val command = withoutSend(pendingCommand.toString())
            pendingCommand.clear()
            if (command.isNotBlank()) { if (com.aiwa.bridge.isQuestion(command)) answerQuestion(command) else runAgentCommand(command) }
        }
    }

    private suspend fun filmByVoice(direction: String) {
        say("Film de moi : $direction")
        val status = withContext(Dispatchers.IO) { LocalClaudeBridge().filmStatus() }
        if (status == null) { Speaker.speakNatural(applicationContext, "Le cerveau ne répond pas, ou il est trop ancien pour les films."); return }
        if (!status.key) {
            Speaker.speakNatural(applicationContext, "Il me faut d'abord ta clé fal. Je t'ouvre les réglages du film.")
            applicationContext.startActivity(Intent(applicationContext, FilmSettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val yes = Vocabulary.spoken(applicationContext, "validation")
        val agreed = askAloud(applicationContext, "Pour un film de toi, j'utilise ta clé fal et tes photos. C'est facturé par fal, et je te montre le prix avant. Dis « $yes » pour continuer.") { Speaker.speakNatural(applicationContext, it) }
        if (!agreed) { Speaker.speakNatural(applicationContext, "D'accord, je laisse."); return }
        FilmActivity.open(applicationContext, direction)
    }

    // A QUESTION is answered by the super agent itself, aloud (Claude, through the brain); it goes to no session. A COMMAND goes through the summary and the validation.
    private suspend fun answerQuestion(question: String) {
        say("Question : $question")
        // While it thinks: nothing for two and a half seconds, then a short word, so that the person knows it did not fail
        val thinking = scope.launch {
            delay(2_500)
            Speaker.speakAndWait(applicationContext, "Je réfléchis.")
            delay(10_000)
            Speaker.speakAndWait(applicationContext, "Un instant.")
        }
        if (ConversationSettings.fluid(this)) {
            // The voice follows the text as Claude writes it: the first sentence is heard while the others are still coming
            val sentences = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.UNLIMITED)
            var heard = false
            val reader = scope.launch(Dispatchers.IO) {
                try {
                    LocalClaudeBridge().answerStream(question) { sentence ->
                        if (!heard) { heard = true; thinking.cancel() }
                        say("Réponse : $sentence")
                        sentences.trySend(sentence)
                    }
                } finally { sentences.close() }
            }
            Speaker.speakFlow(applicationContext, sentences)
            reader.join()
            thinking.cancel()
            if (heard) return
        }
        val (ok, text) = try { withContext(Dispatchers.IO) { LocalClaudeBridge().answer(question) } } finally { thinking.cancel() }
        if (ok) {
            say("Réponse : $text")
            Speaker.speakNatural(applicationContext, text)
        } else {
            say("Réponse impossible : $text")
            Speaker.speakNatural(applicationContext, "Je n'ai pas pu répondre.")
        }
    }

    // The command leaves through the same path as a dictation: the repository (named in the command, or the pill's) is confirmed with « c'est ça », then the command goes to the session.
    private suspend fun runAgentCommand(command: String, validated: Boolean = false) {
        suspendCancellableCoroutine<Unit> { cont ->
            val spoken = VoiceDictation(
                context = this,
                startInInstructions = true,
                confirmRepoAlways = true,
                onQuestion = { if (!it.isNullOrBlank()) say(it) },
                onFinished = { main.post { runningCommand = null; if (cont.isActive) cont.resume(Unit) } },
            )
            runningCommand = spoken
            cont.invokeOnCancellation { spoken.cancel() }
            scope.launch { spoken.runCommand(command, validated) }
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
        val connector = command.connector
        if (connector != null && !ConnectorSettings.enabled(applicationContext, connector.kind)) {
            val message = "Le connecteur ${ConnectorSettings.label(connector.kind)} est coupé dans les réglages : rien n'est fait."
            toastOnMain(applicationContext, message)
            Speaker.speakNatural(applicationContext, message)
            agentCall("cancel", mapOf("id" to command.id))
            return
        }
        say(command.readBack)
        if (!askAloud(applicationContext, command.readBack) { Speaker.speakNatural(applicationContext, it) }) {
            toastOnMain(applicationContext, "Pas confirmé : rien n'est appliqué. Les réglages attendent.")
            Speaker.speakNatural(applicationContext, "Pas appliqué. Les réglages attendent.")
            return
        }
        if (command.instruction.isNotBlank()) VoiceDictation(context = this, startInInstructions = true, onFinished = {}).applyInstruction(command.instruction)
        command.cast?.let { applyCast(it) }
        command.open?.let { openProduction(applicationContext, it) }
        connector?.let { if (!runConnector(applicationContext, it)) toastOnMain(applicationContext, "${ConnectorSettings.label(it.kind)} : aucune application n'a pu s'ouvrir.") }
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
        // The agent no longer speaks on its own. What is read aloud: the question of a session, simply, once, and the next one only after this one was answered.
        if (!agentMode || agentBusy || paused || answeringAsk != null) return
        val ask = AiwaRepository.state.value.ask ?: return
        if (ask.id == askRead) return
        askRead = ask.id
        agentBusy = true
        agentListener?.stop()
        agentListener = null
        scope.launch {
            val options = if (ask.options.isNotEmpty()) " " + ask.options.joinToString(", ou ") + "." else ""
            say("Question : " + ask.question)
            Speaker.speakAndWait(applicationContext, Vocabulary.spoken(applicationContext, "question").replaceFirstChar { it.uppercase() } + ".")
            Speaker.speakAndWait(applicationContext, ask.question.trim().trimEnd('.', '?') + " ?" + options)
            delay(300)
            answeringAsk = ask
            agentBusy = false
            agentListen()
            // no answer for a minute: the question stays on the widget, and is not read again
            val waiting = ask.id
            delay(60_000)
            if (answeringAsk?.id == waiting) answeringAsk = null
        }
    }

    private fun listen() {
        val current = model ?: return
        if (paused || dictation != null) return
        say("À l'écoute : dis « " + WakeWordSettings.name(this, "liaison").ifBlank { "mon super agent" } + " » pour parler au super agent, ou « stop Claude »")
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
        // « éteins toi » while only the wake word listens: nothing is open, nothing to turn off — the wake word stays (the button of the widget stops everything)
        if (kind == WakeKind.OFF) { listen(); return }
        if (kind == WakeKind.WHERE) {
            say("Je lis les pastilles")
            scope.launch { readPillsReport(applicationContext); listen() }
            return
        }
        // « renvoie les instructions » is gone: the consignes go with the messages, nothing to resend by voice
        if (kind == WakeKind.RESEND) { listen(); return }
        if (kind == WakeKind.STOP) {
            // A stop is CONFIRMED (a word heard by mistake must not stop the work): the phone asks, and only the confirmation (« c'est ça ») stops Claude.
            say("Stop ? Dis « c'est bien ça » pour arrêter Claude")
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
        // « mon agent » and the other wake words open the super agent too: one listening, one way to give a command
        openAgent()
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
            AiwaRepository.update { it.copy(agent = agentFlag(it.agent, false)) }
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
