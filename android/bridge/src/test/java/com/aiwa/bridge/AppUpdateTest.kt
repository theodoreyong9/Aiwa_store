package com.aiwa.bridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
    private val sha = "ebff2e7a1b2c3d4e5f60718293a4b5c6d7e8f901"

    @Test
    fun theInstalledBuildIsRecognisedByItsCommit() {
        assertTrue(isUpToDate("0.1.ebff2e7", sha))
        assertFalse(isUpToDate("0.1.0f73312", sha))
    }

    @Test
    fun aBuildMadeByHandIsNeverUpToDate() {
        assertFalse(isUpToDate("0.1.local", sha))
        assertFalse(isUpToDate(null, sha))
        assertFalse(isUpToDate("", sha))
    }

    @Test
    fun onlyAnApkIsInstalled() {
        assertTrue(looksLikeApk(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4), 40_000_000))
        assertFalse(looksLikeApk("<h".toByteArray(), 40_000_000))
        assertFalse(looksLikeApk(byteArrayOf('P'.code.toByte(), 'K'.code.toByte()), 500))
    }
}
