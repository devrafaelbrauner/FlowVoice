package dev.rafaelbrauner.flowvoice.shared.transcription

import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow
import dev.rafaelbrauner.flowvoice.shared.dictation.SilentWindow
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
    private val eventLog: TranscriptionEventLog = TranscriptionEventLog.NoOp
) {
    private val segmentsMutex = Mutex()
    private val processMutex = Mutex()
    private val provisional = MutableStateFlow("")
    private val segmentState = MutableStateFlow<List<TranscriptionSegment>>(emptyList())
    private val jobs = mutableListOf<Job>()
    private var cancelled = false
    private var requestCount = 0
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

    fun reset() {
        jobs.toList().forEach { it.cancel() }
        jobs.clear()
        cancelled = false
        requestCount = 0
        budgetState.value = false
        fatalState.value = null
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
                "model" to config.model
            )
        )
    }

    private suspend fun process(window: DictationWindow) {
        if (cancelled) return
        if (SilentWindow.detect(window)) {
            skipSilent(window)
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
        val apiKey = apiKeyProvider().orEmpty()
        if (apiKey.isBlank()) {
            failFatally(window, TranscriptionError.InvalidKey())
            return
        }
        try {
            val result = client.transcribe(window, apiKey)
            upsert(
                TranscriptionSegment(
                    windowIndex = window.index,
                    status = TranscriptionSegment.Status.Ok,
                    text = result.text
                )
            )
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
    private suspend fun skipSilent(window: DictationWindow) {
        upsert(TranscriptionSegment(windowIndex = window.index, status = TranscriptionSegment.Status.Ok))
        eventLog.log(
            "transcription_silent_window",
            mapOf(
                "window" to window.index.toString(),
                "durationMs" to window.durationMs.toString()
            )
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
                "model" to config.model,
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
