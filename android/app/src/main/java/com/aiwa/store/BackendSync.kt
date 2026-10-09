package com.aiwa.store

import android.content.Context
import com.aiwa.bridge.BackendStatus
import com.aiwa.bridge.BackendVerdict
import com.aiwa.bridge.ClaudeBridge
import com.aiwa.bridge.backendVerdict
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong

/**
 * The single place that copies the backend's real state (current cloud
 * session, model, session list) into AiwaRepository. Reported live: the
 * widget and the app kept disagreeing about the current session. Every
 * caller now goes through here instead of each writing its own guess.
 *
 * Overlapping refreshes can finish out of order; the one that STARTED
 * last always wins, so a slow, older answer can never overwrite a newer.
 */
object BackendSync {
    private val started = AtomicLong(0)
    private var applied = 0L
    private val lock = Any()

    suspend fun refresh(bridge: ClaudeBridge) {
        val generation = started.incrementAndGet()
        val status = try {
            bridge.status()
        } catch (err: Exception) {
            if (isBackendUnreachable(err)) markBackendDown()
            return
        }
        val fetched = try { bridge.listCloudSessions() } catch (err: Exception) { null }
        // Copied out: a property of a class from another module can't be
        // smart-cast.
        val cloudId = status.cloudSession
        synchronized(lock) {
            if (generation < applied) return
            applied = generation
            AiwaRepository.update { current ->
                val sessions = fetched ?: current.cloudSessions
                val title = sessions.find { it.id == cloudId }?.title?.take(30)
                current.copy(
                    // The backend is the one that knows whether a message is on its way: the app that sent it may have been left, or restarted.
                    // A send this process is running itself stays WORKING (the backend may not have taken it yet).
                    status = when {
                        status.sending || SendTracker.inFlight.get() -> AiwaState.Status.WORKING
                        current.status == AiwaState.Status.WORKING -> AiwaState.Status.READY
                        else -> current.status
                    },
                    sendingNote = if (status.sending) status.sendingNote else null,
                    sendingSince = when {
                        status.sending -> status.sendingSince ?: current.sendingSince.takeIf { it > 0 } ?: System.currentTimeMillis()
                        SendTracker.inFlight.get() -> current.sendingSince
                        else -> 0L
                    },
                    backend = "up",
                    backendStarts = 0,
                    backendMissing = null,
                    session = title ?: cloudId?.take(12) ?: "Nouvelle session",
                    cloudSessionId = cloudId,
                    lastSessionId = status.lastSession,
                    cloudSessions = sessions,
                    model = status.model,
                    backendVersion = status.version,
                    repo = status.repo,
                    extraRepos = status.extraRepos,
                    pushMain = status.pushMain,
                    deploy = status.deploy,
                    extra = status.extra,
                    waiting = status.waiting,
                    alertAt = status.alertLast,
                    effort = status.effort,
                    siteUrl = status.site.url,
                    siteState = status.site.state,
                    siteKind = status.site.kind,
                    siteTermux = status.site.termux,
                    ciState = status.ci?.state,
                    ciUrl = status.ci?.url,
                    ciFresh = status.ci?.fresh ?: false,
                    ciDetail = status.ci?.detail,
                    githubError = status.githubError,
                    repoAccessMissing = status.repoAccessMissing,
                    sourceRepos = status.sourceRepos,
                    githubAppUrl = status.githubAppUrl,
                    claudeLogin = status.claudeLogin,
                    relayCloud = status.relayCloud,
                    sentApp = status.sentApp,
                    ask = status.ask,
                )
            }
        }
        AiwaRepository.persist()
    }

    // Down — unless Termux was asked to start it a moment ago (then it is
    // "starting", for at most a minute). A backend that Termux was asked to start
    // twice without it ever answering is not slow, it is not installed: "missing"
    // stops the restarts (KeepAliveService only restarts a "down" one) and says what to do.
    private fun markBackendDown() {
        AiwaRepository.update {
            when {
                it.backend == "missing" -> it
                it.backend == "starting" && System.currentTimeMillis() - it.backendStartedAt < 60_000 -> it
                backendVerdict(termuxInstalled = true, startsWithoutAnswer = it.backendStarts) == BackendVerdict.NOT_INSTALLED ->
                    it.copy(backend = "missing", backendMissing = "install")
                else -> it.copy(backend = "down")
            }
        }
    }
}

/**
 * A widget tap doesn't go through the app, so the backend may simply not be
 * running (Termux was stopped): a picker then showed an empty list and told
 * the user to open Aiwa — which starts it. Starts it here (Termux
 * RUN_COMMAND) and waits for it, so the picker shows real data. Returns
 * whether the backend answers.
 */
suspend fun ensureBackend(context: Context, bridge: ClaudeBridge): Boolean {
    try {
        bridge.status()
        return true
    } catch (err: Exception) {
        if (!isBackendUnreachable(err)) return true
    }
    AiwaRepository.update { if (it.backend == "missing") it else it.copy(backend = "down") }
    startAiwaBackendViaTermux(context)
    return awaitBackendStatus(bridge, 30_000) != null
}

/**
 * Polls until the backend runs AT LEAST this version, or gives up. When an
 * update replaces the server, the old one still answers for a few seconds
 * first: that must not be taken for "outdated" (which used to trigger a
 * second, useless restart).
 */
suspend fun awaitBackendVersion(bridge: ClaudeBridge, expected: Int, timeoutMs: Long): BackendStatus? {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        try {
            val status = bridge.status()
            if (status.version >= expected) return status
        } catch (err: Exception) {
            // not up yet
        }
        delay(1500)
    }
    return null
}

/** Polls until the backend answers (it may be starting up), or gives up. */
suspend fun awaitBackendStatus(bridge: ClaudeBridge, timeoutMs: Long): BackendStatus? {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        try {
            return bridge.status()
        } catch (err: Exception) {
            delay(1500)
        }
    }
    return null
}
