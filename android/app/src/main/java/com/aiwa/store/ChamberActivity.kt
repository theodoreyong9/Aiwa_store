package com.aiwa.store

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.aiwa.bridge.LocalClaudeBridge
import org.json.JSONObject

/** The chamber, shown (the Chambre cell of the widget opens this): who is in it, what the two super agents say, and the buttons of the chamber. It does not open or close anything by itself being opened. */
class ChamberActivity : ComponentActivity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var talk: TextView
    private lateinit var scroll: ScrollView
    private val buttons = LinkedHashMap<String, Button>()
    private var running = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#10132E")); setPadding(36, 36, 36, 24) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets -> val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()); v.setPadding(36 + bars.left, 36 + bars.top, 36 + bars.right, 24 + bars.bottom); WindowInsetsCompat.CONSUMED }
        root.addView(TextView(this).apply { text = "Chambre"; setTextColor(Color.WHITE); textSize = 26f; setTypeface(null, Typeface.BOLD) })
        status = TextView(this).apply { setTextColor(Color.parseColor("#9DB8FF")); textSize = 16f; setPadding(0, 8, 0, 16) }
        root.addView(status)
        scroll = ScrollView(this)
        talk = TextView(this).apply { setTextColor(Color.WHITE); textSize = 17f; setLineSpacing(6f, 1f) }
        scroll.addView(talk)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun button(id: String, row: LinearLayout) {
            val b = Button(this).apply { isAllCaps = false; textSize = 14f; setOnClickListener { ChamberActions.press(this@ChamberActivity, id, null, true); main.postDelayed({ refresh() }, 400) } }
            buttons[id] = b; row.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        button("room", row1); button("roomtalk", row1); button("roomear", row1); button("roomdanger", row2); button("roomnote", row2)
        root.addView(row1); root.addView(row2)
        setContentView(root)
        loop()
    }

    override fun onDestroy() { running = false; super.onDestroy() }

    private fun loop() {
        if (!running) return
        refresh()
        main.postDelayed({ loop() }, 2500)
    }

    private fun refresh() {
        val s = ChamberState.status
        val shown = JSONObject().also { ChamberActions.fill(it) }
        val partner = s.optJSONObject("partner")?.optString("name")
        status.text = when {
            s.optString("mode", "closed") == "closed" -> "Ta chambre est fermée. « Chambre » l'ouvre : tu attends quelqu'un."
            s.optBoolean("conversing") -> "En conversation avec $partner."
            partner != null -> "$partner est là. « Converser » lance la conversation."
            else -> "Ta chambre est ouverte : en attente."
        }
        for ((id, b) in buttons) { val e = shown.optJSONObject(id); b.text = e?.optString("label") ?: id; b.alpha = if (e?.optBoolean("on") == true) 1f else .6f }
        Thread {
            val view = try { LocalClaudeBridge().chamberCall("/api/chamber", JSONObject()) } catch (err: Exception) { null }
            val lines = view?.optJSONArray("lines")
            if (lines != null) {
                val sb = StringBuilder()
                for (i in 0 until lines.length()) { val l = lines.getJSONObject(i); sb.append(if (l.optString("who") == "me") "▸ " else "◂ ").append(l.optString("name")).append(" : ").append(l.optString("text")).append("\n\n") }
                runOnUiThread { if (talk.text.toString() != sb.toString()) { talk.text = sb.toString(); scroll.post { scroll.fullScroll(android.view.View.FOCUS_DOWN) } } }
            }
        }.start()
    }
}
