package com.aiwa.store
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.aiwa.bridge.inviteText

/**
 * The widget's « invite » button: opens Android's share sheet with an invitation (the Store and the page of the APK), which the person sends by
 * whatever they use. The invitation to open one given app is the 🔗 button of that app in the Store, which copies its Aiwa address. No UI of its own.
 */
class InviteActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, inviteText(null, null))
        startActivity(Intent.createChooser(send, "Inviter").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
