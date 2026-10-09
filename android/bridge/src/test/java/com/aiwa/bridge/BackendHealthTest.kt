package com.aiwa.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendHealthTest {
    @Test fun withoutTermuxItIsSaidAtOnce() {
        assertEquals(BackendVerdict.TERMUX_MISSING, backendVerdict(termuxInstalled = false, startsWithoutAnswer = 0))
        assertEquals(BackendVerdict.TERMUX_MISSING, backendVerdict(termuxInstalled = false, startsWithoutAnswer = 5))
    }

    @Test fun aBackendThatNeverComesUpIsCalledNotInstalledAfterTwoStarts() {
        assertEquals(BackendVerdict.DOWN, backendVerdict(termuxInstalled = true, startsWithoutAnswer = 0))
        assertEquals(BackendVerdict.DOWN, backendVerdict(termuxInstalled = true, startsWithoutAnswer = STARTS_BEFORE_MISSING - 1))
        assertEquals(BackendVerdict.NOT_INSTALLED, backendVerdict(termuxInstalled = true, startsWithoutAnswer = STARTS_BEFORE_MISSING))
        assertEquals(BackendVerdict.NOT_INSTALLED, backendVerdict(termuxInstalled = true, startsWithoutAnswer = 9))
    }

    @Test fun eachProblemHasItsOwnMessageAndOnlyDownPromisesAStart() {
        val messages = BackendVerdict.values().associateWith { backendProblemMessage(it) }
        assertEquals(BackendVerdict.values().size, messages.values.toSet().size)
        assertTrue(messages.getValue(BackendVerdict.TERMUX_MISSING).contains("F-Droid"))
        assertTrue(messages.getValue(BackendVerdict.NOT_INSTALLED).contains("ligne d'installation"))
        assertFalse(messages.getValue(BackendVerdict.TERMUX_MISSING).contains("lancement automatique"))
        assertFalse(messages.getValue(BackendVerdict.NOT_INSTALLED).contains("lancement automatique"))
        assertTrue(messages.getValue(BackendVerdict.DOWN).contains("lancement automatique"))
    }

    @Test fun theInstallLineIsTheOneOfTheReadmeAndTheScriptExists() {
        assertTrue(BOOTSTRAP_COMMAND.startsWith("curl -fsSL https://raw.githubusercontent.com/theodoreyong9/public/main/"))
        assertTrue(BOOTSTRAP_COMMAND.endsWith("| bash"))
        val readme = java.io.File("../../README.md").takeIf { it.exists() }?.readText()
        if (readme != null) assertTrue("the README gives the same line", readme.contains(BOOTSTRAP_COMMAND))
        val script = java.io.File("../backend/bootstrap.sh")
        if (java.io.File("../backend").isDirectory) assertTrue("the script the line downloads", script.exists())
    }
}
