package com.aiwa.bridge

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import org.json.JSONObject

/** The login could not be completed; the message says why, in words a person can act on. */
class GitHubLoginException(message: String) : Exception(message)

/** What the person has to do: type [userCode] at [verificationUri] (once). */
data class DeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val intervalSeconds: Int,
    val expiresInSeconds: Int,
)

/** One POST of a form, answered with GitHub's JSON as a flat map (its login endpoints answer 200 even for "not yet"). */
fun interface FormPost {
    fun post(url: String, form: Map<String, String>): Map<String, Any?>
}

/** The real transport. GitHub's login endpoints send no CORS headers, so a web page cannot call them: this is why the app does. */
object UrlConnectionPost : FormPost {
    override fun post(url: String, form: Map<String, String>): Map<String, Any?> {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        val body = form.entries.joinToString("&") { (k, v) -> "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}" }
        connection.outputStream.use { it.write(body.toByteArray()) }
        val stream = if (connection.responseCode < 400) connection.inputStream else (connection.errorStream ?: connection.inputStream)
        val json = JSONObject(stream.bufferedReader().use { it.readText() })
        return json.keys().asSequence().associateWith { json.opt(it) }
    }
}

/**
 * GitHub's device flow: the app asks for a code, the person types it on github.com once, and the app polls until GitHub
 * hands over a token. Nothing here is a secret of the app: a device-flow application has no client secret.
 */
class GitHubDeviceFlow(
    private val http: FormPost = UrlConnectionPost,
    private val sleepMs: (Long) -> Unit = { Thread.sleep(it) },
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    fun start(clientId: String, scope: String): DeviceCode {
        val reply = http.post("$BASE/login/device/code", mapOf("client_id" to clientId, "scope" to scope))
        failIfError(reply)
        fun text(key: String) = reply[key] as? String ?: throw GitHubLoginException("GitHub's answer has no $key.")
        fun number(key: String, default: Int) = (reply[key] as? Number)?.toInt() ?: default
        return DeviceCode(text("device_code"), text("user_code"), text("verification_uri"), number("interval", 5), number("expires_in", 900))
    }

    /** Polls, as GitHub asks (its interval, and 5 seconds more each time it says slow_down), until the code is typed, refused or expired. */
    fun awaitToken(clientId: String, code: DeviceCode): String {
        var interval = code.intervalSeconds.coerceAtLeast(1)
        val deadline = nowMs() + code.expiresInSeconds * 1000L
        while (nowMs() < deadline) {
            sleepMs(interval * 1000L)
            val reply = http.post(
                "$BASE/login/oauth/access_token",
                mapOf("client_id" to clientId, "device_code" to code.deviceCode, "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"),
            )
            (reply["access_token"] as? String)?.let { return it }
            when (reply["error"] as? String) {
                "authorization_pending" -> Unit
                "slow_down" -> interval = (reply["interval"] as? Number)?.toInt() ?: (interval + 5)
                else -> failIfError(reply)
            }
        }
        throw GitHubLoginException("The code expired before it was typed on GitHub. Try again.")
    }

    private fun failIfError(reply: Map<String, Any?>) {
        val error = reply["error"] as? String ?: return
        throw GitHubLoginException(
            when (error) {
                "access_denied" -> "You refused the access on GitHub."
                "expired_token" -> "The code expired before it was typed on GitHub. Try again."
                "device_flow_disabled" -> "Device flow is not enabled on this GitHub application (its settings page has a checkbox for it)."
                "incorrect_client_credentials" -> "GitHub does not know this application (deployment.json, github.clientId)."
                else -> (reply["error_description"] as? String) ?: error
            },
        )
    }

    private companion object {
        const val BASE = "https://github.com"
    }
}
