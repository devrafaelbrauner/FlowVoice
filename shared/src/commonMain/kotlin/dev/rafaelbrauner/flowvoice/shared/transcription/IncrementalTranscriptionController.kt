package dev.rafaelbrauner.flowvoice.shared.transcription

import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow
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

    val provisionalText: StateFlow<String> = provisional.asStateFlow()
    val segments: StateFlow<List<TranscriptionSegment>> = segmentState.asStateFlow()
    val budgetExhausted: StateFlow<Boolean> = budgetState.asStateFlow()

    fun submit(window: DictationWindow) {
        if (cancelled) return
        val job = scope.launch {
            processMutex.withLock {
                if (!cancelled) {
                    process(window)
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
        provisional.value = ""
        segmentState.value = emptyList()
    }

    suspend fun awaitIdle() {
        jobs.toList().forEach { it.join() }
    }

    private suspend fun process(window: DictationWindow) {
        if (cancelled) return
        if (requestCount >= config.maxRequestsPerSession) {
            upsert(
                TranscriptionSegment(
                    windowIndex = window.index,
                    status = TranscriptionSegment.Status.Failed,
                    errorKind = TranscriptionError.SessionBudgetExceeded().kind
                )
            )
            budgetState.value = true
            eventLog.log(
                "transcription_budget",
                mapOf(
                    "window" to window.index.toString(),
                    "durationMs" to window.durationMs.toString(),
                    "model" to config.model
                )
            )
            return
        }
        upsert(
            TranscriptionSegment(
                windowIndex = window.index,
                status = TranscriptionSegment.Status.Transcribing
            )
        )
        val apiKey = apiKeyProvider().orEmpty()
        if (apiKey.isBlank()) {
            fail(window, TranscriptionError.InvalidKey())
            return
        }
        requestCount++
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
        } catch (error: TranscriptionError) {
            fail(window, error)
        } catch (error: Throwable) {
            fail(window, TranscriptionErrorClassifier.fromThrowable(error))
        }
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
