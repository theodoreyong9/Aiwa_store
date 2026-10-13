package com.aiwa.store
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The widget's battery line: shows Android's own question (the exemption from the battery's optimisation). No UI of its own. */
class BatteryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = applicationContext
        if (!BatteryExemption.isExempt(app)) {
            BatteryExemption.noteAsked(app)
            try { startActivity(BatteryExemption.requestIntent(app)) } catch (e: Exception) { toastOnMain(app, "Réglages d'Android > Batterie > Aiwa : « Sans restriction ».") }
        }
        AiwaRepository.update { it.copy(batteryRestricted = BatteryExemption.shouldWarn(app)) }
        CoroutineScope(Dispatchers.Default).launch { AiwaWidget().updateAll(app) }
        finish()
    }
}
