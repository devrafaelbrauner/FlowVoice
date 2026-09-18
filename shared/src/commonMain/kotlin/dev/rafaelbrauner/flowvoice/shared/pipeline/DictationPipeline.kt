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
import dev.rafaelbrauner.flowvoice.shared.proofreading.ProofreadingMerge
import dev.rafaelbrauner.flowvoice.shared.transcription.IncrementalTranscriptionController
import dev.rafaelbrauner.flowvoice.shared.transcription.OpenRouterConfig
import dev.rafaelbrauner.flowvoice.shared.transcription.SecretStore
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionClient
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionEventLog
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionModels
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
        modelProvider = { TranscriptionModels.selected(preferences, config) },
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
        get() = TranscriptionModels.selected(preferences, config)

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
                        put("model", TranscriptionModels.selected(preferences, config))
                        put("cut", window.cut.name.lowercase())
                        window.noiseFloor?.let { put("noiseFloor", it.toString()) }
                        // Fala medida na janela (P151): é o número que diz, no aparelho, por que a
                        // janela foi ou não à rede — e se a trava encostou em fala baixa.
                        window.voicedMs?.let { put("voicedMs", it.toString()) }
                        // Bloco mais alto do áudio próprio (P153): é por ele que se compara esta
                        // janela com outra que já voltou vazia.
                        window.peakLevel?.let { put("peak", it.toString()) }
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
            if (token != sessionToken || statusState.value == DictationPipelineStatus.Cancelled) {
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
        if (token == sessionToken && statusState.value != DictationPipelineStatus.Cancelled) {
            publish(outcome)
        }
        return if (statusState.value == DictationPipelineStatus.Cancelled) {
            DictationPipelineStatus.Cancelled
        } else {
            outcome
        }
    }

    fun insertReady(): DictationPipelineStatus {
        if (isDirectSession()) return insertPending()
        if (statusState.value == DictationPipelineStatus.Cancelled) return statusState.value
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
        // "Inserir aqui" pode ter escrito noutro campo: o próximo pedaço não apaga nada (P144).
        directPlan = directPlan.copy(contiguous = false)
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
        var next = progress
        // Deixa de valer assim que um pedaço não entra direto no campo: o que está antes do cursor
        // passa a ser texto do usuário, e apagá-lo seria apagar o que não é nosso (P144).
        var contiguous = true
        step.pieces.forEach { piece ->
            // Vocabulário do usuário (P145): o trecho é consertado aqui, antes de ir ao campo, e por
            // isso a conta do que o app escreveu (P148) já nasce com a correção dentro. O log diz que
            // houve troca e quanto ficou, nunca o texto.
            val corrected = dictionary.apply(piece.text)
            val text = piece.separator + corrected
            val window = (piece.windowIndex + 1).toString()
            if (corrected != piece.text) {
                log(
                    "dictation_vocabulary_applied",
                    mapOf("window" to window, "chars" to corrected.length.toString())
                )
            }
            next = if (next.paused) {
                // O pendente ainda não está no campo: o ponto a tirar está nele mesmo. Com o pendente
                // vazio, o ponto está no campo, de antes da pausa, e não se toca nele.
                val erasable = piece.deleteBefore > 0 && next.pending.length >= piece.deleteBefore
                val head = if (erasable) next.pending.dropLast(piece.deleteBefore) else next.pending
                contiguous = false
                next.copy(pending = head + text)
            } else {
                // O campo é a verdade antes e depois de escrever (P142): a composição do teclado pode
                // comer o apagar da P144 ou o espaço da emenda, e nenhuma das duas coisas dá erro.
                val limit = DirectFieldWrite.readLimit(piece.deleteBefore, text)
                val before = inserter.readBeforeCursor(limit)
                val erase = DirectFieldWrite.erase(before, next.typed, piece.deleteBefore)
                if (erase < piece.deleteBefore) {
                    log(
                        "dictation_erase_skipped",
                        mapOf("window" to window, "motivo" to DirectFieldWrite.REASON_UNCONFIRMED)
                    )
                }
                val insertion = inserter.insertWithoutTap(text, erase)
                if (insertion.success) {
                    log(
                        "dictation_direct_inserted",
                        buildMap {
                            put("window", window)
                            put("chars", text.length.toString())
                            if (erase > 0) put("erased", erase.toString())
                        }
                    )
                    auditDirectWrite(window, before, inserter.readBeforeCursor(limit), erase, text)
                    next.copy(typed = next.typed.dropLast(erase) + text)
                } else {
                    refusalMark = timeSource.markNow()
                    log("dictation_direct_paused", mapOf("window" to window, "route" to insertion.route))
                    contiguous = false
                    next.copy(pending = next.pending + text, pausedReason = insertion.message)
                }
            }
        }
        directPlan = step.plan.copy(contiguous = step.plan.contiguous && contiguous)
        val warning = TranscriptionFailureSummary.from(segments, controller.emittedWindowCount)?.partialMessage
        next = next.copy(warning = warning)
        if (next != progress) directState.value = next
    }

    // Confere no campo o pedaço que acabou de ser escrito (P142). O que não saiu como pedido é refeito
    // pela rota atômica — apagar e escrever num passo só, sem instante nenhum com o texto apagado —, e
    // o que diverge além do que escrevemos fica como está: refazer dali apagaria texto que não é do
    // FlowVoice. Sem leitura do campo não há o que conferir, e vale a conta do app, como antes.
    private fun auditDirectWrite(window: String, before: String?, after: String?, erased: Int, written: String) {
        when (val verdict = DirectFieldWrite.verdict(before, after, erased, written)) {
            is DirectFieldWrite.Verdict.Ok, is DirectFieldWrite.Verdict.Unknown -> Unit
            is DirectFieldWrite.Verdict.Mismatch -> {
                log("dictation_write_mismatch", mapOf("window" to window, "acao" to verdict.reason))
                logWriteAudit(window, before, after)
            }
            is DirectFieldWrite.Verdict.Repair -> {
                val redone = inserter.rewriteTail(verdict.deleteBefore, verdict.text)
                log(
                    "dictation_write_mismatch",
                    mapOf(
                        "window" to window,
                        // O que o campo tinha do nosso pedaço contra o que foi mandado: a diferença é o
                        // caractere que sobrou (apagar engolido) ou que sumiu (espaço da emenda).
                        "campo" to verdict.deleteBefore.toString(),
                        "chars" to verdict.text.length.toString(),
                        "acao" to when {
                            redone == null -> "sem_rota"
                            redone.success -> "refeito"
                            else -> "falhou"
                        }
                    )
                )
                logWriteAudit(window, before, after)
            }
        }
    }

    // O que o campo tinha antes e depois de escrever, só quando houve divergência e só no log da P135
    // (build debuggable + marcador do adb). É o que diz o que o editor fez com o pedaço — o espaço
    // que sumiu, o ponto que ficou — e nunca vai para o log comum nem para o Diagnóstico.
    private fun logWriteAudit(window: String, before: String?, after: String?) {
        transcriptTextLog.log(
            "direct_write_audit",
            mapOf("window" to window, "antes" to (before ?: "—"), "depois" to (after ?: "—"))
        )
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
                directOutcome(mark)
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

    // A latência só é medida depois da revisão final (P147): o que interessa é o tempo entre o toque
    // que encerra o ditado e o texto final estar no campo.
    private suspend fun directOutcome(mark: TimeMark): DictationPipelineStatus {
        advanceDirect()
        proofreadDictation()
        if (statusState.value == DictationPipelineStatus.Cancelled) {
            return DictationPipelineStatus.Cancelled
        }
        val latencyMs = mark.elapsedNow().inWholeMilliseconds
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

    // Revisão final do ditado direto (P147). Cada janela foi pontuada isolada; no fim o texto inteiro
    // vai ao modelo de revisão, que só pode mexer em pontuação, maiúsculas, acentos e ortografia
    // (P132). Qualquer falha — guard, rede, campo trocado — deixa o campo exatamente como está:
    // nunca se apaga sem escrever de volta. Um cancelamento no meio (P38) também desiste: o texto
    // final não vai à OpenRouter depois de cancelado.
    private suspend fun proofreadDictation() {
        if (statusState.value == DictationPipelineStatus.Cancelled) {
            return skipProofread(DictationProofread.REASON_CANCELLED)
        }
        val prefs = preferences.read()
        val before = directState.value
        val request = DictationProofread.request(
            typed = before.typed,
            pending = before.pending,
            contiguous = directPlan.contiguous,
            enabled = prefs.proofreadingEnabled
        )
        val text = when (request) {
            is DictationProofread.Request.Skip -> return skipProofread(request.reason)
            is DictationProofread.Request.Send -> request.text
        }
        if (statusState.value == DictationPipelineStatus.Cancelled) {
            return skipProofread(DictationProofread.REASON_CANCELLED)
        }
        val apiKey = sessionApiKey.takeIf { transcription.fatalError.value == null }
            ?: return skipProofread(DictationProofread.REASON_ERROR)
        directState.value = before.copy(proofreading = true)
        val token = sessionToken
        val revised = try {
            // O ditado já está no campo: quem espera aqui é o usuário, de olho no texto (P152).
            withTimeoutOrNull(DictationProofread.TIMEOUT_MS) {
                proofreading.proofread(text, apiKey, prefs.proofreadingModel)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            directState.value = directState.value.copy(proofreading = false)
            return skipProofread(DictationProofread.REASON_ERROR)
        }
        if (token != sessionToken || statusState.value == DictationPipelineStatus.Cancelled) {
            directState.value = directState.value.copy(proofreading = false)
            return skipProofread(DictationProofread.REASON_CANCELLED)
        }
        directState.value = directState.value.copy(proofreading = false)
        if (revised == null) return skipProofread(DictationProofread.REASON_TIMEOUT)
        transcriptTextLog.log("proofreading_input", mapOf("text" to text))
        transcriptTextLog.log("proofreading_output", mapOf("text" to revised))
        // Uma palavra trocada não pode custar a pontuação do ditado inteiro (P149): onde a revisão
        // mexeu no que não podia, fica a palavra do ditado; a pontuação dela entra do mesmo jeito, e o
        // guard ainda confere o resultado.
        val merged = ProofreadingMerge.merge(text, revised) ?: revised
        if (merged != revised) transcriptTextLog.log("proofreading_merged", mapOf("text" to merged))
        when (val outcome = DictationProofread.outcome(text, merged)) {
            is DictationProofread.Outcome.Skip ->
                // Revisão igual ao ditado não quer dizer campo igual ao ditado: o editor pode ter
                // comido o espaço da emenda (P142). Antes de desistir, confere o campo.
                if (outcome.reason == DictationProofread.REASON_UNCHANGED) {
                    restoreDictation(text)
                } else {
                    skipProofread(outcome.reason)
                }
            is DictationProofread.Outcome.Replace -> replaceDictation(text, outcome)
        }
    }

    // A revisão veio igual ao ditado — mas o campo pode não estar igual ao que o app escreveu. No S26
    // (2026-09-16 14:55) o editor comeu o espaço da emenda e o campo ficou "sanguemostrou", enquanto
    // o app tinha escrito " mostrou" com o espaço. A conferência de logo depois de escrever não vê
    // isso, porque a leitura de lá chega antes de o editor aplicar (P142, `leitura_velha`); esta, no
    // fim do ditado, pega o campo já estável. Se o que está lá não é o que foi ditado, o ditado volta.
    private fun restoreDictation(sent: String) {
        val current = directState.value
        if (current.typed != sent || current.pending.isNotBlank() || !directPlan.contiguous) {
            return skipProofread(DictationProofread.REASON_UNCHANGED)
        }
        val before = inserter.readBeforeCursor(sent.length + DictationFieldTail.SLACK)
            ?: return skipProofread(DictationProofread.REASON_UNCHANGED)
        // O mesmo casamento por letras da P148: sem ele não se apaga nada.
        val erase = DictationFieldTail.eraseLength(before, sent)
            ?: return skipProofread(DictationProofread.REASON_FIELD_CHANGED)
        if (before.takeLast(erase) == sent) return skipProofread(DictationProofread.REASON_UNCHANGED)
        val insertion = inserter.insertWithoutTap(sent, erase)
        if (!insertion.success) {
            refusalMark = timeSource.markNow()
            return skipProofread(DictationProofread.REASON_REFUSED)
        }
        log(
            "dictation_field_restored",
            mapOf("chars" to sent.length.toString(), "erased" to erase.toString())
        )
    }

    // A troca só acontece se nada mexeu no campo enquanto a revisão ia e voltava (~1 s).
    private fun replaceDictation(sent: String, outcome: DictationProofread.Outcome.Replace) {
        val current = directState.value
        if (current.typed != sent || current.pending.isNotBlank() || !directPlan.contiguous) {
            return skipProofread(DictationProofread.REASON_NOT_CONTIGUOUS)
        }
        // O campo é a verdade, não a conta do app (P148): no S26 a conta saiu um caractere menor que o
        // campo, a troca apagou de menos e sobrou a primeira letra do ditado ("HHoje o dia..."). Quem
        // não souber ler o que está antes do cursor continua com a conta do app.
        val before = inserter.readBeforeCursor(sent.length + DictationFieldTail.SLACK)
        val erase = if (before == null) {
            outcome.deleteBefore
        } else {
            DictationFieldTail.eraseLength(before, sent)
                ?: return skipProofread(DictationProofread.REASON_FIELD_CHANGED)
        }
        val insertion = inserter.insertWithoutTap(outcome.text, erase)
        if (!insertion.success) {
            refusalMark = timeSource.markNow()
            return skipProofread(DictationProofread.REASON_REFUSED)
        }
        directState.value = current.copy(typed = outcome.text)
        log(
            "dictation_proofread_applied",
            mapOf(
                "chars" to outcome.text.length.toString(),
                "erased" to erase.toString(),
                // Quanto o campo divergiu da conta do app: zero é o esperado, e o que não for zero diz
                // que algum apagar ou escrever anterior não saiu como o app anotou.
                "drift" to (erase - outcome.deleteBefore).toString()
            )
        )
    }

    private fun skipProofread(reason: String) {
        log("dictation_proofread_skipped", mapOf("reason" to reason))
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
        if (statusState.value == DictationPipelineStatus.Cancelled) return revised
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
