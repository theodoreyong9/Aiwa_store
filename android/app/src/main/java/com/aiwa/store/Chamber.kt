package com.aiwa.store

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import org.json.JSONObject

/**
 * The chamber: a room where the super agent of the visited and the one of the visitor meet. The connection between the two phones lives in a small page of the web app (apps/web chambre.js, a hidden view of the Store),
 * the words of the agents are made by the brain (aiwa_chamber.py); here are the buttons' side: what the page says is going on (`ChamberState`), the way to give it orders (`ChamberHost`) and what each button does (`ChamberActions`).
 */
object ChamberState {
    @Volatile var status: JSONObject = JSONObject()
    @Volatile var ear: Boolean = false

    /** What the page says now; a visitor who arrives or leaves is told on the screen. */
    fun update(context: Context, next: JSONObject) {
        val before = status.optJSONObject("partner")?.optString("name")
        val after = next.optJSONObject("partner")?.optString("name")
        status = next
        ear = next.optBoolean("ear", false)
        if (after != before) {
            if (after != null) toastOnMain(context, "$after est dans la chambre.")
            else if (before != null) toastOnMain(context, "$before a quitté la chambre.")
        }
    }
}

object ChamberHost {
    /** The Store's page that holds the connection (set while the Store is open); the orders wait here when it is not. */
    @Volatile var runner: ((JSONObject) -> Unit)? = null
    private val waiting = ArrayList<JSONObject>()

    fun send(context: Context, cmd: JSONObject) {
        val run = runner
        if (run != null) { run(cmd); return }
        synchronized(waiting) { waiting.add(cmd) }
        context.startActivity(Intent(context, StoreActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun drain(): List<JSONObject> = synchronized(waiting) { val out = ArrayList(waiting); waiting.clear(); out }
}

object ChamberActions {
    private fun mode() = ChamberState.status.optString("mode", "closed").ifBlank { "closed" }
    private fun partnered() = ChamberState.status.optJSONObject("partner") != null
    private fun conversing() = ChamberState.status.optBoolean("conversing", false)

    fun press(context: Context, id: String, source: String?, own: Boolean) {
        when (id) {
            "room" -> {
                val here = if (own) ProfileSettings.repo(context) else source
                if (!ProfileView.valid(here)) { toastOnMain(context, "Pas de profil pour la chambre."); return }
                val room = ChamberState.status.optString("room")
                when {
                    mode() == "closed" -> ChamberHost.send(context, JSONObject().put("cmd", if (own) "open" else "visit").put("repo", here))
                    room == here -> ChamberHost.send(context, JSONObject().put("cmd", "close"))
                    else -> toastOnMain(context, "Tu es déjà dans une autre chambre : ferme-la d'abord.")
                }
            }
            "roomtalk" -> when {
                conversing() -> ChamberHost.send(context, JSONObject().put("cmd", "end"))
                partnered() -> ChamberHost.send(context, JSONObject().put("cmd", "begin"))
                else -> toastOnMain(context, "Personne dans la chambre pour l'instant.")
            }
            "roomear" -> ChamberHost.send(context, JSONObject().put("cmd", "ear"))
            "roomdanger" -> {
                if (ChamberState.status.optBoolean("dangerMe", false)) { ChamberHost.send(context, JSONObject().put("cmd", "danger").put("on", false)); return }
                val activity = context as? Activity
                val on = { ChamberHost.send(context, JSONObject().put("cmd", "danger").put("on", true)) }
                if (activity == null) { toastOnMain(context, "Ouvre Aiwa pour activer le danger."); return }
                activity.runOnUiThread {
                    AlertDialog.Builder(activity)
                        .setTitle("Activer le danger ?")
                        .setMessage("Si l'autre l'active aussi, vos super agents pourront s'envoyer des messages (à ton super agent) et des propositions (à toi, qui réponds toujours). Sans cela, ils ne font que parler.")
                        .setPositiveButton("Activer") { _, _ -> on() }
                        .setNegativeButton("Annuler", null)
                        .show()
                }
            }
        }
    }

    private fun entry(label: String, tone: String, on: Boolean, found: Boolean? = null): JSONObject {
        val o = JSONObject().put("label", label).put("tone", tone).put("on", on)
        if (found != null) o.put("found", found)
        return o
    }

    /** What the four buttons of the chamber show now. */
    fun fill(out: JSONObject) {
        val s = ChamberState.status
        val name = s.optJSONObject("partner")?.optString("name")
        val busy = s.optString("refused") == "busy"
        out.put("room", when {
            busy -> entry("Occupé", "red", false)
            mode() == "closed" -> entry("Chambre", "grey", false)
            conversing() -> entry("Avec $name", "green", true)
            name != null -> entry(name, "green", true)
            else -> entry("En attente", "violet", true)
        })
        out.put("roomtalk", entry(if (conversing()) "Couper" else "Converser", if (conversing()) "red" else "green", conversing(), partnered()))
        out.put("roomear", entry("Écouter", "violet", ChamberState.ear))
        val both = s.optBoolean("dangerMe", false) && s.optBoolean("dangerThem", false)
        out.put("roomdanger", entry(if (both) "Danger actif" else "Danger", "red", s.optBoolean("dangerMe", false)))
    }
}
