package com.aiwa.store
import android.content.Context
import com.aiwa.bridge.LocalClaudeBridge
import com.aiwa.bridge.Recognitions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** The nine recognitions as they are now, and what happens when one changes: the small model, the parser and the super agent all get the new words. */
object Vocabulary {
    /** The words Aiwa SAYS for a recognition of its own (Accomplissement, Question) or asks for (Confirmation): the one the person wrote, else the one of origin. */
    fun spoken(context: Context, slot: String): String =
        WakeWordSettings.written(context, slot).firstOrNull() ?: Recognitions.slot(slot)?.defaults?.firstOrNull().orEmpty()

    /** label → the words the phone recognises for it: those chosen by voice (their name) and written, else the words of origin. */
    fun describe(context: Context): Map<String, List<String>> = Recognitions.SLOTS.associate { slot ->
        val spoken = WakeWordSettings.name(context, slot.id).takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
        val written = WakeWordSettings.written(context, slot.id)
        slot.label to (spoken + written).ifEmpty { slot.defaults }
    }

    /** Called after any change: the parser's words are set again, the small model listens for the new ones, and the super agent is told (it answers « noté »). */
    fun changed(context: Context) {
        val app = context.applicationContext
        WakeWordSettings.applyVocabulary(app)
        WakeWordService.reload(app)
        val words = JSONObject().also { o -> describe(app).forEach { (label, list) -> o.put(label, JSONArray(list)) } }
        CoroutineScope(Dispatchers.IO).launch {
            try { LocalClaudeBridge().agent("vocabulary", mapOf("words" to words)) } catch (err: Exception) { /* no super agent yet, or an older brain: nothing to tell */ }
        }
    }
}
