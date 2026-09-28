package dev.rafaelbrauner.flowvoice.shared.pipeline

import dev.rafaelbrauner.flowvoice.shared.dictation.DictationSessionController
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationSessionState
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow
import dev.rafaelbrauner.flowvoice.shared.dictionary.PersonalDictionary
import dev.rafaelbrauner.flowvoice.shared.insertion.TextInserter
import dev.rafaelbrauner.flowvoice.shared.insertion.TextInsertionResult
import dev.rafaelbrauner.flowvoice.shared.localasr.LocalSpeechEngines
import dev.rafaelbrauner.flowvoice.shared.localasr.LocalTranscription
import dev.rafaelbrauner.flowvoice.shared.localasr.NemotronModel
import dev.rafaelbrauner.flowvoice.shared.localasr.TranscriptionEngine
import dev.rafaelbrauner.flowvoice.shared.localasr.TranscriptionEngineSelection
import dev.rafaelbrauner.flowvoice.shared.model.CloudModels
import dev.rafaelbrauner.flowvoice.shared.prefs.PreferencesStore
import dev.rafaelbrauner.flowvoice.shared.preview.LivePreview
import dev.rafaelbrauner.flowvoice.shared.preview.LivePreviewAssembler
import dev.rafaelbrauner.flowvoice.shared.proofreading.AudioChatClient
import dev.rafaelbrauner.flowvoice.shared.proofreading.ProofreadingClient
import dev.rafaelbrauner.flowvoice.shared.transcription.IncrementalTranscriptionController
import dev.rafaelbrauner.flowvoice.shared.transcription.OpenRouterConfig
import dev.rafaelbrauner.flowvoice.shared.transcription.SecretStore
import dev.rafaelbrauner.flowvoice.shared.transcription.SessionTranscription
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionClient
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionEventLog
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionModels
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionSegment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class DictationPipeline(
    private val controller: DictationSessionController,
    client: TranscriptionClient,
    private val config: OpenRouterConfig,
    private val dictionary: PersonalDictionary,
    proofreading: ProofreadingClient,
    // Passo único da passada final (Ajustes → "Formatação" → um passo só).
    audioChat: AudioChatClient,
    private val preferences: PreferencesStore,
    private val secrets: SecretStore,
    private val inserter: TextInserter,
    private val scope: CoroutineScope,
    private val eventLog: TranscriptionEventLog = TranscriptionEventLog.NoOp,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    // Texto ditado vai só para este log, nunca para eventLog nem para as linhas do Diagnóstico (P135).
    private val transcriptTextLog: TranscriptionEventLog = TranscriptionEventLog.NoOp,
    // Motor no aparelho (Android). Null no desktop: só nuvem.
    private val localEngines: LocalSpeechEngines? = null,
    private val localWindowTargetDurationMs: Long = LOCAL_WINDOW_TARGET_MS
) {
    private val eventLines = MutableSharedFlow<String>(extraBufferCapacity = EVENT_BUFFER)
    private val cloud = IncrementalTranscriptionController(
        client = client,
        config = config,
        scope = scope,
        apiKeyProvider = { secrets.readOpenRouterKey() },
        modelProvider = { TranscriptionModels.selected(preferences, config) },
        eventLog = { event, metadata -> log(event, metadata) },
        textLog = transcriptTextLog
    )
    private val local = localEngines?.let { engines ->
        LocalTranscription(engines, scope, eventLog = { event, metadata -> log(event, metadata) }, timeSource = timeSource)
    }
    private val finalPass = FinalPass(
        client = client,
        proofreading = proofreading,
        audioChat = audioChat,
        log = { event, metadata -> log(event, metadata) },
        textLog = transcriptTextLog,
        timeSource = timeSource
    )
    // Quem transcreve a sessão em curso, escolhido no início dela e fixo até o fim: uma falha do motor
    // no aparelho nunca troca de motor no meio do ditado.
    private var active: SessionTranscription = cloud
    private var sessionEngine = TranscriptionEngine.Cloud
    private val segmentsState = MutableStateFlow<List<TranscriptionSegment>>(emptyList())
    private val partialState = MutableStateFlow("")
    // O motor no aparelho falhou no meio do ditado: o texto até ali é entregue com este aviso.
    private var engineInterruption: EngineInterruption? = null
    // O ditado no aparelho chegou ao teto de duração (LOCAL_SESSION_CAP).
    private var sessionCapReached = false
    private var sessionCapJob: Job? = null
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
    // "Inserir aqui" levou o ditado para outro campo (Y2): o que o app escreveu já não está inteiro
    // antes do cursor, e a revisão final não pode mais apagar o ditado para reescrevê-lo.
    private var dictationSplitAcrossFields = false
    // Microfone que caiu no meio do ditado (R3): o texto até ali é entregue com este aviso.
    private var captureInterruption: CaptureInterruption? = null
    // O fim do ditado passou do prazo (Y5): as janelas ainda sem resposta contam como falha.
    private var finalizeDeadlinePassed = false
    // O texto final da passada final (o áudio inteiro) está no campo, ou veio igual ao rascunho. Aí um
    // trecho que falhou ao vivo não faltou no que ficou, e o fim do ditado não avisa dele.
    private var finalPassCovered = false

    val status: StateFlow<DictationPipelineStatus> = statusState.asStateFlow()
    val target: StateFlow<DictationTarget> = targetState.asStateFlow()
    val session: StateFlow<DictationPipelineSession> = pipelineSessionState.asStateFlow()
    val sessionState: StateFlow<DictationSessionState> = controller.state
    val segments: StateFlow<List<TranscriptionSegment>> = segmentsState.asStateFlow()
    // Texto do motor no aparelho ainda não fechado em pedaço: só prévia (cartão da sessão direta,
    // barra de revisão, nota), nunca digitado. Vazio com a nuvem.
    val livePartial: StateFlow<String> = partialState.asStateFlow()
    val sessionWindows: StateFlow<List<DictationWindow>> = windowsState.asStateFlow()
    val events: SharedFlow<String> = eventLines.asSharedFlow()
    val directInsertion: StateFlow<DirectInsertionProgress> = directState.asStateFlow()

    val capturedDurationMs: Long
        get() = controller.capturedDurationMs

    val model: String
        get() = TranscriptionModels.selected(preferences, config)

    // O motor da sessão em curso (ou da última).
    val sessionTranscriptionEngine: TranscriptionEngine
        get() = sessionEngine

    val proofreadingEnabled: Boolean
        get() = preferences.read().proofreadingEnabled

    // O motor que o próximo ditado usa (`TranscriptionEngineSelection`).
    fun effectiveEngine(): TranscriptionEngine = TranscriptionEngineSelection.effective(
        choice = preferences.read().transcriptionEngine,
        modelInstalled = localEngines?.modelInstalled() == true,
        platformSupportsLocal = localEngines != null
    )

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            controller.windows.collect { window ->
                windowsState.value = windowsState.value + window
                active.submit(window)
                submittedWindows.value += 1
                log(
                    "dictation_window",
                    buildMap {
                        put("window", (window.index + 1).toString())
                        put("durationMs", window.durationMs.toString())
                        put("model", modelLabel())
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
                if (state !is DictationSessionState.Error) return@collect
                when (statusState.value) {
                    DictationPipelineStatus.Starting -> {
                        publish(DictationPipelineStatus.Failed(state.message))
                        log("dictation_failed", mapOf("stage" to "capture"))
                    }
                    DictationPipelineStatus.Recording -> stopOnCaptureError(state.message)
                    else -> Unit
                }
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            cloud.budgetExhausted.collect { exhausted ->
                if (exhausted) stopAtRequestBudget()
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            cloud.fatalError.collect { error ->
                if (error != null) stopOnInvalidKey()
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            cloud.segments.collect { segments ->
                if (active !== cloud) return@collect
                segmentsState.value = segments
                advanceDirect()
            }
        }
        local?.let { transcriber ->
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                transcriber.segments.collect { segments ->
                    if (active !== transcriber) return@collect
                    segmentsState.value = segments
                    advanceDirect()
                }
            }
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                transcriber.partial.collect { partial -> if (active === transcriber) partialState.value = partial }
            }
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                transcriber.failure.collect { error -> if (error != null && active === transcriber) stopOnEngineFailure() }
            }
        }
    }

    fun preview(): LivePreview {
        val assembled = LivePreviewAssembler.assemble(
            segments = previewSegments(),
            sessionComplete = sessionState.value is DictationSessionState.Finalized
        )
        return LivePreview(
            finalized = dictionary.apply(assembled.finalized),
            provisional = dictionary.apply(withPartial(assembled.provisional))
        )
    }

    fun liveText(): LivePreview {
        val split = LiveDictationText.split(previewSegments())
        return LivePreview(
            finalized = dictionary.apply(split.finalized),
            provisional = dictionary.apply(withPartial(split.provisional))
        )
    }

    // No motor local o pedaço em fechamento leva milissegundos e não tem texto próprio: o que está
    // sendo dito aparece pelo parcial, sem a marca de "transcrevendo".
    private fun previewSegments(): List<TranscriptionSegment> = segmentsState.value.let { current ->
        if (sessionEngine == TranscriptionEngine.Local) {
            current.filter { it.status != TranscriptionSegment.Status.Transcribing }
        } else {
            current
        }
    }

    private fun withPartial(provisional: String): String =
        listOf(provisional, partialState.value).filter { it.isNotBlank() }.joinToString(" ")

    suspend fun start(target: DictationTarget = DictationTarget.ActiveField) {
        if (statusState.value.isBusy) return
        val token = ++sessionToken
        sessionId++
        targetState.value = target
        directPlan = DirectInsertionPlan()
        dictationSplitAcrossFields = false
        captureInterruption = null
        finalizeDeadlinePassed = false
        finalPassCovered = false
        directState.value = DirectInsertionProgress(
            sessionId = sessionId,
            active = target == DictationTarget.ActiveField && !preferences.read().reviewBeforeInsert
        )
        publish(DictationPipelineStatus.Starting)
        val engine = effectiveEngine()
        // A chave só é exigida pela nuvem. Com o motor no aparelho ela serve só para a revisão por IA,
        // se estiver ligada; sem ela a revisão é pulada e o ditado segue.
        sessionApiKey = secrets.readOpenRouterKey()?.takeIf { it.isNotBlank() }
        cloud.reset(sessionApiKey)
        local?.cancel()
        sessionCapJob?.cancel()
        sessionEngine = engine
        val localSession = local.takeIf { engine == TranscriptionEngine.Local }
        active = localSession ?: cloud
        segmentsState.value = emptyList()
        partialState.value = ""
        engineInterruption = null
        sessionCapReached = false
        windowsState.value = emptyList()
        submittedWindows.value = 0
        if (localSession == null && sessionApiKey == null) {
            publish(DictationPipelineStatus.Failed(INVALID_KEY_MESSAGE))
            log("dictation_failed", mapOf("stage" to "key"))
            return
        }
        if (target == DictationTarget.ActiveField) inserter.captureTarget()
        try {
            localSession?.begin(NemotronModel.LANGUAGE)
            if (localSession == null) {
                controller.start()
            } else {
                controller.start(
                    tap = localSession,
                    windowTargetDurationMs = localWindowTargetDurationMs,
                    windowEndpointing = controller.windowEndpointing?.copy(minBufferedMs = LOCAL_MIN_BUFFERED_MS)
                )
            }
            if (token != sessionToken) {
                controller.cancel()
                return
            }
            if (statusState.value == DictationPipelineStatus.Starting) {
                publish(DictationPipelineStatus.Recording)
                log("dictation_started", mapOf("engine" to engineLabel(engine)))
                if (localSession != null) limitLocalSession(token)
                when {
                    localSession?.failure?.value != null -> stopOnEngineFailure()
                    cloud.fatalError.value != null -> stopOnInvalidKey()
                    cloud.budgetExhausted.value -> stopAtRequestBudget()
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            localSession?.cancel()
            if (token == sessionToken) {
                publish(DictationPipelineStatus.Failed(error.message ?: "erro desconhecido"))
                log("dictation_failed", mapOf("stage" to "start"))
            }
        }
    }

    private fun stopOnInvalidKey() = stopEarly("dictation_key_stop")

    // O motor no aparelho falhou (P-local): o que ele já reconheceu é entregue, com aviso. Nunca troca
    // para a nuvem no meio do ditado — seria mandar áudio para fora sem o usuário ter escolhido.
    private fun stopOnEngineFailure() {
        if (!statusState.value.isBusy) return
        if (engineInterruption == null) engineInterruption = EngineInterruption(controller.capturedDurationMs)
        stopEarly("dictation_engine_stop")
    }

    // O microfone nunca fica aberto indefinidamente: sem o teto de requisições da nuvem, o ditado no
    // aparelho para em LOCAL_SESSION_CAP, com o texto até ali e aviso.
    private fun limitLocalSession(token: Int) {
        sessionCapJob?.cancel()
        sessionCapJob = scope.launch {
            delay(LOCAL_SESSION_CAP)
            if (token == sessionToken && statusState.value == DictationPipelineStatus.Recording) {
                sessionCapReached = true
                stopEarly("dictation_time_cap_stop")
            }
        }
    }

    private fun stopAtRequestBudget() = stopEarly("dictation_budget_stop")

    // O microfone caiu com o ditado em curso (R3): é uma parada antecipada como a da chave (P111). O
    // controlador já soltou o áudio que tinha como última janela; aqui o fim segue o caminho normal —
    // espera as janelas em voo e entrega o texto até ali, com aviso.
    private fun stopOnCaptureError(message: String) {
        captureInterruption = CaptureInterruption(message, controller.capturedDurationMs)
        stopEarly("dictation_capture_stop")
    }

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
            } else if (final.text.isBlank() && failsWithoutText(final.failures)) {
                noTextOutcome(final.failures)
            } else {
                dictionary.suggestFrom(final.text)
                insert(
                    text = final.text,
                    warning = warningFor(final.failures),
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
            } else if (final.text.isBlank() && failsWithoutText(final.failures)) {
                noTextOutcome(final.failures)
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
                DictationPipelineStatus.Ready(final.text, warningFor(final.failures), latencyMs)
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
        // Depois do toque de parar, a sessão direta já não tem o que cancelar: o microfone fechou, o que foi
        // dito está no campo ou a caminho, e a passada final só troca o rascunho pelo texto certo. No S26
        // (2026-09-28 13:56) o "Cancelar" do cartão "revisando…" descartou uma passada final de 150
        // caracteres e deixou no campo um rascunho de 88. O fim segue e aplica; quem quiser desfazer apaga
        // pelo teclado, como qualquer texto que já entrou.
        if (isDirectSession() && statusState.value == DictationPipelineStatus.Transcribing) {
            log("dictation_cancel_ignored", mapOf("motivo" to "finalizando"))
            return
        }
        sessionToken++
        sessionCapJob?.cancel()
        active.cancel()
        partialState.value = ""
        try {
            controller.cancel()
        } finally {
            publish(DictationPipelineStatus.Cancelled)
            // O que já foi digitado fica no campo; o pendente é descartado. O áudio da sessão também: um
            // ditado cancelado não vai à passada final nem fica como clipe do Diagnóstico.
            directState.value = directState.value.copy(pending = "", pausedReason = null)
            windowsState.value = emptyList()
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
        // "Inserir aqui" pode ter escrito noutro campo: o próximo pedaço não apaga nada (P144), e a
        // revisão final não troca mais o ditado nesta sessão (Y2) — o pedaço seguinte volta a ser
        // contíguo ao anterior, mas o ditado inteiro nunca mais está todo antes do cursor.
        directPlan = directPlan.copy(contiguous = false)
        dictationSplitAcrossFields = true
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
        val segments = resolvedSegments()
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
                    auditDirectWrite(window, before, inserter.readBeforeCursor(limit), erase, text, next.typed)
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
        // Com a passada final a caminho, o trecho que falhou ao vivo não aparece: ela retranscreve o áudio
        // inteiro, e o fim do ditado avisa se ainda assim faltar texto. Sem ela, o aviso é a única rede.
        val warning = TranscriptionFailureSummary.from(segments, controller.emittedWindowCount)
            ?.partialMessage
            ?.takeUnless { finalPassExpected() }
        next = next.copy(warning = warning)
        if (next != progress) directState.value = next
    }

    // Confere no campo o pedaço que acabou de ser escrito (P142). O que não saiu como pedido é refeito
    // pela rota atômica — apagar e escrever num passo só, sem instante nenhum com o texto apagado —, e
    // o que diverge além do que escrevemos fica como está: refazer dali apagaria texto que não é do
    // FlowVoice. Sem leitura do campo não há o que conferir, e vale a conta do app, como antes.
    private fun auditDirectWrite(
        window: String,
        before: String?,
        after: String?,
        erased: Int,
        written: String,
        typed: String
    ) {
        when (val verdict = DirectFieldWrite.verdict(before, after, erased, written, typed)) {
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
            awaitTranscriptions()
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
        finalPassDictation()
        if (statusState.value == DictationPipelineStatus.Cancelled) {
            return DictationPipelineStatus.Cancelled
        }
        // Sem o texto final no campo (revisão desligada, sem resposta, recusada), o campo ainda passa pela
        // mesma conferência do fim (E): no corpo de nota do Samsung Notes (S26, 2026-09-28) a emenda entre
        // trechos virava linha nova ou perdia o espaço ("tarde?\nPaciente", "bem?Consegue"). A causa, o commit
        // longo tratado como colagem, é evitada na escrita (CommitChunks); isto fica como rede para o editor
        // que ainda mexer no espaço. O ditado volta ao que o app escreveu, conferido letra a letra.
        if (!finalPassCovered) directState.value.typed.takeIf { it.isNotBlank() }?.let { rewriteDictation(it, spacingOnly = true) }
        val latencyMs = mark.elapsedNow().inWholeMilliseconds
        val progress = directState.value
        val failures = TranscriptionFailureSummary.from(resolvedSegments(), controller.emittedWindowCount)
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
                DictationPipelineStatus.Ready(progress.pending, warningFor(failures), latencyMs, progress.pausedReason)
            }
            progress.typed.isBlank() && failsWithoutText(failures) -> noTextOutcome(failures)
            else -> completed(
                progress.typed,
                directDelivery(progress.typed),
                warningFor(failures),
                latencyMs,
                failedWindows = failures?.failedCount ?: 0
            )
        }
    }

    // Passada final (duas passadas), nos dois motores: o rascunho — do Nemotron ou das janelas da nuvem —
    // já está no campo; o áudio inteiro vai uma vez à nuvem (FinalPass) e o texto final troca o rascunho
    // (P147) com as travas de sempre: sem pendente, contíguo, nunca depois de "Inserir aqui" noutro campo
    // (Y2), até MAX_CHARS, e só apagando o que o campo confirma ser o rascunho (P148). Um cancelamento no
    // meio (P38) desiste. Qualquer falha deixa o rascunho, e o fim do ditado avisa.
    private suspend fun finalPassDictation() {
        if (statusState.value == DictationPipelineStatus.Cancelled) {
            return skipFinalPass(DictationProofread.REASON_CANCELLED)
        }
        val prefs = preferences.read()
        val before = directState.value
        val request = DictationProofread.request(
            typed = before.typed,
            pending = before.pending,
            contiguous = wholeDictationBeforeCursor(),
            enabled = prefs.proofreadingEnabled,
            windowsFailed = TranscriptionFailureSummary.from(resolvedSegments(), controller.emittedWindowCount) != null
        )
        val draft = when (request) {
            is DictationProofread.Request.Skip -> return skipFinalPass(request.reason)
            is DictationProofread.Request.Send -> request.text
        }
        val apiKey = sessionApiKey ?: return skipFinalPass(DictationProofread.REASON_NO_KEY)
        // A chave foi recusada ou o crédito acabou no meio do ditado (erro fatal da nuvem): a passada final
        // cairia no mesmo erro.
        if (cloud.fatalError.value != null) return skipFinalPass(DictationProofread.REASON_ERROR)
        directState.value = before.copy(proofreading = true)
        val token = sessionToken
        val result = try {
            runFinalPass(draft, apiKey)
        } finally {
            directState.value = directState.value.copy(proofreading = false)
        }
        if (token != sessionToken || statusState.value == DictationPipelineStatus.Cancelled) {
            return skipFinalPass(DictationProofread.REASON_CANCELLED)
        }
        val final = when (result) {
            is FinalPass.Result.Kept -> return skipFinalPass(result.reason)
            is FinalPass.Result.Final -> result.text
        }
        when (val outcome = DictationProofread.replacement(draft, final)) {
            is DictationProofread.Outcome.Skip ->
                if (outcome.reason == DictationProofread.REASON_UNCHANGED) {
                    finalPassCovered = true
                    restoreDictation(draft)
                } else {
                    skipFinalPass(outcome.reason)
                }
            is DictationProofread.Outcome.Replace -> replaceDictation(draft, outcome)
        }
    }

    // A barra de revisão e a nota recebem o texto final no lugar do rascunho; sem ele, o rascunho.
    private suspend fun finalPassText(draft: String): String {
        val prefs = preferences.read()
        if (!prefs.proofreadingEnabled) return draft.also { skipFinalPass(DictationProofread.REASON_DISABLED) }
        if (draft.isBlank()) return draft.also { skipFinalPass(DictationProofread.REASON_EMPTY) }
        if (statusState.value == DictationPipelineStatus.Cancelled) {
            return draft.also { skipFinalPass(DictationProofread.REASON_CANCELLED) }
        }
        val apiKey = sessionApiKey ?: return draft.also { skipFinalPass(DictationProofread.REASON_NO_KEY) }
        if (cloud.fatalError.value != null) return draft.also { skipFinalPass(DictationProofread.REASON_ERROR) }
        val token = sessionToken
        val result = runFinalPass(draft, apiKey)
        if (token != sessionToken || statusState.value == DictationPipelineStatus.Cancelled) return draft
        return when (result) {
            is FinalPass.Result.Kept -> draft.also { skipFinalPass(result.reason) }
            is FinalPass.Result.Final -> result.text.also {
                finalPassCovered = true
                log("final_pass_applied", mapOf("chars" to it.length.toString(), "draftChars" to draft.length.toString()))
            }
        }
    }

    // O vocabulário do usuário vale no texto final também (N2): a transcrição da nuvem pode escrever um
    // termo aprovado de outro jeito.
    private suspend fun runFinalPass(draft: String, apiKey: String): FinalPass.Result =
        when (
            val result = finalPass.run(
                draft = draft,
                windows = windowsState.value,
                apiKey = apiKey,
                transcriptionModel = TranscriptionModels.selected(preferences, config),
                formatting = CloudModels.formatting(preferences.read())
            )
        ) {
            is FinalPass.Result.Final -> FinalPass.Result.Final(dictionary.apply(result.text))
            is FinalPass.Result.Kept -> result
        }

    private fun skipFinalPass(reason: String) {
        log("final_pass_skipped", mapOf("reason" to reason))
    }

    // A revisão veio igual ao ditado — mas o campo pode não estar igual ao que o app escreveu. No S26
    // (2026-09-16 14:55) o editor comeu o espaço da emenda e o campo ficou "sanguemostrou", enquanto
    // o app tinha escrito " mostrou" com o espaço. A conferência de logo depois de escrever não vê
    // isso, porque a leitura de lá chega antes de o editor aplicar (P142, `leitura_velha`); esta, no
    // fim do ditado, pega o campo já estável. Se o que está lá não é o que foi ditado, o ditado volta.
    private fun restoreDictation(sent: String) {
        rewriteDictation(sent)?.let(::skipFinalPass)
    }

    // Devolve o motivo de não ter reescrito; nulo quando o campo voltou ao ditado. `spacingOnly`: só quando o
    // campo difere do ditado em espaço e linha nova (a emenda do Samsung Notes). Um ponto que sobrou pode
    // ser o apagar que o editor engoliu, e reescrever num campo assim duplicaria o ditado.
    private fun rewriteDictation(sent: String, spacingOnly: Boolean = false): String? {
        val current = directState.value
        if (current.typed != sent || current.pending.isNotBlank() || !wholeDictationBeforeCursor()) {
            return DictationProofread.REASON_UNCHANGED
        }
        val before = inserter.readBeforeCursor(sent.length + DictationFieldTail.SLACK)
            ?: return DictationProofread.REASON_UNCHANGED
        // O mesmo casamento por letras da P148: sem ele não se apaga nada.
        val erase = DictationFieldTail.eraseLength(before, sent)
            ?: return DictationProofread.REASON_FIELD_CHANGED
        val tail = before.takeLast(erase)
        if (tail == sent) return DictationProofread.REASON_UNCHANGED
        if (spacingOnly && tail.filterNot { it.isWhitespace() } != sent.filterNot { it.isWhitespace() }) {
            return DictationProofread.REASON_FIELD_CHANGED
        }
        val insertion = inserter.insertWithoutTap(sent, erase)
        if (!insertion.success) {
            refusalMark = timeSource.markNow()
            return DictationProofread.REASON_REFUSED
        }
        log(
            "dictation_field_restored",
            mapOf("chars" to sent.length.toString(), "erased" to erase.toString())
        )
        return null
    }

    // A troca só acontece se nada mexeu no campo enquanto a revisão ia e voltava (~1 s). Depois de um
    // "Inserir aqui" noutro campo nunca há troca (Y2): o apagar pela conta do app, quando o campo não
    // se deixa ler, comeria o texto do usuário daquele campo.
    private fun replaceDictation(sent: String, outcome: DictationProofread.Outcome.Replace) {
        val current = directState.value
        if (current.typed != sent || current.pending.isNotBlank() || !wholeDictationBeforeCursor()) {
            return skipFinalPass(DictationProofread.REASON_NOT_CONTIGUOUS)
        }
        // O campo é a verdade, não a conta do app (P148): no S26 a conta saiu um caractere menor que o
        // campo, a troca apagou de menos e sobrou a primeira letra do ditado ("HHoje o dia..."). Quem
        // não souber ler o que está antes do cursor continua com a conta do app.
        val before = inserter.readBeforeCursor(sent.length + DictationFieldTail.SLACK)
        // Rascunho vazio (todas as janelas falharam): nada a apagar, o texto final só entra.
        val erase = if (before == null || sent.isEmpty()) {
            outcome.deleteBefore
        } else {
            DictationFieldTail.eraseLength(before, sent)
                ?: return skipFinalPass(DictationProofread.REASON_FIELD_CHANGED)
        }
        val insertion = inserter.insertWithoutTap(outcome.text, erase)
        if (!insertion.success) {
            refusalMark = timeSource.markNow()
            return skipFinalPass(DictationProofread.REASON_REFUSED)
        }
        directState.value = current.copy(typed = outcome.text)
        finalPassCovered = true
        log(
            "final_pass_applied",
            mapOf(
                "chars" to outcome.text.length.toString(),
                "erased" to erase.toString(),
                // Quanto o campo divergiu da conta do app: zero é o esperado, e o que não for zero diz
                // que algum apagar ou escrever anterior não saiu como o app anotou.
                "drift" to (erase - outcome.deleteBefore).toString()
            )
        )
    }

    private fun finalPassExpected(): Boolean = preferences.read().proofreadingEnabled && sessionApiKey != null

    private fun wholeDictationBeforeCursor(): Boolean = directPlan.contiguous && !dictationSplitAcrossFields

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
        awaitTranscriptions()
        val segments = resolvedSegments()
        val failures = TranscriptionFailureSummary.from(
            segments = segments,
            totalWindows = controller.emittedWindowCount
        )
        return FinalText(reviseFinalText(segments), failures)
    }

    // Fecha a captura e espera as janelas em voo, com prazo para a sessão inteira (Y5). Cada janela
    // tem seus 30 s e 3 retentativas; com a rede presa, a fila somava minutos com o usuário parado em
    // "Transcrevendo". Passado o prazo, o que já voltou é entregue e o resto conta como falha, com
    // aviso — e nada mais é pedido à OpenRouter nesta sessão.
    private suspend fun awaitTranscriptions() {
        controller.finalize()
        sessionCapJob?.cancel()
        submittedWindows.first { it >= controller.emittedWindowCount }
        // O motor no aparelho fecha o que ainda segura com a cauda, depois da última janela.
        local?.takeIf { active === it }?.endOfAudio()
        withTimeoutOrNull(FINALIZE_DEADLINE) { active.awaitIdle() } ?: run {
            finalizeDeadlinePassed = true
            active.cancel()
            log(
                "dictation_finalize_deadline",
                mapOf(
                    "windows" to controller.emittedWindowCount.toString(),
                    "unresolved" to resolvedSegments().count { it.errorKind == TranscriptionFailureSummary.DEADLINE_KIND }.toString()
                )
            )
        }
        // Tudo o que o motor reconheceu já fechou em pedaço: não sobra provisório na tela.
        partialState.value = ""
    }

    // As janelas como o fim do ditado as enxerga: depois do prazo (Y5), a que ficou sem resposta — ou
    // nem chegou a ir, presa na fila — vira falha, para o texto sair sem ela e o aviso dizer qual foi.
    private fun resolvedSegments(): List<TranscriptionSegment> {
        val segments = active.segments.value
        if (!finalizeDeadlinePassed) return segments
        val byWindow = segments.associateBy { it.windowIndex }
        val total = maxOf(controller.emittedWindowCount, (segments.maxOfOrNull { it.windowIndex } ?: -1) + 1)
        return (0 until total).map { index ->
            val segment = byWindow[index]
            if (segment == null || segment.status == TranscriptionSegment.Status.Transcribing) {
                TranscriptionSegment(
                    windowIndex = index,
                    status = TranscriptionSegment.Status.Failed,
                    errorKind = TranscriptionFailureSummary.DEADLINE_KIND
                )
            } else {
                segment
            }
        }
    }

    private fun noTextFailure(failures: TranscriptionFailureSummary): DictationPipelineStatus {
        log(
            "dictation_failed",
            mapOf("stage" to "transcription", "failedWindows" to failures.failedCount.toString())
        )
        return DictationPipelineStatus.Failed(failures.noTextMessage)
    }

    private fun failsWithoutText(failures: TranscriptionFailureSummary?): Boolean =
        captureInterruption != null || engineInterruption != null || failures != null

    // Sem texto nenhum, o motivo que vale é o microfone que caiu (R3); sem queda, o das janelas.
    private fun noTextOutcome(failures: TranscriptionFailureSummary?): DictationPipelineStatus {
        val interruption = captureInterruption
        val engineFailure = engineInterruption
        if (interruption == null && engineFailure != null) {
            log("dictation_failed", mapOf("stage" to "local_engine"))
            return DictationPipelineStatus.Failed(engineFailure.message)
        }
        if (interruption != null || failures == null) {
            log("dictation_failed", mapOf("stage" to "capture"))
            return DictationPipelineStatus.Failed(interruption?.message ?: "erro desconhecido")
        }
        return noTextFailure(failures)
    }

    private fun warningFor(failures: TranscriptionFailureSummary?): String? =
        listOfNotNull(
            captureInterruption?.warning,
            engineInterruption?.warning,
            LOCAL_SESSION_CAP_WARNING.takeIf { sessionCapReached },
            // Trecho que falhou ao vivo só é aviso se o texto que ficou é o ao vivo (sem passada final, ou
            // ela não veio): a passada final retranscreve o áudio inteiro (decisão de 2026-09-28).
            failures?.partialMessage?.takeUnless { finalPassCovered }
        )
            .joinToString("; ")
            .ifEmpty { null }

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

    private suspend fun reviseFinalText(segments: List<TranscriptionSegment>): String {
        val assembled = LivePreviewAssembler.assemble(
            segments = segments,
            sessionComplete = true
        )
        return finalPassText(dictionary.apply(assembled.finalized))
    }

    private fun modelLabel(): String =
        if (sessionEngine == TranscriptionEngine.Local) LOCAL_MODEL_LABEL else TranscriptionModels.selected(preferences, config)

    private fun engineLabel(engine: TranscriptionEngine): String =
        if (engine == TranscriptionEngine.Local) "local" else "cloud"

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

    // Onde o microfone caiu (R3). O aviso diz até que ponto do ditado o texto vai, no mesmo molde do
    // aviso de janela que falhou.
    private class CaptureInterruption(val message: String, capturedMs: Long) {
        val warning: String = "Captura do microfone interrompida aos ${clock(capturedMs)}: texto só até ali"
    }

    // Onde o motor no aparelho falhou. O aviso diz até que ponto do ditado o texto vai, como o do
    // microfone que caiu (R3).
    private class EngineInterruption(capturedMs: Long) {
        val message: String = "O motor no aparelho falhou e nada foi transcrito"
        val warning: String = "Motor no aparelho falhou aos ${clock(capturedMs)}: texto só até ali"
    }

    companion object {
        // Teto de duração do ditado no aparelho: o microfone nunca fica aberto indefinidamente. Dez
        // minutos cobrem uma evolução longa e são mais que os ~4–6 min do teto da nuvem.
        val LOCAL_SESSION_CAP = 10.minutes
        const val LOCAL_SESSION_CAP_WARNING = "Ditado encerrado no limite de 10 min"
        // No aparelho cada corte só fecha o texto do fluxo contínuo (não custa requisição): pedaços de até
        // 1,2 s, e pausa valendo a partir de 0,8 s de áudio, põem as palavras no campo ~1 s depois de
        // ditas, e o cartão da prévia deixou de ser preciso (S26, 2026-09-28). A nuvem segue com 4 s e 2 s.
        const val LOCAL_WINDOW_TARGET_MS = 1_200L
        const val LOCAL_MIN_BUFFERED_MS = 800L
        private const val LOCAL_MODEL_LABEL = "nemotron-3.5-streaming"
        private const val EVENT_BUFFER = 64
        private const val INVALID_KEY_MESSAGE = "chave OpenRouter ausente ou inválida"
        private const val DIRECT_ROUTE = "direto"
        private val RETRY_GUARD = 1.seconds
        private val NOTE_DELIVERY = TextInsertionResult(success = false, route = "nota", message = "texto entregue à nota")

        // Prazo do fim do ditado (Y5), do toque de parar até o texto: uma janela sadia volta em 1–2 s,
        // e a fila no fim é de uma ou duas janelas. 15 s cobrem uma retentativa com folga sem deixar o
        // usuário minutos em "Transcrevendo".
        private val FINALIZE_DEADLINE = 15.seconds

        private fun clock(ms: Long): String {
            val seconds = ms / 1_000L
            return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
        }
    }
}
