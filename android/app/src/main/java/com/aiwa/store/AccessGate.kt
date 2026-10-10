package com.aiwa.store
import android.content.Context
import com.aiwa.bridge.LocalClaudeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Who may use this build of the app: a list of GitHub accounts in a file of a public repository (ACCESS_URL), checked against the
 * account the person signed in with (GitHub's device flow, the same sign-in as the Store's publish sheet; the token is the one
 * the Keystore keeps under "github-token").
 *
 *   level 2  everything (the Store, the Aiwa apps, every deployment)
 *   level 1  GitHub Pages and Android APK only; the Store and the Aiwa apps are empty, with a note saying they are for the level above
 *   level 0  nothing, even installed: the widget and the Store say to sign in, or that the account is not on the list
 *
 * The file is `{ "enforce": false, "level2": ["account"], "level1": ["account"] }`. While `enforce` is false (or the file cannot
 * be read and was never read), everybody has everything: the list does nothing until it is turned on. Honest limit: this is the gate
 * of the official build. The code is public, and anyone can compile a build without it.
 */
object AccessGate {
    const val ACCESS_URL = "https://raw.githubusercontent.com/theodoreyong9/Aiwa_store/main/access.json"
    private const val PREFS = "aiwa_access"
    private const val TOKEN_KEY = "github-token"
    private const val RECHECK_MS = 30 * 60 * 1000L
    private var lastCheck = 0L

    /** Brings back what was known at the last check (so that a widget redrawn offline still knows). */
    fun load(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        publish(p.getBoolean("enforce", false), p.getInt("level", 2), p.getString("login", null))
    }

    private fun publish(enforce: Boolean, level: Int, login: String?) {
        AiwaRepository.update { it.copy(accessEnforced = enforce, accessLevel = if (enforce) level else 2, accessLogin = login) }
    }

    /** The GitHub client id of this deployment (deployment.json, github.clientId); empty until its OAuth application exists. */
    fun clientId(context: Context): String = try {
        JSONObject(context.assets.open("deployment.json").use { String(it.readBytes(), Charsets.UTF_8) }).optJSONObject("github")?.optString("clientId").orEmpty()
    } catch (err: Exception) { "" }

    fun hasToken(context: Context): Boolean = SecretStore(context.applicationContext).get(TOKEN_KEY) != null

    /** Keeps the token of a sign-in (the Store's publish sheet reuses it) and checks the account again at once. */
    suspend fun signedIn(context: Context, token: String) {
        withContext(Dispatchers.IO) { SecretStore(context.applicationContext).set(TOKEN_KEY, token) }
        refresh(context, force = true)
    }

    /** At most every half hour unless forced: reads the list and the account, keeps the answer, tells the app. */
    suspend fun refresh(context: Context, force: Boolean = false) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - lastCheck < RECHECK_MS) return@withContext
        lastCheck = now
        val app = context.applicationContext
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val list = try { JSONObject(get(ACCESS_URL, null)) } catch (err: Exception) { null }
        if (list == null && !p.contains("enforce")) return@withContext      // never read, cannot read: nothing is blocked
        val enforce = list?.optBoolean("enforce", false) ?: p.getBoolean("enforce", false)
        if (!enforce) {
            p.edit().putBoolean("enforce", false).apply()
            publish(false, 2, p.getString("login", null))
            return@withContext
        }
        val token = SecretStore(app).get(TOKEN_KEY)
        var login = p.getString("login", null)
        if (token == null) login = null
        else try {
            login = try { JSONObject(get("https://api.github.com/user", token)).optString("login").ifEmpty { null } } catch (err: UnauthorizedException) { null }
        } catch (err: Exception) { /* offline: the account known at the last check stands */ }
        val names = { key: String -> list?.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it).lowercase() } } ?: p.getString(key, "").orEmpty().split(",").filter { it.isNotEmpty() } }
        val level2 = names("level2")
        val level1 = names("level1")
        val me = login?.lowercase()
        val level = when {
            me == null -> 0
            me in level2 -> 2
            me in level1 -> 1
            else -> 0
        }
        p.edit().putBoolean("enforce", true).putInt("level", level).putString("login", login)
            .putString("level2", level2.joinToString(",")).putString("level1", level1.joinToString(",")).apply()
        publish(true, level, login)
        // Level 1 has no Store and no Aiwa apps: a deployment left on one of them goes back to Pages.
        if (level == 1 && deliversApp(AiwaRepository.state.value.deploy)) {
            try { switchOptions(app, LocalClaudeBridge(), deploy = "pages") } catch (err: Exception) { }
        }
    }

    private class UnauthorizedException : Exception()

    private fun get(address: String, token: String?): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Accept", "application/json")
        if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
        if (connection.responseCode == 401) throw UnauthorizedException()
        if (connection.responseCode >= 400) throw IllegalStateException("HTTP ${connection.responseCode}")
        return connection.inputStream.bufferedReader().use { it.readText() }
    }
}

/** What the person sees when a screen is closed to them: null when it is open. */
fun accessNotice(state: AiwaState, needsLevel: Int): String? = when {
    !state.accessEnforced || state.accessLevel >= needsLevel -> null
    state.accessLevel == 0 && state.accessLogin == null -> "Cette version d'Aiwa est réservée : connecte-toi avec ton compte GitHub."
    state.accessLevel == 0 -> "Le compte GitHub « ${state.accessLogin} » n'a pas accès à cette version d'Aiwa."
    else -> "Le Store et les apps Aiwa sont réservés au niveau supérieur. Ton compte « ${state.accessLogin} » a accès aux modes Pages et APK."
}
