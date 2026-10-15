package com.aiwa.store
import android.content.Context
import com.aiwa.bridge.Dictation
import com.aiwa.bridge.LocalClaudeBridge
import com.aiwa.bridge.VoiceCommand
import com.aiwa.bridge.VoiceModel
import com.aiwa.bridge.VoiceSession
import com.aiwa.bridge.describeCommand
import com.aiwa.bridge.PillReference
import com.aiwa.bridge.Pills
import com.aiwa.bridge.parseDictation
import com.aiwa.bridge.recapSentence
import com.aiwa.bridge.statusReport
import androidx.glance.appwidget.updateAll
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

// "Dis à l'agent …", "demande à l'agent …", "agent, …": what follows goes to the agent, not to the working session.
private val AGENT_REGEX = Regex("""^\s*(?:(?:dis|demande|répond\w*|parle)\s+(?:à|a)\s+l['’]?\s*|l['’]\s*)?agent\b[,:]?\s*(.+)$""", RegexOption.IGNORE_CASE)

/**
 * One dictation, from the first word to the message sent and the settings applied, with nothing of a screen in it: the window of the
 * widget's mic (DictateActivity) and the always-on listening service (WakeWordService) both run it and show what they want of it.
 * Everything here runs on the main thread (the speech recognizer wants it).
 *
 *  - what is said is text for Claude until "instruction Aiwa"; after it (or from the first word when the wake word was already heard:
 *    startInInstructions) the keywords of the pills are commands (see VoiceCommands.kt in the bridge);
 *  - "c'est bon vas-y" ends it: the commands are applied, the repository only after the phone has asked aloud and heard "c'est bon vas-y"
 *    again, then the message goes to Claude with the new settings.
 */
class VoiceDictation(
    context: Context,
    private val startInInstructions: Boolean = false,
    // Hands-free (woken by a phrase, not by a touch): where things stand is read first, and before anything is applied or sent the phone reads back all of it
    // and waits for "c'est bon vas-y", every time: nobody looks at a screen to catch a mistake or a false waking.
    private val reportFirst: Boolean = false,
    private val confirmAll: Boolean = false,
    private val onPartial: (String) -> Unit = {},
    private val onUnderstood: (String) -> Unit = {},
    private val onQuestion: (String?) -> Unit = {},
    private val onFinished: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val voiceContext = context
    private var listener: StopPhraseListener? = null
    private var confirmListener: ConfirmListener? = null
    private var work: Job? = null
    private var repoNames: List<String> = emptyList()
    private var lastPartial = ""
    private var ended = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** The pills as they are now, for the sentence that reads everything back before a hands-free request leaves. */
    private fun currentPills(): Pills {
        val state = AiwaRepository.state.value
        return Pills(
            repo = state.repo,
            model = if (state.model == null) "automatique" else modelLabel(state.model),
            effort = PillReference.effortLabel(state.effort),
            pushMain = state.pushMain,
            deploy = state.deploy,
            videoQuality = PillReference.videoQualityLabel(state.videoQuality),
            session = if (state.cloudSessionId != null) state.session else null,
        )
    }

    private fun voiceModels() = MODEL_CHOICES.map { VoiceModel(it.id, it.label) }
    private fun voiceSessions() = AiwaRepository.state.value.cloudSessions.map { VoiceSession(it.id, it.title) }

    // What the words so far mean to Aiwa, said as the person speaks so that a mistake shows before it is applied.
    private fun understoodLine(text: String): String {
        val dictation = parseDictation(text, voiceModels(), voiceSessions(), repoNames, startInInstructions)
        if (!dictation.instructions) return ""
        val said = dictation.commands.map { describeCommand(it) } + dictation.problems.map { "⚠ $it" }
        return "Instructions Aiwa : " + if (said.isEmpty()) "rien compris pour l'instant" else said.joinToString(" · ")
    }

    fun start() {
        // The repositories a spoken name can be one of: read once, in the background.
        CoroutineScope(Dispatchers.IO).launch {
            repoNames = try { LocalClaudeBridge().githubRepos().map { it.name } } catch (err: Exception) { emptyList() }
        }
        if (reportFirst) {
            // The voice speaks first, then the microphone opens (it must not hear the voice).
            work = scope.launch {
                readReport(LocalClaudeBridge())
                beginListening()
            }
        } else {
            beginListening()
        }
    }

    private fun beginListening() {
        if (ended) return
        listener = StopPhraseListener(
            context = voiceContext,
            onPartial = { lastPartial = it; onPartial(it); onUnderstood(understoodLine(it)) },
            onFinalText = { text -> handleFinal(text) },
            onGiveUp = { end() },
            onCancelPhrase = { toastOnMain(appContext, "Annulé : rien n'est appliqué ni envoyé."); cancel() },
            proposalWaiting = { AiwaRepository.state.value.agent?.proposal != null },
            onSendProposal = { work = scope.launch { sendProposal() } },
        )
        listener?.start()
    }

    /** The person gave up: nothing is applied, nothing is sent. */
    fun cancel() {
        listener?.cancel()
        confirmListener?.cancel()
        work?.cancel()
        end()
    }

    /** "Envoyer maintenant": what was heard so far is taken as said, the stop phrase being the one thing the recognizer may have mangled. */
    fun sendNow() {
        listener?.cancel()
        if (lastPartial.isNotBlank()) handleFinal(lastPartial) else end()
    }

    private fun end() {
        if (ended) return
        ended = true
        listener?.cancel()
        confirmListener?.cancel()
        onQuestion(null)
        onFinished()
    }

    /** The settings the agent asked for, written as a spoken instruction (« instruction aiwa modèle opus push main »): understood by the same parser, applied by the same code. The read-back was done by the caller. */
    suspend fun applyInstruction(text: String) {
        if (repoNames.isEmpty()) repoNames = withContext(Dispatchers.IO) { try { LocalClaudeBridge().githubRepos().map { it.name } } catch (err: Exception) { emptyList() } }
        runInstructions(parseDictation(text, voiceModels(), voiceSessions(), repoNames, true))
    }

    // "Vas-y dis-lui": the phone reads back where the proposal of the agent goes and what it says, and only "c'est bon vas-y" sends it.
    private suspend fun sendProposal() {
        val bridge = LocalClaudeBridge()
        val proposal = AiwaRepository.state.value.agent?.proposal
        if (proposal == null) { end(); return }
        if (!confirmSentence(proposal.readBack)) {
            toastOnMain(appContext, "Pas confirmé : rien n'est envoyé. La proposition attend.")
            end()
            return
        }
        val error = try { bridge.agent("confirm", mapOf("id" to proposal.id)) } catch (err: Exception) { err.message ?: "?" }
        toastOnMain(appContext, if (error == null) "Envoyé à ${proposal.sessionTitle}." else "Pas envoyé : $error")
        try { BackendSync.refresh(bridge) } catch (err: Exception) { }
        AiwaWidget().updateAll(appContext)
        end()
    }

    private fun handleFinal(text: String) {
        val toAgent = AGENT_REGEX.find(text)?.groupValues?.get(1)?.trim().orEmpty()
        if (toAgent.isNotBlank()) {
            CoroutineScope(Dispatchers.Default).launch {
                val error = try { LocalClaudeBridge().agent("ask", mapOf("text" to toAgent)) } catch (err: Exception) { err.message ?: "?" }
                if (error != null) toastOnMain(appContext, "L'agent n'a pas reçu : $error")
            }
            end()
            return
        }
        val dictation = parseDictation(text, voiceModels(), voiceSessions(), repoNames, startInInstructions)
        if (!dictation.instructions) {
            sendPlain(text)
            return
        }
        work = scope.launch {
            // With the person's own API key (optional), what the keyword parser did not understand is read by Claude: the settings it finds are applied as spoken instructions, the rest goes on as the message.
            if (dictation.problems.isNotEmpty() && AiwaRepository.state.value.agent?.llm == "ready") {
                val read = try { LocalClaudeBridge().llmCommands(text) } catch (err: Exception) { null }
                if (read != null && read.first.isNotBlank()) {
                    if (repoNames.isEmpty()) repoNames = withContext(Dispatchers.IO) { try { LocalClaudeBridge().githubRepos().map { it.name } } catch (err: Exception) { emptyList() } }
                    val understood = parseDictation(read.first, voiceModels(), voiceSessions(), repoNames, true)
                    runInstructions(understood.copy(message = read.second))
                    return@launch
                }
            }
            val wantsRepo = dictation.commands.any { it is VoiceCommand.Repo }
            if (wantsRepo && repoNames.isEmpty()) repoNames = withContext(Dispatchers.IO) { try { LocalClaudeBridge().githubRepos().map { it.name } } catch (err: Exception) { emptyList() } }
            // Said again with the list of repositories, if it was not there yet when the words were heard.
            runInstructions(if (wantsRepo || dictation.problems.isNotEmpty()) parseDictation(text, voiceModels(), voiceSessions(), repoNames, startInInstructions) else dictation)
        }
    }

    private fun sendPlain(text: String) {
        if (text.isNotBlank()) {
            CoroutineScope(Dispatchers.Default).launch {
                sendAndTrack(appContext, LocalClaudeBridge(), text, toastErrors = true)
                AiwaWidget().updateAll(appContext)
            }
        }
        end()
    }

    // Applied at the end, in an order that makes sense (the session, then the repository it works on, then the rest), then the message.
    private suspend fun runInstructions(heard: Dictation) {
        val bridge = LocalClaudeBridge()
        // The report was read when the dictation began: it is not read a second time because "t'en es où" was said again.
        val dictation = if (reportFirst) heard.copy(commands = heard.commands.filter { it !is VoiceCommand.Report }) else heard
        // A stop is done at once and never waits for a confirmation: stopping loses nothing, and every second counts.
        if (dictation.commands.any { it is VoiceCommand.StopClaude }) stopClaude(appContext)
        var repoConfirmed = false
        if (confirmAll) {
            val recap = recapSentence(dictation, currentPills())
            if (recap != null) {
                if (!confirmSentence(recap)) {
                    // What was said is not lost, and nothing was applied nor sent.
                    if (dictation.message.isNotBlank()) copyToClipboard(appContext, dictation.message)
                    toastOnMain(appContext, "Pas confirmé : rien n'est appliqué ni envoyé." + if (dictation.message.isNotBlank()) " Le message est copié." else "")
                    end()
                    return
                }
                repoConfirmed = true   // the repository was part of what was read back
            }
        }
        val done = mutableListOf<String>()
        var repoRefused = false
        var report = false
        val ordered = dictation.commands.sortedBy {
            when (it) {
                is VoiceCommand.NewSession, is VoiceCommand.SelectSession -> 0
                is VoiceCommand.Repo -> 1
                is VoiceCommand.Model -> 2
                is VoiceCommand.Effort -> 3
                is VoiceCommand.PushMain -> 4
                is VoiceCommand.Deploy -> 5
                is VoiceCommand.VideoQuality -> 6
                is VoiceCommand.Report -> 7
                is VoiceCommand.StopClaude -> 8
                is VoiceCommand.CastStop -> 9
                is VoiceCommand.Cast -> 10
            }
        }
        for (command in ordered) {
            when (command) {
                is VoiceCommand.NewSession -> switchCloud(appContext, bridge, "new")
                is VoiceCommand.SelectSession -> switchCloud(appContext, bridge, command.id)
                is VoiceCommand.Model -> switchModel(appContext, bridge, command.id)
                is VoiceCommand.Effort -> switchEffort(appContext, bridge, command.id)
                is VoiceCommand.VideoQuality -> switchOptions(appContext, bridge, videoQuality = command.id)
                is VoiceCommand.PushMain -> switchOptions(appContext, bridge, pushMain = command.direct)
                is VoiceCommand.Deploy -> switchOptions(appContext, bridge, deploy = command.mode)
                is VoiceCommand.Report -> report = true
                is VoiceCommand.StopClaude -> {}   // done above
                is VoiceCommand.CastStop -> {
                    CastService.stop(appContext)
                    try { bridge.castClear() } catch (err: Exception) { }
                }
                is VoiceCommand.Cast -> {
                    if (dictation.message.isNotBlank()) {
                        // With a message: the video does not exist yet. The wish waits at the backend for the video Claude is going to make.
                        try { bridge.castPending(command.tv) } catch (err: Exception) { toastOnMain(appContext, "Envoi à la TV non enregistré : ${err.message ?: "?"}") }
                    } else {
                        // Alone: the latest video goes at once
                        val latest = try { bridge.videos().videos.firstOrNull() } catch (err: Exception) { null }
                        if (latest == null) toastOnMain(appContext, "Aucune vidéo à envoyer à la TV.")
                        else try { CastService.start(appContext, latest.url, command.tv, latest.name) } catch (err: Exception) { toastOnMain(appContext, "Envoi à la TV impossible : ${err.message ?: "?"}") }
                    }
                }
                is VoiceCommand.Repo -> {
                    // A repository is never changed on a guess: the phone asks aloud and the answer is the same phrase.
                    if (repoConfirmed || confirmRepo(command.name)) {
                        val problem = switchRepo(appContext, bridge, command.name)
                        if (problem != null) {
                            toastOnMain(appContext, "⚠ Le dépôt n'a PAS changé : $problem")
                            repoRefused = true
                            continue
                        }
                    } else {
                        repoRefused = true
                        continue
                    }
                }
            }
            if (command !is VoiceCommand.Report && command !is VoiceCommand.StopClaude) done.add(describeCommand(command))
        }
        if (done.isNotEmpty()) toastOnMain(appContext, "Aiwa : " + done.joinToString(" · "))
        if (dictation.problems.isNotEmpty()) toastOnMain(appContext, "⚠ " + dictation.problems.joinToString(" · "))
        if (repoRefused) toastOnMain(appContext, "Dépôt non confirmé : il ne change pas.")
        val message = dictation.message
        if (message.isNotBlank()) {
            if (repoRefused) {
                // The message was meant for the other repository: it does not go to this one. It is not lost.
                copyToClipboard(appContext, message)
                toastOnMain(appContext, "Le message n'est pas parti (dépôt non confirmé) : il est copié.")
            } else {
                sendAndTrack(appContext, bridge, message, toastErrors = true)
            }
        }
        if (report) readReport(bridge)
        AiwaWidget().updateAll(appContext)
        end()
    }

    // "T'es sur quoi ?": the pills as they are now (after what was just applied), read aloud.
    private suspend fun readReport(bridge: LocalClaudeBridge) {
        try { BackendSync.refresh(bridge) } catch (err: Exception) { /* what is known is said, with the warning below */ }
        val state = AiwaRepository.state.value
        val model = if (state.model == null) "automatique" else modelLabel(state.model)
        val sentence = statusReport(
            state.repo, model, state.pushMain, state.deploy, if (state.cloudSessionId != null) state.session else null, state.backend == "up",
            PillReference.effortLabel(state.effort), PillReference.videoQualityLabel(state.videoQuality),
        )
        onQuestion(sentence)
        Speaker.speakAndWait(voiceContext, sentence)
        onQuestion(null)
    }

    // The phone says the question, then listens for "c'est bon vas-y" and nothing else: silence or any other words are a no.
    private suspend fun confirmRepo(name: String): Boolean {
        val spoken = name.substringAfter('/').replace('_', ' ').replace('-', ' ')
        return confirmSentence("Dépôt $spoken. Tu confirmes ?")
    }

    private suspend fun confirmSentence(sentence: String): Boolean {
        onQuestion("$sentence Dis « c'est bon vas-y » pour confirmer")
        val asked = Speaker.speakAndWait(voiceContext, "$sentence Dis : c'est bon, vas-y.")
        if (!asked) {
            onQuestion(null)
            toastOnMain(appContext, "Pas de voix sur ce téléphone pour poser la question : rien ne change.")
            return false
        }
        delay(400)   // the end of the voice must not be heard as the answer
        val yes = suspendCancellableCoroutine<Boolean> { cont ->
            val confirm = ConfirmListener(voiceContext) { answer -> if (cont.isActive) cont.resume(answer) }
            confirmListener = confirm
            cont.invokeOnCancellation { confirm.cancel() }
            confirm.start()
        }
        onQuestion(null)
        return yes
    }
}
