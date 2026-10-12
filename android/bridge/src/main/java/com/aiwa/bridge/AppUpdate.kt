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
