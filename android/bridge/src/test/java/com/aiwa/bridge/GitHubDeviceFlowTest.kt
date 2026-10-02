package com.aiwa.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GitHubDeviceFlowTest {
    private class Script(vararg replies: Map<String, Any?>) : FormPost {
        val queue = replies.toMutableList()
        val calls = mutableListOf<Pair<String, Map<String, String>>>()
        override fun post(url: String, form: Map<String, String>): Map<String, Any?> {
            calls += url to form
            return queue.removeAt(0)
        }
    }

    private val code = mapOf("device_code" to "dev123", "user_code" to "WXYZ-1234", "verification_uri" to "https://github.com/login/device", "interval" to 5, "expires_in" to 900)

    private fun flow(script: Script, slept: MutableList<Long> = mutableListOf(), clock: () -> Long = { 0L }) =
        GitHubDeviceFlow(script, { slept += it }, clock)

    @Test fun theCodeToTypeIsAskedForWithTheScopeAndTheClient() {
        val script = Script(code)
        val got = flow(script).start("client1", "public_repo")
        assertEquals(DeviceCode("dev123", "WXYZ-1234", "https://github.com/login/device", 5, 900), got)
        assertEquals("https://github.com/login/device/code", script.calls[0].first)
        assertEquals(mapOf("client_id" to "client1", "scope" to "public_repo"), script.calls[0].second)
    }

    @Test fun itPollsAtGitHubsPaceUntilTheTokenComes() {
        val script = Script(mapOf("error" to "authorization_pending"), mapOf("error" to "authorization_pending"), mapOf("access_token" to "gho_abc", "token_type" to "bearer"))
        val slept = mutableListOf<Long>()
        val token = flow(script, slept).awaitToken("client1", DeviceCode("dev123", "U", "V", 5, 900))
        assertEquals("gho_abc", token)
        assertEquals(listOf(5000L, 5000L, 5000L), slept)
        assertEquals("urn:ietf:params:oauth:grant-type:device_code", script.calls[0].second["grant_type"])
        assertEquals("dev123", script.calls[0].second["device_code"])
        assertEquals("https://github.com/login/oauth/access_token", script.calls[0].first)
    }

    @Test fun slowDownMakesItWaitLonger() {
        val script = Script(mapOf("error" to "slow_down", "interval" to 10), mapOf("error" to "slow_down"), mapOf("access_token" to "t"))
        val slept = mutableListOf<Long>()
        flow(script, slept).awaitToken("c", DeviceCode("d", "U", "V", 5, 900))
        assertEquals("GitHub's own interval when it gives one, 5 seconds more otherwise", listOf(5000L, 10_000L, 15_000L), slept)
    }

    @Test fun aRefusalAnExpiryAndADisabledApplicationAreSaidPlainly() {
        for ((error, expected) in listOf("access_denied" to "refused", "expired_token" to "expired", "device_flow_disabled" to "not enabled")) {
            try {
                flow(Script(mapOf("error" to error))).awaitToken("c", DeviceCode("d", "U", "V", 5, 900))
                fail("went through")
            } catch (e: GitHubLoginException) {
                assertTrue("${e.message}", e.message!!.contains(expected))
            }
        }
    }

    @Test fun aCodeThatRunsOutBeforeItIsTypedEnds() {
        var now = 0L
        val script = Script(*Array(10) { mapOf<String, Any?>("error" to "authorization_pending") })
        try {
            GitHubDeviceFlow(script, { now += it }, { now }).awaitToken("c", DeviceCode("d", "U", "V", 5, 20))
            fail("went through")
        } catch (e: GitHubLoginException) {
            assertTrue(e.message!!.contains("expired"))
        }
        assertEquals(4, script.calls.size)
    }

    @Test fun anErrorWhenAskingForTheCodeIsReported() {
        try {
            flow(Script(mapOf("error" to "incorrect_client_credentials"))).start("nope", "public_repo")
            fail("went through")
        } catch (e: GitHubLoginException) {
            assertTrue(e.message!!.contains("clientId"))
        }
    }
}
