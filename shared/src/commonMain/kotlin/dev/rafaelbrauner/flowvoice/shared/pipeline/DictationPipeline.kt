package dev.rafaelbrauner.flowvoice.shared.pipeline

import dev.rafaelbrauner.flowvoice.shared.dictation.DictationSessionController
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationSessionState
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow
import dev.rafaelbrauner.flowvoice.shared.dictionary.PersonalDictionary
import dev.rafaelbrauner.flowvoice.shared.insertion.TextInserter
import dev.rafaelbrauner.flowvoice.shared.insertion.TextInsertionResult
import dev.rafaelbrauner.flowvoice.shared.prefs.PreferencesStore
import dev.rafaelbrauner.flowvoice.shared.preview.LivePreview
import dev.rafaelbrauner.flowvoice.shared.preview.LivePreviewAssembler
import dev.rafaelbrauner.flowvoice.shared.proofreading.ProofreadingClient
import dev.rafaelbrauner.flowvoice.shared.proofreading.ProofreadingGuard
import dev.rafaelbrauner.flowvoice.shared.transcription.IncrementalTranscriptionController
import dev.rafaelbrauner.flowvoice.shared.transcription.OpenRouterConfig
import dev.rafaelbrauner.flowvoice.shared.transcription.SecretStore
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionClient
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionEventLog
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionSegment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class DictationPipeline(
    private val controller: DictationSessionController,
    client: TranscriptionClient,
    private val config: OpenRouterConfig,
    private val dictionary: PersonalDictionary,
    private val proofreading: ProofreadingClient,
    private val preferences: PreferencesStore,
    private val secrets: SecretStore,
    private val inserter: TextInserter,
    private val scope: CoroutineScope,
    private val eventLog: TranscriptionEventLog = TranscriptionEventLog.NoOp,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    // Texto ditado vai só para este log, nunca para eventLog nem para as linhas do Diagnóstico (P135).
    private val transcriptTextLog: TranscriptionEventLog = TranscriptionEventLog.NoOp
) {
    private val eventLines = MutableSharedFlow<String>(extraBufferCapacity = EVENT_BUFFER)
    private val transcription = IncrementalTranscriptionController(
        client = client,
        config = config,
        scope = scope,
        apiKeyProvider = { secrets.readOpenRouterKey() },
        eventLog = { event, metadata -> log(event, metadata) },
        textLog = transcriptTextLog
    )
    private val statusState = MutableStateFlow<DictationPipelineStatus>(DictationPipelineStatus.Idle)
    private val windowsState = MutableStateFlow<List<DictationWindow>>(emptyList())
    private val submittedWindows = MutableStateFlow(0)
    private val targetState = MutableStateFlow(DictationTarget.ActiveField)
    private var sessionToken = 0
    private var refusalMark: TimeMark? = null
    private var sessionApiKey: String? = null
    private var abandonedStartGuard: Job? = null
    private var sessionId = 0
    private val pipelineSessionState = MutableStateFlow(
        DictationPipelineSession(sessionId, DictationTarget.ActiveField, DictationPipelineStatus.Idle)
    )
    private val directState = MutableStateFlow(DirectInsertionProgress())
    private var directPlan = DirectInsertionPlan()

    val status: StateFlow<DictationPipelineStatus> = statusState.asStateFlow()
    val target: StateFlow<DictationTarget> = targetState.asStateFlow()
    val session: StateFlow<DictationPipelineSession> = pipelineSessionState.asStateFlow()
    val sessionState: StateFlow<DictationSessionState> = controller.state
    val segments: StateFlow<List<TranscriptionSegment>> = transcription.segments
    val sessionWindows: StateFlow<List<DictationWindow>> = windowsState.asStateFlow()
    val events: SharedFlow<String> = eventLines.asSharedFlow()
    val directInsertion: StateFlow<DirectInsertionProgress> = directState.asStateFlow()

    val capturedDurationMs: Long
        get() = controller.capturedDurationMs

    val model: String
        get() = config.model

    val proofreadingEnabled: Boolean
        get() = preferences.read().proofreadingEnabled

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            controller.windows.collect { window ->
                windowsState.value = windowsState.value + window
                transcription.submit(window)
                submittedWindows.value += 1
                log(
                    "dictation_window",
                    buildMap {
                        put("window", (window.index + 1).toString())
                        put("durationMs", window.durationMs.toString())
                        put("model", config.model)
                        put("cut", window.cut.name.lowercase())
                        window.noiseFloor?.let { put("noiseFloor", it.toString()) }
                    }
                )
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            controller.state.collect { state ->
                val active = statusState.value
                val capturing = active == DictationPipelineStatus.Starting ||
                    active == DictationPipelineStatus.Recording
                if (state is DictationSessionState.Error && capturing) {
                    publish(DictationPipelineStatus.Failed(state.message))
                    log("dictation_failed", mapOf("stage" to "capture"))
                }
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            transcription.budgetExhausted.collect { exhausted ->
                if (exhausted) stopAtRequestBudget()
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            transcription.fatalError.collect { error ->
                if (error != null) stopOnInvalidKey()
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            transcription.segments.collect { advanceDirect() }
        }
    }

    fun preview(): LivePreview {
        val assembled = LivePreviewAssembler.assemble(
            segments = segments.value,
            sessionComplete = sessionState.value is DictationSessionState.Finalized
        )
        return LivePreview(
            finalized = dictionary.apply(assembled.finalized),
            provisional = dictionary.apply(assembled.provisional)
        )
    }

    fun liveText(): LivePreview {
        val split = LiveDictationText.split(segments.value)
        return LivePreview(
            finalized = dictionary.apply(split.finalized),
            provisional = dictionary.apply(split.provisional)
        )
    }

    suspend fun start(target: DictationTarget = DictationTarget.ActiveField) {
        if (statusState.value.isBusy) return
        val token = ++sessionToken
        sessionId++
        targetState.value = target
        directPlan = DirectInsertionPlan()
        directState.value = DirectInsertionProgress(
            sessionId = sessionId,
            active = target == DictationTarget.ActiveField && !preferences.read().reviewBeforeInsert
        )
        publish(DictationPipelineStatus.Starting)
        sessionApiKey = secrets.readOpenRouterKey()?.takeIf { it.isNotBlank() }
        transcription.reset(sessionApiKey)
        windowsState.value = emptyList()
        submittedWindows.value = 0
        if (sessionApiKey == null) {
            publish(DictationPipelineStatus.Failed(INVALID_KEY_MESSAGE))
            log("dictation_failed", mapOf("stage" to "key"))
            return
        }
        if (target == DictationTarget.ActiveField) inserter.captureTarget()
        try {
            controller.start()
            if (token != sessionToken) {
                controller.cancel()
                return
            }
            if (statusState.value == DictationPipelineStatus.Starting) {
                publish(DictationPipelineStatus.Recording)
                log("dictation_started", emptyMap())
                when {
                    transcription.fatalError.value != null -> stopOnInvalidKey()
                    transcription.budgetExhausted.value -> stopAtRequestBudget()
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (token == sessionToken) {
                publish(DictationPipelineStatus.Failed(error.message ?: "erro desconhecido"))
                log("dictation_failed", mapOf("stage" to "start"))
            }
        }
    }

    private fun stopOnInvalidKey() = stopEarly("dictation_key_stop")

    private fun stopAtRequestBudget() = stopEarly("dictation_budget_stop")

    private fun stopEarly(event: String) {
        if (statusState.value != DictationPipelineStatus.Recording) return
        log(event, mapOf("target" to targetState.value.name))
        when (targetState.value) {
            DictationTarget.Note -> requestFinalize()
            DictationTarget.ActiveField -> scope.launch { finalizeForReview(captureTarget = false) }
        }
    }

    suspend fun finalize(): DictationPipelineStatus {
        if (targetState.value == DictationTarget.ActiveField) return finalizeForReview()
        if (statusState.value != DictationPipelineStatus.Recording) return statusState.value
        val token = sessionToken
        publish(DictationPipelineStatus.Transcribing)
        val outcome = try {
            val final = transcribeFinalText()
            if (token != sessionToken) {
                DictationPipelineStatus.Cancelled
            } else if (final.text.isBlank() && final.failures != null) {
                noTextFailure(final.failures)
            } else {
                dictionary.suggestFrom(final.text)
                insert(
                    text = final.text,
                    warning = final.failures?.partialMessage,
                    latencyMs = null,
                    failedWindows = final.failures?.failedCount ?: 0
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log("dictation_failed", mapOf("stage" to "finalize"))
            DictationPipelineStatus.Failed(error.message ?: "erro desconhecido")
        }
        if (token == sessionToken) {
            publish(outcome)
        }
        return outcome
    }

    suspend fun finalizeForReview(): DictationPipelineStatus = finalizeForReview(captureTarget = true)

    private suspend fun finalizeForReview(captureTarget: Boolean): DictationPipelineStatus {
        if (targetState.value == DictationTarget.Note) return finalize()
        if (isDirectSession()) return finalizeDirect()
        if (statusState.value != DictationPipelineStatus.Recording) return statusState.value
        val token = sessionToken
        val mark = timeSource.markNow()
        if (captureTarget) inserter.captureTargetIfUnknown()
        publish(DictationPipelineStatus.Transcribing)
        val outcome = try {
            val final = transcribeFinalText()
            if (token != sessionToken) {
                DictationPipelineStatus.Cancelled
            } else if (final.text.isBlank() && final.failures != null) {
                noTextFailure(final.failures)
            } else {
                dictionary.suggestFrom(final.text)
                val latencyMs = mark.elapsedNow().inWholeMilliseconds
                log(
                    "dictation_ready",
                    mapOf(
                        "chars" to final.text.length.toString(),
                        "latencyMs" to latencyMs.toString(),
                        "failedWindows" to (final.failures?.failedCount ?: 0).toString()
                    )
                )
                DictationPipelineStatus.Ready(final.text, final.failures?.partialMessage, latencyMs)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log("dictation_failed", mapOf("stage" to "finalize"))
            DictationPipelineStatus.Failed(error.message ?: "erro desconhecido")
        }
        if (token == sessionToken) {
            publish(outcome)
        }
        return outcome
    }

    fun insertReady(): DictationPipelineStatus {
        if (isDirectSession()) return insertPending()
        val ready = statusState.value as? DictationPipelineStatus.Ready ?: return statusState.value
        if (ready.refusal != null && refusalMark?.let { it.elapsedNow() < RETRY_GUARD } == true) {
            log("dictation_insert_retry_ignored", emptyMap())
            return ready
        }
        if (ready.text.isNotBlank() && targetState.value == DictationTarget.ActiveField) {
            if (ready.refusal == null) inserter.captureTargetIfUnknown() else inserter.captureTarget()
            val insertion = inserter.insert(ready.text)
            if (!insertion.success) {
                refusalMark = timeSource.markNow()
                log("dictation_insert_refused", mapOf("chars" to ready.text.length.toString()))
                val retry = ready.copy(refusal = insertion.message)
                publish(retry)
                return retry
            }
            val outcome = completed(ready.text, insertion, ready.warning, ready.latencyMs, failedWindows = null)
            publish(outcome)
            return outcome
        }
        val outcome = insert(ready.text, ready.warning, ready.latencyMs)
        publish(outcome)
        return outcome
    }

    suspend fun cancel() {
        if (!statusState.value.isBusy) return
        sessionToken++
        transcription.cancel()
        try {
            controller.cancel()
        } finally {
            publish(DictationPipelineStatus.Cancelled)
            // O que já foi digitado fica no campo; o pendente é descartado.
            directState.value = directState.value.copy(pending = "", pausedReason = null)
            log("dictation_cancelled", emptyMap())
        }
    }

    fun requestStart(target: DictationTarget): Job = scope.launch { start(target) }

    fun requestFinalize(): Job = scope.launch { finalize() }

    fun requestFinalizeForReview(): Job = scope.launch { finalizeForReview() }

    fun requestInsertReady(): Job = scope.launch { insertReady() }

    fun requestCancel(): Job = scope.launch { cancel() }

    fun requestInsertPending(): Job = scope.launch { insertPending() }

    // "Inserir aqui" da sessão direta (P139): toque do usuário, então recaptura o destino (app em foco),
    // escreve o pendente e retoma a digitação sem toque nesse campo. Toque até 1 s depois da pausa é
    // ignorado, como na P113.
    fun insertPending(): DictationPipelineStatus {
        val status = statusState.value
        val progress = directState.value
        if (!isDirectSession() || progress.pending.isBlank()) return status
        if (status != DictationPipelineStatus.Recording &&
            status != DictationPipelineStatus.Transcribing &&
            status !is DictationPipelineStatus.Ready
        ) {
            return status
        }
        if (refusalMark?.let { it.elapsedNow() < RETRY_GUARD } == true) {
            log("dictation_insert_retry_ignored", emptyMap())
            return status
        }
        inserter.captureTarget()
        val insertion = inserter.insert(progress.pending)
        if (!insertion.success) {
            refusalMark = timeSource.markNow()
            log("dictation_insert_refused", mapOf("chars" to progress.pending.length.toString()))
            directState.value = progress.copy(pausedReason = insertion.message)
            if (status is DictationPipelineStatus.Ready) {
                val retry = status.copy(refusal = insertion.message)
                publish(retry)
                return retry
            }
            return status
        }
        log("dictation_direct_resumed", mapOf("chars" to progress.pending.length.toString()))
        val resumed = progress.copy(typed = progress.typed + progress.pending, pending = "", pausedReason = null)
        directState.value = resumed
        if (status is DictationPipelineStatus.Ready) {
            val outcome = completed(resumed.typed, directDelivery(resumed.typed), status.warning, status.latencyMs, failedWindows = null)
            publish(outcome)
            return outcome
        }
        advanceDirect()
        return statusState.value
    }

    private fun isDirectSession(): Boolean = directState.value.let { it.active && it.sessionId == sessionId }

    // Digita as janelas resolvidas, em ordem, enquanto a sessão direta captura ou finaliza. Depois de
    // qualquer recusa, nada mais é digitado sem toque: o resto vai para o pendente, mesmo que o foco
    // volte ao app de origem (a volta pode ser noutra conversa).
    private fun advanceDirect() {
        val progress = directState.value
        if (!isDirectSession()) return
        when (statusState.value) {
            DictationPipelineStatus.Starting,
            DictationPipelineStatus.Recording,
            DictationPipelineStatus.Transcribing -> Unit
            else -> return
        }
        val segments = transcription.segments.value
        val step = DirectInsertionPlanner.advance(directPlan, segments)
        directPlan = step.plan
        var next = progress
        step.pieces.forEach { piece ->
            val text = piece.separator + dictionary.apply(piece.text)
            val window = (piece.windowIndex + 1).toString()
            next = if (next.paused) {
                next.copy(pending = next.pending + text)
            } else {
                val insertion = inserter.insertWithoutTap(text)
                if (insertion.success) {
                    log("dictation_direct_inserted", mapOf("window" to window, "chars" to text.length.toString()))
                    next.copy(typed = next.typed + text)
                } else {
                    refusalMark = timeSource.markNow()
                    log("dictation_direct_paused", mapOf("window" to window, "route" to insertion.route))
                    next.copy(pending = next.pending + text, pausedReason = insertion.message)
                }
            }
        }
        val warning = TranscriptionFailureSummary.from(segments, controller.emittedWindowCount)?.partialMessage
        next = next.copy(warning = warning)
        if (next != progress) directState.value = next
    }

    private suspend fun finalizeDirect(): DictationPipelineStatus {
        if (statusState.value != DictationPipelineStatus.Recording) return statusState.value
        val token = sessionToken
        val mark = timeSource.markNow()
        publish(DictationPipelineStatus.Transcribing)
        val outcome = try {
            controller.finalize()
            submittedWindows.first { it >= controller.emittedWindowCount }
            transcription.awaitIdle()
            if (token != sessionToken) {
                DictationPipelineStatus.Cancelled
            } else {
                directOutcome(mark.elapsedNow().inWholeMilliseconds)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log("dictation_failed", mapOf("stage" to "finalize"))
            DictationPipelineStatus.Failed(error.message ?: "erro desconhecido")
        }
        if (token == sessionToken) {
            publish(outcome)
        }
        return outcome
    }

    private fun directOutcome(latencyMs: Long): DictationPipelineStatus {
        advanceDirect()
        val progress = directState.value
        val failures = TranscriptionFailureSummary.from(transcription.segments.value, controller.emittedWindowCount)
        val dictated = progress.typed + progress.pending
        if (dictated.isNotBlank()) dictionary.suggestFrom(dictated)
        return when {
            progress.pending.isNotBlank() -> {
                log(
                    "dictation_ready",
                    mapOf(
                        "chars" to progress.pending.length.toString(),
                        "latencyMs" to latencyMs.toString(),
                        "failedWindows" to (failures?.failedCount ?: 0).toString()
                    )
                )
                DictationPipelineStatus.Ready(progress.pending, failures?.partialMessage, latencyMs, progress.pausedReason)
            }
            progress.typed.isBlank() && failures != null -> noTextFailure(failures)
            else -> completed(
                progress.typed,
                directDelivery(progress.typed),
                failures?.partialMessage,
                latencyMs,
                failedWindows = failures?.failedCount ?: 0
            )
        }
    }

    private fun directDelivery(typed: String): TextInsertionResult =
        if (typed.isBlank()) {
            TextInsertionResult(success = false, route = DIRECT_ROUTE, message = "sem texto para inserir")
        } else {
            TextInsertionResult(success = true, route = DIRECT_ROUTE, message = "digitado no campo")
        }

    // Depois do aviso de timeout do microfone do Início, a sessão pedida por aquele toque não pode
    // seguir gravando: se abrir em até graceMs, é cancelada. Roda no escopo do pipeline, que não
    // morre com a tela (rotação, Voltar), e um novo pedido de início descarta a guarda (P133).
    fun abandonStart(previousSessionId: Int, graceMs: Long) {
        abandonedStartGuard?.cancel()
        abandonedStartGuard = scope.launch {
            val late = withTimeoutOrNull(graceMs) { session.first { it.id > previousSessionId } } ?: return@launch
            if (late.id == previousSessionId + 1 && late.status.isBusy) cancel()
        }
    }

    fun clearAbandonedStart() {
        abandonedStartGuard?.cancel()
        abandonedStartGuard = null
    }

    private suspend fun transcribeFinalText(): FinalText {
        controller.finalize()
        submittedWindows.first { it >= controller.emittedWindowCount }
        transcription.awaitIdle()
        val failures = TranscriptionFailureSummary.from(
            segments = transcription.segments.value,
            totalWindows = controller.emittedWindowCount
        )
        return FinalText(reviseFinalText(), failures)
    }

    private fun noTextFailure(failures: TranscriptionFailureSummary): DictationPipelineStatus {
        log(
            "dictation_failed",
            mapOf("stage" to "transcription", "failedWindows" to failures.failedCount.toString())
        )
        return DictationPipelineStatus.Failed(failures.noTextMessage)
    }

    private fun insert(
        text: String,
        warning: String?,
        latencyMs: Long?,
        failedWindows: Int? = null
    ): DictationPipelineStatus {
        val insertion = if (text.isBlank()) {
            TextInsertionResult(success = false, route = "pipeline", message = "sem texto para inserir")
        } else {
            NOTE_DELIVERY
        }
        return completed(text, insertion, warning, latencyMs, failedWindows)
    }

    private fun completed(
        text: String,
        insertion: TextInsertionResult,
        warning: String?,
        latencyMs: Long?,
        failedWindows: Int?
    ): DictationPipelineStatus.Completed {
        log(
            "dictation_finalized",
            buildMap {
                put("chars", text.length.toString())
                put("inserted", insertion.success.toString())
                if (failedWindows != null) put("failedWindows", failedWindows.toString())
            }
        )
        return DictationPipelineStatus.Completed(text, insertion, warning, latencyMs)
    }

    private suspend fun reviseFinalText(): String {
        val assembled = LivePreviewAssembler.assemble(
            segments = transcription.segments.value,
            sessionComplete = true
        )
        val revised = dictionary.apply(assembled.finalized)
        val prefs = preferences.read()
        if (!prefs.proofreadingEnabled || revised.isBlank() || transcription.fatalError.value != null) return revised
        val apiKey = sessionApiKey ?: return revised
        return try {
            val proofread = proofreading.proofread(revised, apiKey, prefs.proofreadingModel)
            transcriptTextLog.log("proofreading_input", mapOf("text" to revised))
            transcriptTextLog.log("proofreading_output", mapOf("text" to proofread))
            if (!ProofreadingGuard.accepts(revised, proofread)) {
                log(
                    "proofreading_rejected",
                    mapOf("inputChars" to revised.length.toString(), "outputChars" to proofread.length.toString())
                )
                revised
            } else {
                dictionary.apply(proofread).also {
                    log("proofreading_applied", mapOf("inputChars" to revised.length.toString(), "chars" to it.length.toString()))
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            log("proofreading_unavailable", emptyMap())
            revised
        }
    }

    private fun publish(status: DictationPipelineStatus) {
        statusState.value = status
        pipelineSessionState.value = DictationPipelineSession(sessionId, targetState.value, status)
    }

    private fun log(event: String, metadata: Map<String, String>) {
        eventLog.log(event, metadata)
        eventLines.tryEmit(
            buildString {
                append(event)
                metadata.forEach { (key, value) ->
                    append(' ')
                    append(key)
                    append('=')
                    append(value)
                }
            }
        )
    }

    private class FinalText(val text: String, val failures: TranscriptionFailureSummary?)

    private companion object {
        const val EVENT_BUFFER = 64
        const val INVALID_KEY_MESSAGE = "chave OpenRouter ausente ou inválida"
        const val DIRECT_ROUTE = "direto"
        val RETRY_GUARD = 1.seconds
        val NOTE_DELIVERY = TextInsertionResult(success = false, route = "nota", message = "texto entregue à nota")
    }
}
