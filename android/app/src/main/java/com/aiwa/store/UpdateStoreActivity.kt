package com.aiwa.store
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * The widget's "update ready" button: no UI. Opens the Store with the downloaded release in place of the one it ran (the Store applies it
 * as it starts, or at once when it was already open).
 */
class UpdateStoreActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        startActivity(
            Intent(this, StoreActivity::class.java)
                .putExtra(StoreActivity.EXTRA_APPLY_UPDATE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}
