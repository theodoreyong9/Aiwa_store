package com.aiwa.store

import android.os.Bundle
import androidx.activity.ComponentActivity

/** The Chambre cell of the widget: opens or closes the room of the person (her own home). A no-UI trampoline: the service that holds the room needs a visible tap to start. */
class ChamberToggleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChamberActions.press(this, "room", null, true)
        finish()
    }
}
