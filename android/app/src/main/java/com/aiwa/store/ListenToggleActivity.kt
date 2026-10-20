package com.aiwa.store
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The switch of the permanent listening, at the right of the 🤖 button of the widget: on, it starts the wake-word listening (the window is on screen for a moment, which is what Android asks to start a
 * microphone service); off, it stops it. It is closed at each installation and each update. A no-UI trampoline.
 */
class ListenToggleActivity : ComponentActivity() {
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) turnOn() else toastOnMain(applicationContext, "Écoute permanente : sans la permission du micro, rien à faire.")
        finish()
    }

    private fun turnOn() {
        ListenSettings.setEnabled(this, true)
        WakeWordService.start(this)
        refreshWidget()
    }

    private fun refreshWidget() {
        val app = applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            AiwaWidget().updateAll(app)
            kotlinx.coroutines.delay(1_500)      // the service has started by then: the switch shows it
            AiwaWidget().updateAll(app)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        val running = WakeWordService.instance != null
        when {
            ListenSettings.enabled(this) && running -> {
                ListenSettings.setEnabled(this, false)
                WakeWordService.stop(this)
                AiwaRepository.update { it.copy(listening = false) }
                refreshWidget()
            }
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED -> { micPermission.launch(Manifest.permission.RECORD_AUDIO); return }
            else -> turnOn()
        }
        finish()
    }
}
