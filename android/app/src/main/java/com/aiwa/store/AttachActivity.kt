package com.aiwa.store
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.aiwa.bridge.LocalClaudeBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Largest file the paperclip takes (the backend refuses more too). */
private const val ATTACH_MAX_BYTES = 12 * 1024 * 1024

/**
 * The widget's paperclip: opens Android's file picker, hands the chosen file to the backend, which keeps it
 * until the next message to the session and sends it with that message. No window of its own.
 */
class AttachActivity : ComponentActivity() {
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) finish() else attach(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeAiwa(applicationContext)
        picker.launch(arrayOf("*/*"))
    }

    private fun attach(uri: Uri) {
        val app = applicationContext
        CoroutineScope(Dispatchers.Main).launch {
            val message = withContext(Dispatchers.IO) {
                try {
                    val name = app.contentResolver.query(uri, null, null, null, null)?.use { c ->
                        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (i >= 0 && c.moveToFirst()) c.getString(i) else null
                    } ?: "fichier"
                    val bytes = app.contentResolver.openInputStream(uri)?.use { readCapped(it, ATTACH_MAX_BYTES + 1) } ?: ByteArray(0)
                    if (bytes.size > ATTACH_MAX_BYTES) "Fichier trop gros (maximum ${ATTACH_MAX_BYTES / 1048576} Mo)."
                    else LocalClaudeBridge().attach(name, Base64.encodeToString(bytes, Base64.NO_WRAP)).let {
                        if (it.first) "« $name » est joint (${it.second} en attente) : il part avec ton prochain message." else it.third
                    }
                } catch (e: Exception) {
                    e.message ?: "Impossible de joindre ce fichier."
                }
            }
            toastOnMain(app, message)
            finish()
        }
    }
}

/** Up to `max` bytes of the stream (readNBytes needs a newer Android than the minimum supported). */
private fun readCapped(input: java.io.InputStream, max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    while (out.size() < max) {
        val n = input.read(buf, 0, minOf(buf.size, max - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
