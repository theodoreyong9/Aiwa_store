package com.aiwa.bridge

// Updating the app itself. The CI publishes every build of main as the one file of a rolling release (tag android-latest), whose description names the commit.
// The installed build names its commit in its version (0.1.<first seven characters of the commit>, or 0.1.local for a build made by hand).

/** The release the app updates from: its commit and the address of its APK are read there. */
const val APP_RELEASE_API = "https://api.github.com/repos/theodoreyong9/Aiwa_store/releases/tags/android-latest"

/** Whether the installed build is the one of `releaseSha` (the full commit of the release). A local build is never up to date. */
fun isUpToDate(versionName: String?, releaseSha: String): Boolean {
    val short = versionName?.substringAfterLast('.') ?: return false
    return short.length >= 7 && releaseSha.startsWith(short, ignoreCase = true)
}

/** An APK starts like any zip file, with "PK" and is far bigger than an error page. */
fun looksLikeApk(firstBytes: ByteArray, size: Long): Boolean = size > 1_000_000 && firstBytes.size >= 2 && firstBytes[0] == 'P'.code.toByte() && firstBytes[1] == 'K'.code.toByte()

/** An mp4 or a mov starts with a box whose type, "ftyp", sits at bytes 4 to 8; a web page or an error is not that. */
fun looksLikeVideo(firstBytes: ByteArray, size: Long): Boolean =
    size > 10_000 && firstBytes.size >= 8 && String(firstBytes, 4, 4, Charsets.ISO_8859_1) == "ftyp"

/** The page of the rolling release where the APK is, and the Store on the web: what an invitation carries. */
const val APP_RELEASE_PAGE = "https://github.com/theodoreyong9/Aiwa_store/releases/tag/android-latest"
const val STORE_WEB_URL = "https://theodoreyong9.github.io/Aiwa_store/"

/** The text of an invitation to open an app (its Aiwa address) or, with no app, to join Aiwa. */
fun inviteText(appName: String?, address: String?): String =
    if (appName != null && address != null) "Je t'invite à ouvrir « $appName » dans Aiwa : $address\nAiwa (Android) : $APP_RELEASE_PAGE"
    else "Je t'invite sur Aiwa : $STORE_WEB_URL\nAiwa (Android) : $APP_RELEASE_PAGE"
