package com.aiwa.store
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.aiwa.bridge.storeCommentUrl

/**
 * The widget's comment button: opens the Store's comment sheet. A comment is published like code: signed with the person's wallet, accepted by the registry
 * and ranked by what its author mined; the critic reads the best ranked at the start of its turns. It starts nothing. No UI of its own.
 */
class CommentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, StoreActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra(StoreActivity.EXTRA_URL, storeCommentUrl()))
        finish()
    }
}
