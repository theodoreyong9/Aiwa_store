package com.aiwa.store

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import java.net.HttpURLConnection
import java.net.URL

/**
 * A profile is the file `Profile/index.html` of a repository (a public one): Aiwa reads it from GitHub as it is and shows it in a WebView with no script, the pictures and videos next to it read from the same folder.
 * Nothing has to be published (no GitHub Pages): the address of the file is enough.
 */
object ProfileView {
    private val REPO = Regex("[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}")
    fun valid(repo: String?): Boolean = repo != null && REPO.matches(repo)
    fun base(repo: String): String = "https://raw.githubusercontent.com/$repo/HEAD/Profile/"

    /** Reads the page in the background and shows it; a word on a plain page when there is none (no repository, a private one, no Profile folder yet). */
    fun load(web: WebView, repo: String?) {
        val main = Handler(Looper.getMainLooper())
        if (!valid(repo)) { show(web, main, null, "Pas de profil : choisis le dépôt du profil dans les Réglages."); return }
        Thread {
            val html = try {
                val c = URL(base(repo!!) + "index.html").openConnection() as HttpURLConnection
                c.connectTimeout = 10_000; c.readTimeout = 15_000
                if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
            } catch (err: Exception) { null }
            if (html == null) show(web, main, null, "Le profil de $repo n'est pas encore là (un dossier Profile/ public avec un index.html).")
            else show(web, main, base(repo), html)
        }.start()
    }

    private fun show(web: WebView, main: Handler, base: String?, html: String) {
        val page = if (base != null) html else "<html><body style='margin:0;background:#10132e;color:#d4d8ff;font:16px system-ui;display:grid;place-items:center;height:100vh;text-align:center;padding:24px'>$html</body></html>"
        main.post { try { web.loadDataWithBaseURL(base ?: "about:blank", page, "text/html", "utf-8", null) } catch (err: Exception) { } }
    }
}
