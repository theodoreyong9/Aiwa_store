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
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

/**
 * Listens for "mon agent" and "instruction" all the time (a foreground service of the microphone type: the phone shows its green microphone dot for as
 * long as it runs), and when one is heard opens a dictation as if the mic of the widget had been touched, with the words that follow read as
 * instructions to Aiwa from the start ("mon agent, t'es sur quoi … modèle Opus, push main … c'est bon vas-y"). "Mon agent" is also the question ("t'es sur quoi ?", or "t'en es où ?"): where
 * things stand is read aloud first. Whatever is said is read back and has to be confirmed, every time (VoiceDictation.confirmAll). Works with the screen locked as long as
 * Android keeps the service alive.
 *
 * What it is not: it is off until the person turns it on; the speech model runs on the phone (VoskModelStore), nothing is sent anywhere;
 * and Android does not let a service of this kind start by itself after a restart of the phone: opening the app once starts it again.
 */
class WakeWordService : Service() {
    companion object {
        private const val CHANNEL_ID = "aiwa_listen"
        private const val NOTIFICATION_ID = 7
        private const val ACTION_STOP = "com.aiwa.store.LISTEN_STOP"

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

        fun stop(context: Context) { context.stopService(Intent(context, WakeWordService::class.java)) }
    }

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var preparing: Job? = null
    private var model: Model? = null
    private var detector: WakeWordDetector? = null
    private var dictation: VoiceDictation? = null
    private var lock: PowerManager.WakeLock? = null
    private var paused = false

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
        if (preparing?.isActive != true && detector == null && dictation == null) preparing = scope.launch { prepare() }
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
        listen()
    }

    private fun listen() {
        val current = model ?: return
        if (paused || dictation != null) return
        say("À l'écoute : dis « mon agent » (t'es sur quoi ?) ou « instruction »")
        holdCpu(true)
        detector = WakeWordDetector(
            model = current,
            onWake = { kind -> main.post { heardWakeWord(kind) } },
            onFailure = { reason -> main.post { fail("Écoute permanente arrêtée : $reason") } },
        ).also { it.start() }
    }

    private fun heardWakeWord(kind: WakeKind) {
        detector = null
        if (paused) return
        try { ToneGenerator(AudioManager.STREAM_MUSIC, 80).startTone(ToneGenerator.TONE_PROP_BEEP, 150) } catch (err: Exception) { /* no tone */ }
        say(if (kind == WakeKind.AGENT) "Je regarde où on en est…" else "Je t'écoute… dis « c'est bon vas-y » pour finir")
        // Woken by a phrase, nobody is looking at a screen: everything is read back and confirmed, every time. "Mon agent" is also the question
        // "t'en es où ?": where things stand is read first.
        dictation = VoiceDictation(
            context = this,
            startInInstructions = true,
            reportFirst = kind == WakeKind.AGENT,
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
        dictation?.cancel()
        dictation = null
        holdCpu(false)
    }

    fun resume() {
        paused = false
        if (model != null && detector == null && dictation == null) listen()
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
        detector?.stop()
        detector = null
        dictation?.cancel()
        dictation = null
        holdCpu(false)
        try { model?.close() } catch (err: Exception) { /* already closed */ }
        scope.cancel()
        super.onDestroy()
    }
}
