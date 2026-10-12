package com.aiwa.store

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.aiwa.bridge.APP_RELEASE_API
import com.aiwa.bridge.isUpToDate
import com.aiwa.bridge.looksLikeApk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The app updates itself the way Android allows: it finds the latest build (the rolling release the CI publishes), downloads it and opens the
 * system installer, where one tap on « Installer » is the person's consent. Android never lets an app install another one silently. The build is
 * signed with the fixed debug key, so it installs over the present one and keeps its data.
 */
object AppUpdater {
    private fun get(url: String, accept: String? = null): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 60_000; instanceFollowRedirects = true
            if (accept != null) setRequestProperty("Accept", accept)
        }

    fun installedVersion(app: Context): String? = try { app.packageManager.getPackageInfo(app.packageName, 0).versionName } catch (e: Exception) { null }

    /** Looks for the latest build, downloads it and opens the installer; says each step with a toast. */
    fun update(context: Context) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Main).launch {
            try {
                toastOnMain(app, "Recherche de la dernière version d'Aiwa…")
                val (sha, apkUrl) = withContext(Dispatchers.IO) {
                    val release = JSONObject(get(APP_RELEASE_API, "application/vnd.github+json").inputStream.bufferedReader().use { it.readText() })
                    val asset = release.getJSONArray("assets").getJSONObject(0)
                    release.getString("target_commitish") to asset.getString("browser_download_url")
                }
                if (isUpToDate(installedVersion(app), sha)) {
                    toastOnMain(app, "Aiwa est déjà à jour (${installedVersion(app)}).")
                    return@launch
                }
                if (!app.packageManager.canRequestPackageInstalls()) {
                    // Asked once: the setting where Aiwa may install apps. The person comes back and presses the entry again.
                    toastOnMain(app, "Autorise Aiwa à installer des applications, puis reviens appuyer sur « Mettre à jour l'application ».")
                    app.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return@launch
                }
                toastOnMain(app, "Téléchargement de la nouvelle version (environ 30 Mo)…")
                val file = withContext(Dispatchers.IO) {
                    val dir = File(app.cacheDir, "update").apply { deleteRecursively(); mkdirs() }
                    val target = File(dir, "Aiwa_store.apk")
                    val connection = get(apkUrl)
                    connection.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
                    val head = target.inputStream().use { s -> ByteArray(2).also { s.read(it) } }
                    check(looksLikeApk(head, target.length())) { "le fichier téléchargé n'est pas un APK" }
                    target
                }
                val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", file)
                app.startActivity(
                    Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
            } catch (e: Exception) {
                toastOnMain(app, "Mise à jour impossible : ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }
}
