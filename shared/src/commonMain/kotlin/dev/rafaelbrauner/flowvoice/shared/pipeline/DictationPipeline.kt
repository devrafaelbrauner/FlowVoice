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
    private val eventLog: TranscriptionEventLog = TranscriptionEventLog.NoOp
) {
    private val eventLines = MutableSharedFlow<String>(extraBufferCapacity = EVENT_BUFFER)
    private val transcription = IncrementalTranscriptionController(
        client = client,
        config = config,
        scope = scope,
        apiKeyProvider = { secrets.readOpenRouterKey() },
        eventLog = { event, metadata -> log(event, metadata) }
    )
    private val statusState = MutableStateFlow<DictationPipelineStatus>(DictationPipelineStatus.Idle)
    private val windowsState = MutableStateFlow<List<DictationWindow>>(emptyList())
    private val submittedWindows = MutableStateFlow(0)
    private var sessionToken = 0

    val status: StateFlow<DictationPipelineStatus> = statusState.asStateFlow()
    val sessionState: StateFlow<DictationSessionState> = controller.state
    val segments: StateFlow<List<TranscriptionSegment>> = transcription.segments
    val sessionWindows: StateFlow<List<DictationWindow>> = windowsState.asStateFlow()
    val events: SharedFlow<String> = eventLines.asSharedFlow()

    val capturedDurationMs: Long
        get() = controller.capturedDurationMs

    val model: String
        get() = config.model

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            controller.windows.collect { window ->
                windowsState.value = windowsState.value + window
                transcription.submit(window)
                submittedWindows.value += 1
                log(
                    "dictation_window",
                    mapOf(
                        "window" to (window.index + 1).toString(),
                        "durationMs" to window.durationMs.toString(),
                        "model" to config.model
                    )
                )
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            controller.state.collect { state ->
                val active = statusState.value
                val capturing = active == DictationPipelineStatus.Starting ||
                    active == DictationPipelineStatus.Recording
                if (state is DictationSessionState.Error && capturing) {
                    statusState.value = DictationPipelineStatus.Failed(state.message)
                    log("dictation_failed", mapOf("stage" to "capture"))
                }
            }
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

    suspend fun start() {
        if (statusState.value.isBusy) return
        val token = ++sessionToken
        statusState.value = DictationPipelineStatus.Starting
        transcription.reset()
        windowsState.value = emptyList()
        submittedWindows.value = 0
        try {
            controller.start()
            if (token != sessionToken) {
                controller.cancel()
                return
            }
            if (statusState.value == DictationPipelineStatus.Starting) {
                statusState.value = DictationPipelineStatus.Recording
                log("dictation_started", emptyMap())
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (token == sessionToken) {
                statusState.value = DictationPipelineStatus.Failed(error.message ?: "erro desconhecido")
                log("dictation_failed", mapOf("stage" to "start"))
            }
        }
    }

    suspend fun finalize(): DictationPipelineStatus {
        if (statusState.value != DictationPipelineStatus.Recording) return statusState.value
        val token = sessionToken
        statusState.value = DictationPipelineStatus.Transcribing
        val outcome = try {
            controller.finalize()
            submittedWindows.first { it >= controller.emittedWindowCount }
            transcription.awaitIdle()
            val failures = TranscriptionFailureSummary.from(
                segments = transcription.segments.value,
                totalWindows = controller.emittedWindowCount
            )
            val text = reviseFinalText()
            if (token != sessionToken) {
                DictationPipelineStatus.Cancelled
            } else if (text.isBlank() && failures != null) {
                log(
                    "dictation_failed",
                    mapOf("stage" to "transcription", "failedWindows" to failures.failedCount.toString())
                )
                DictationPipelineStatus.Failed(failures.noTextMessage)
            } else {
                dictionary.suggestFrom(text)
                val insertion = if (text.isBlank()) {
                    TextInsertionResult(success = false, route = "pipeline", message = "sem texto para inserir")
                } else {
                    inserter.insert(text)
                }
                log(
                    "dictation_finalized",
                    mapOf(
                        "chars" to text.length.toString(),
                        "inserted" to insertion.success.toString(),
                        "failedWindows" to (failures?.failedCount ?: 0).toString()
                    )
                )
                DictationPipelineStatus.Completed(text, insertion, warning = failures?.partialMessage)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log("dictation_failed", mapOf("stage" to "finalize"))
            DictationPipelineStatus.Failed(error.message ?: "erro desconhecido")
        }
        if (token == sessionToken) {
            statusState.value = outcome
        }
        return outcome
    }

    suspend fun cancel() {
        if (!statusState.value.isBusy) return
        sessionToken++
        transcription.cancel()
        try {
            controller.cancel()
        } finally {
            statusState.value = DictationPipelineStatus.Cancelled
            log("dictation_cancelled", emptyMap())
        }
    }

    fun requestStart(): Job = scope.launch { start() }

    fun requestFinalize(): Job = scope.launch { finalize() }

    fun requestCancel(): Job = scope.launch { cancel() }

    private suspend fun reviseFinalText(): String {
        val assembled = LivePreviewAssembler.assemble(
            segments = transcription.segments.value,
            sessionComplete = true
        )
        val revised = dictionary.apply(assembled.finalized)
        val prefs = preferences.read()
        if (!prefs.proofreadingEnabled || revised.isBlank()) return revised
        val apiKey = secrets.readOpenRouterKey().orEmpty()
        if (apiKey.isBlank()) return revised
        return try {
            dictionary.apply(proofreading.proofread(revised, apiKey, prefs.proofreadingModel)).also {
                log("proofreading_applied", mapOf("chars" to it.length.toString()))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            log("proofreading_unavailable", emptyMap())
            revised
        }
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

    private companion object {
        const val EVENT_BUFFER = 64
    }
}
