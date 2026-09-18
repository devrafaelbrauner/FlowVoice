package dev.rafaelbrauner.flowvoice.shared.transcription

import dev.rafaelbrauner.flowvoice.shared.api.OpenRouterTranscriptionClient
import dev.rafaelbrauner.flowvoice.shared.session.DictationConfig
import dev.rafaelbrauner.flowvoice.shared.session.FinalizedStreamText
import dev.rafaelbrauner.flowvoice.shared.session.SessionState
import dev.rafaelbrauner.flowvoice.shared.session.StreamRouter
import dev.rafaelbrauner.flowvoice.shared.session.StreamText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

data class WindowResult(
    val text: String,
    val latencyMs: Long,
    val modelId: String
)

data class IncrementalReport(
    val windows: Int,
    val totalLatencyMs: Long,
    val modelId: String,
    val promptTokens: Int,
    val droppedTerms: Int
)

class TranscriptionManager(
    private val router: StreamRouter,
    private val api: TranscriptionApi,
    private val config: DictationConfig = DictationConfig(),
    val defaultModel: String = DEFAULT_MODEL,
    private val scope: CoroutineScope
) {

    private val workerId = AtomicLong(0)
    private var workerJob: Job? = null

    private val mutableState = MutableStateFlow(SessionState.IDLE)
    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    private val mutableLiveText = MutableStateFlow(StreamText("", ""))
    val liveText: StateFlow<StreamText> = mutableLiveText.asStateFlow()

    private val managerScope = CoroutineScope(scope.coroutineContext + SupervisorJob())

    fun start() {
        if (router.isOpen) return
        val channel = router.open()
        val id = workerId.incrementAndGet()
        mutableState.value = SessionState.LISTENING
        mutableLiveText.value = StreamText("", "")
        workerJob = managerScope.launch {
            runWorker(id, channel)
        }
    }

    suspend fun stop(apiKey: String, model: String = defaultModel): Pair<String, IncrementalReport?> {
        val channel = router.take() ?: return "" to null
        val reply = kotlinx.coroutines.CompletableDeferred<FinalizedStreamText?>()
        channel.send(dev.rafaelbrauner.flowvoice.shared.session.StreamCmd.Finalize(reply))
        val finalized = reply.await()
        workerJob?.join()
        workerJob = null
        mutableState.value = SessionState.IDLE
        if (finalized == null || finalized.audio.isEmpty()) return "" to null
        return transcribeWindows(finalized.audio, apiKey, model = model)
    }

    suspend fun cancel() {
        val channel = router.take()
        channel?.send(dev.rafaelbrauner.flowvoice.shared.session.StreamCmd.Cancel)
        workerJob?.cancelAndJoin()
        workerJob = null
        mutableState.value = SessionState.IDLE
        mutableLiveText.value = StreamText("", "")
    }

    suspend fun snapshot(): FloatArray {
        val channel = router.current() ?: return FloatArray(0)
        val reply = kotlinx.coroutines.CompletableDeferred<FloatArray>()
        channel.send(dev.rafaelbrauner.flowvoice.shared.session.StreamCmd.Snapshot(reply))
        return reply.await()
    }

    fun publishTentative(text: String) {
        val current = mutableLiveText.value
        mutableLiveText.value = current.copy(tentative = text)
    }

    private suspend fun runWorker(id: Long, channel: Channel<dev.rafaelbrauner.flowvoice.shared.session.StreamCmd>) {
        val pcm = mutableListOf<Float>()
        for (cmd in channel) {
            if (id != workerId.get()) break
            when (cmd) {
                is dev.rafaelbrauner.flowvoice.shared.session.StreamCmd.Feed -> {
                    pcm.addAll(cmd.pcm.asList())
                    mutableState.value = SessionState.LISTENING
                }
                is dev.rafaelbrauner.flowvoice.shared.session.StreamCmd.Finalize -> {
                    mutableState.value = SessionState.WORKING
                    pcm.addAll(collectAudio(channel, cmd).asList())
                    val finalized = if (pcm.isEmpty()) {
                        null
                    } else {
                        FinalizedStreamText("", pcm.toFloatArray(), config.sampleRateHz)
                    }
                    cmd.reply.complete(finalized)
                    break
                }
                is dev.rafaelbrauner.flowvoice.shared.session.StreamCmd.Snapshot -> {
                    cmd.reply.complete(pcm.toFloatArray())
                }
                dev.rafaelbrauner.flowvoice.shared.session.StreamCmd.Cancel -> break
            }
        }
    }

    private suspend fun collectAudio(
        channel: Channel<dev.rafaelbrauner.flowvoice.shared.session.StreamCmd>,
        finalize: dev.rafaelbrauner.flowvoice.shared.session.StreamCmd.Finalize
    ): FloatArray {
        val pending = mutableListOf<Float>()
        var cmd = channel.tryReceive().getOrNull()
        while (cmd != null &&
            cmd !is dev.rafaelbrauner.flowvoice.shared.session.StreamCmd.Finalize &&
            cmd !is dev.rafaelbrauner.flowvoice.shared.session.StreamCmd.Snapshot
        ) {
            if (cmd is dev.rafaelbrauner.flowvoice.shared.session.StreamCmd.Feed) {
                pending.addAll(cmd.pcm.asList())
            }
            if (cmd is dev.rafaelbrauner.flowvoice.shared.session.StreamCmd.Cancel) break
            cmd = channel.tryReceive().getOrNull()
        }
        if (cmd is dev.rafaelbrauner.flowvoice.shared.session.StreamCmd.Finalize && cmd !== finalize) {
            cmd.reply.complete(null)
        }
        return pending.toFloatArray()
    }

    suspend fun transcribeWindows(
        audio: FloatArray,
        apiKey: String,
        language: String = "pt",
        prompt: String? = null,
        model: String = defaultModel,
        fallbackModel: String? = FALLBACK_MODEL
    ): Pair<String, IncrementalReport> {
        val windowSamples = (config.windowMs * config.sampleRateHz / 1000).toInt()
        val overlapSamples = (config.overlapMs * config.sampleRateHz / 1000).toInt()
        val step = (windowSamples - overlapSamples).coerceAtLeast(1)
        val windows = splitWindows(audio, windowSamples, step)
        val committed = StringBuilder()
        var totalLatency = 0L
        var usedModel = model
        for (window in windows) {
            val wav = OpenRouterTranscriptionClient.wavBytes(window, config.sampleRateHz)
            val result = transcribeWithFallback(
                wav = wav,
                model = model,
                fallbackModel = fallbackModel,
                language = language,
                prompt = prompt,
                apiKey = apiKey
            )
            usedModel = result.modelId
            totalLatency += result.latencyMs
            val merged = mergeOverlap(committed.toString(), result.text, overlapWords = 6)
            committed.clear().append(merged)
            mutableLiveText.value = StreamText(committed.toString(), result.text)
        }
        mutableLiveText.value = StreamText(committed.toString(), "")
        return committed.toString() to IncrementalReport(
            windows = windows.size,
            totalLatencyMs = totalLatency,
            modelId = usedModel,
            promptTokens = 0,
            droppedTerms = 0
        )
    }

    private suspend fun transcribeWithFallback(
        wav: ByteArray,
        model: String,
        fallbackModel: String?,
        language: String,
        prompt: String?,
        apiKey: String
    ): TranscriptionResult {
        try {
            return api.transcribe(
                TranscriptionRequest(
                    audio = wav,
                    model = model,
                    language = language,
                    prompt = prompt
                ),
                apiKey
            )
        } catch (e: TranscriptionError.ModelNotFound) {
            val fallback = fallbackModel ?: throw e
            if (fallback == model) throw e
            val result = api.transcribe(
                TranscriptionRequest(
                    audio = wav,
                    model = fallback,
                    language = language,
                    prompt = prompt
                ),
                apiKey
            )
            return result.copy(modelId = "${result.modelId} (fallback de $model)")
        }
    }

    internal fun splitWindows(audio: FloatArray, windowSamples: Int, step: Int): List<FloatArray> {
        if (audio.isEmpty()) return emptyList()
        if (audio.size <= windowSamples) return listOf(audio.copyOf())
        val out = mutableListOf<FloatArray>()
        var start = 0
        while (start < audio.size) {
            val end = minOf(start + windowSamples, audio.size)
            out.add(audio.copyOfRange(start, end))
            if (end == audio.size) break
            start += step
        }
        return out
    }

    internal fun mergeOverlap(committed: String, next: String, overlapWords: Int = 6): String {
        if (committed.isEmpty()) return next
        if (next.isEmpty()) return committed
        val committedWords = committed.split(" ")
        val nextWords = next.split(" ")
        val maxOverlap = minOf(overlapWords, committedWords.size, nextWords.size)
        for (size in maxOverlap downTo 1) {
            val tail = committedWords.takeLast(size).joinToString(" ")
            val head = nextWords.take(size).joinToString(" ")
            if (tail.equals(head, ignoreCase = true)) {
                return (committedWords + nextWords.drop(size)).joinToString(" ")
            }
        }
        return "$committed $next"
    }

    companion object {
        const val DEFAULT_MODEL = "microsoft/mai-transcribe-2"
        const val FALLBACK_MODEL = "openai/whisper-large-v3-turbo"
    }
}
