package dev.rafaelbrauner.flowvoice.shared.transcription

import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow
import dev.rafaelbrauner.flowvoice.shared.dictation.EmptyAudioMemory
import dev.rafaelbrauner.flowvoice.shared.dictation.SilentWindow
import dev.rafaelbrauner.flowvoice.shared.dictation.WindowSpeechGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class IncrementalTranscriptionController(
    private val client: TranscriptionClient,
    private val config: OpenRouterConfig,
    private val scope: CoroutineScope,
    private val apiKeyProvider: () -> String?,
    private val modelProvider: () -> String = { config.model },
    private val eventLog: TranscriptionEventLog = TranscriptionEventLog.NoOp,
    private val textLog: TranscriptionEventLog = TranscriptionEventLog.NoOp
) {
    private val segmentsMutex = Mutex()
    private val processMutex = Mutex()
    private val provisional = MutableStateFlow("")
    private val segmentState = MutableStateFlow<List<TranscriptionSegment>>(emptyList())
    private val jobs = mutableListOf<Job>()
    private var cancelled = false
    private var requestCount = 0
    // O que já se provou vazio nesta sessão, para não pagar duas vezes pelo mesmo nível (P153).
    private val emptyAudio = EmptyAudioMemory()
    private var sessionApiKey: String? = null
    private val budgetState = MutableStateFlow(false)
    private val fatalState = MutableStateFlow<TranscriptionError?>(null)

    val provisionalText: StateFlow<String> = provisional.asStateFlow()
    val segments: StateFlow<List<TranscriptionSegment>> = segmentState.asStateFlow()
    val budgetExhausted: StateFlow<Boolean> = budgetState.asStateFlow()
    val fatalError: StateFlow<TranscriptionError?> = fatalState.asStateFlow()

    fun submit(window: DictationWindow) {
        if (cancelled) return
        val withinBudget = requestCount < config.maxRequestsPerSession
        if (withinBudget) requestCount++ else budgetState.value = true
        val job = scope.launch {
            if (!withinBudget) {
                rejectOverBudget(window)
            } else {
                processMutex.withLock {
                    if (!cancelled) {
                        process(window)
                    }
                }
            }
        }
        jobs += job
    }

    fun cancel() {
        cancelled = true
        jobs.toList().forEach { it.cancel() }
        jobs.clear()
        client.cancel()
    }

    // A chave vale para a sessão inteira: uma falha passageira do cofre no meio do ditado não pode
    // virar "chave ausente" e encerrar a sessão (P134).
    fun reset(apiKey: String? = null) {
        jobs.toList().forEach { it.cancel() }
        jobs.clear()
        cancelled = false
        requestCount = 0
        sessionApiKey = apiKey?.takeIf { it.isNotBlank() }
        budgetState.value = false
        fatalState.value = null
        emptyAudio.clear()
        provisional.value = ""
        segmentState.value = emptyList()
    }

    suspend fun awaitIdle() {
        jobs.toList().forEach { it.join() }
    }

    private suspend fun rejectOverBudget(window: DictationWindow) {
        upsert(
            TranscriptionSegment(
                windowIndex = window.index,
                status = TranscriptionSegment.Status.Failed,
                errorKind = TranscriptionError.SessionBudgetExceeded().kind
            )
        )
        eventLog.log(
            "transcription_budget",
            mapOf(
                "window" to window.index.toString(),
                "durationMs" to window.durationMs.toString(),
                "model" to modelProvider()
            )
        )
    }

    private suspend fun process(window: DictationWindow) {
        if (cancelled) return
        if (SilentWindow.detect(window)) {
            skipSilent(window, "digital")
            return
        }
        // Sem fala medida bastante, a janela não vale uma requisição: com o piso de ruído alto o
        // silêncio do fim do ditado chegava aqui como janela de teto e voltava vazia (P151).
        WindowSpeechGate.skipReason(window)?.let { reason ->
            skipSilent(window, reason)
            return
        }
        // Áudio que não é mais alto do que um que já voltou vazio nesta sessão não se paga de novo
        // (P153). Quem fala baixo escapa da trava: o nível que já rendeu texto nunca é silêncio.
        if (emptyAudio.skips(window.peakLevel)) {
            skipSilent(window, WindowSpeechGate.REASON_LEVEL_ALREADY_EMPTY)
            return
        }
        upsert(
            TranscriptionSegment(
                windowIndex = window.index,
                status = TranscriptionSegment.Status.Transcribing
            )
        )
        fatalState.value?.let { fatal ->
            fail(window, fatal)
            return
        }
        val apiKey = sessionApiKey ?: apiKeyProvider()?.takeIf { it.isNotBlank() }?.also { sessionApiKey = it }
        if (apiKey == null) {
            failFatally(window, TranscriptionError.InvalidKey())
            return
        }
        try {
            val result = client.transcribe(window, apiKey, modelProvider())
            upsert(
                TranscriptionSegment(
                    windowIndex = window.index,
                    status = TranscriptionSegment.Status.Ok,
                    text = result.text,
                    contextDurationMs = window.contextDurationMs
                )
            )
            emptyAudio.remember(window.peakLevel, result.text.isNotBlank())
            textLog.log("transcription_window_text", mapOf("window" to window.index.toString(), "text" to result.text))
            rebuildProvisionalText()
        } catch (error: CancellationException) {
            throw error
        } catch (error: TranscriptionError.InvalidKey) {
            failFatally(window, error)
        } catch (error: TranscriptionError) {
            fail(window, error)
        } catch (error: Throwable) {
            fail(window, TranscriptionErrorClassifier.fromThrowable(error))
        }
    }

    // A janela silenciosa já ocupou uma vaga do teto na submissão: sem isso, uma captura muda
    // nunca atingiria o teto e o microfone ficaria aberto (P107).
    private suspend fun skipSilent(window: DictationWindow, reason: String) {
        upsert(TranscriptionSegment(windowIndex = window.index, status = TranscriptionSegment.Status.Ok))
        eventLog.log(
            "transcription_silent_window",
            buildMap {
                put("window", window.index.toString())
                put("durationMs", window.durationMs.toString())
                put("reason", reason)
                put("cut", window.cut.name.lowercase())
                // Quanto de fala foi medido na janela barrada: é o número que diz, no aparelho, se o
                // corte da P151 pegou silêncio mesmo ou se encostou em fala baixa.
                window.voicedMs?.let { put("voicedMs", it.toString()) }
            }
        )
    }

    private suspend fun failFatally(window: DictationWindow, error: TranscriptionError) {
        fail(window, error)
        fatalState.value = error
    }

    private suspend fun fail(window: DictationWindow, error: TranscriptionError) {
        upsert(
            TranscriptionSegment(
                windowIndex = window.index,
                status = TranscriptionSegment.Status.Failed,
                errorKind = when (error) {
                    is TranscriptionError.InvalidResponse -> error.message?.take(48) ?: error.kind
                    is TranscriptionError.Server -> "server_${error.statusCode}"
                    else -> error.kind
                }
            )
        )
        eventLog.log(
            "transcription_window_failed",
            mapOf(
                "window" to window.index.toString(),
                "durationMs" to window.durationMs.toString(),
                "model" to modelProvider(),
                "kind" to error.kind
            )
        )
    }

    private suspend fun upsert(segment: TranscriptionSegment) {
        segmentsMutex.withLock {
            val current = segmentState.value.toMutableList()
            val index = current.indexOfFirst { it.windowIndex == segment.windowIndex }
            if (index >= 0) {
                current[index] = segment
            } else {
                current += segment
                current.sortBy { it.windowIndex }
            }
            segmentState.value = current
        }
    }

    private fun rebuildProvisionalText() {
        provisional.value = segmentState.value
            .filter { it.status == TranscriptionSegment.Status.Ok && it.text.isNotBlank() }
            .joinToString(" ") { it.text.trim() }
    }
}
