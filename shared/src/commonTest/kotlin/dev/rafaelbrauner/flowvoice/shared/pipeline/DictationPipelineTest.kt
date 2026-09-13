package dev.rafaelbrauner.flowvoice.shared.pipeline

import dev.rafaelbrauner.flowvoice.shared.dictation.AudioCaptureEngine
import dev.rafaelbrauner.flowvoice.shared.dictation.AudioFormat
import dev.rafaelbrauner.flowvoice.shared.dictation.AudioFrame
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationSessionController
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow
import dev.rafaelbrauner.flowvoice.shared.dictionary.InMemoryPersonalDictionary
import dev.rafaelbrauner.flowvoice.shared.insertion.TextInserter
import dev.rafaelbrauner.flowvoice.shared.insertion.TextInsertionResult
import dev.rafaelbrauner.flowvoice.shared.prefs.AppPreferences
import dev.rafaelbrauner.flowvoice.shared.prefs.InMemoryPreferencesStore
import dev.rafaelbrauner.flowvoice.shared.proofreading.ProofreadingClient
import dev.rafaelbrauner.flowvoice.shared.transcription.InMemorySecretStore
import dev.rafaelbrauner.flowvoice.shared.transcription.OpenRouterConfig
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionClient
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DictationPipelineTest {
    @Test
    fun finalizeInsertsTextFromEveryWindowIncludingFinalFlush() = runTest {
        val env = PipelineEnv(
            scope = backgroundScope,
            frames = listOf(frame(100L), frame(100L), frame(50L)),
            texts = mapOf(0 to "o médico", 1 to "pediu o exame", 2 to "de sangue")
        )

        env.pipeline.start()
        runCurrent()
        val status = env.pipeline.finalize()

        val completed = assertIs<DictationPipelineStatus.Completed>(status)
        assertEquals("o médico pediu o exame de sangue", completed.text)
        assertEquals(listOf("o médico pediu o exame de sangue"), env.inserter.inserted)
        assertEquals(3, env.pipeline.sessionWindows.value.size)
        assertEquals(status, env.pipeline.status.value)
    }

    @Test
    fun appliesDictionaryBeforeAndAfterProofreadingWhenEnabled() = runTest {
        val env = PipelineEnv(
            scope = backgroundScope,
            frames = listOf(frame(50L)),
            texts = mapOf(0 to "tomar dipirona"),
            preferences = AppPreferences(proofreadingEnabled = true)
        )
        env.dictionary.approve("Dipirona")

        env.pipeline.start()
        val status = env.pipeline.finalize()

        assertEquals(listOf("tomar Dipirona"), env.proofreader.received)
        assertEquals("Tomar Dipirona.", assertIs<DictationPipelineStatus.Completed>(status).text)
        assertEquals(listOf("Tomar Dipirona."), env.inserter.inserted)
    }

    @Test
    fun proofreadingFailureKeepsDictionaryText() = runTest {
        val env = PipelineEnv(
            scope = backgroundScope,
            frames = listOf(frame(50L)),
            texts = mapOf(0 to "tomar dipirona"),
            preferences = AppPreferences(proofreadingEnabled = true),
            proofreadingFails = true
        )
        env.dictionary.approve("Dipirona")

        env.pipeline.start()
        env.pipeline.finalize()

        assertEquals(listOf("tomar Dipirona"), env.inserter.inserted)
    }

    @Test
    fun blankTranscriptSkipsInsertion() = runTest {
        val env = PipelineEnv(
            scope = backgroundScope,
            frames = listOf(frame(50L)),
            texts = mapOf(0 to "")
        )

        env.pipeline.start()
        val status = assertIs<DictationPipelineStatus.Completed>(env.pipeline.finalize())

        assertEquals("", status.text)
        assertEquals(false, status.insertion.success)
        assertTrue(env.inserter.inserted.isEmpty())
    }

    @Test
    fun cancelWhileRecordingDoesNotInsert() = runTest {
        val env = PipelineEnv(scope = backgroundScope, frames = listOf(frame(50L)))

        env.pipeline.start()
        assertEquals(DictationPipelineStatus.Recording, env.pipeline.status.value)
        env.pipeline.cancel()
        advanceUntilIdle()

        assertEquals(DictationPipelineStatus.Cancelled, env.pipeline.status.value)
        assertEquals(1, env.engine.stopCount)
        assertTrue(env.inserter.inserted.isEmpty())
    }

    @Test
    fun cancelWhileTranscribingDoesNotInsert() = runTest {
        val env = PipelineEnv(
            scope = backgroundScope,
            frames = listOf(frame(50L)),
            texts = mapOf(0 to "texto"),
            transcriptionDelayMs = 1_000L
        )

        env.pipeline.start()
        val finalizing = async { env.pipeline.finalize() }
        runCurrent()
        assertEquals(DictationPipelineStatus.Transcribing, env.pipeline.status.value)
        env.pipeline.cancel()
        advanceUntilIdle()

        assertEquals(DictationPipelineStatus.Cancelled, finalizing.await())
        assertEquals(DictationPipelineStatus.Cancelled, env.pipeline.status.value)
        assertTrue(env.inserter.inserted.isEmpty())
    }

    @Test
    fun startFailureReportsFailedStatus() = runTest {
        val env = PipelineEnv(
            scope = backgroundScope,
            frames = emptyList(),
            startError = IllegalStateException("microfone indisponível")
        )

        env.pipeline.start()

        val failed = assertIs<DictationPipelineStatus.Failed>(env.pipeline.status.value)
        assertEquals("microfone indisponível", failed.message)
    }

    private fun frame(durationMs: Long): AudioFrame =
        AudioFrame(ByteArray((durationMs * 16).toInt() * 2), AudioFormat.DEFAULT)
}

private class PipelineEnv(
    scope: CoroutineScope,
    frames: List<AudioFrame>,
    texts: Map<Int, String> = emptyMap(),
    preferences: AppPreferences = AppPreferences(),
    proofreadingFails: Boolean = false,
    transcriptionDelayMs: Long = 0L,
    startError: Throwable? = null
) {
    val engine = ScriptedAudioCaptureEngine(frames, startError)
    val dictionary = InMemoryPersonalDictionary()
    val inserter = RecordingInserter()
    val proofreader = FakeProofreader(proofreadingFails)
    val pipeline = DictationPipeline(
        controller = DictationSessionController(engine, windowTargetDurationMs = 100L),
        client = ScriptedTranscriptionClient(texts, transcriptionDelayMs),
        config = OpenRouterConfig(),
        dictionary = dictionary,
        proofreading = proofreader,
        preferences = InMemoryPreferencesStore(preferences),
        secrets = InMemorySecretStore().apply { writeOpenRouterKey("sk-or-v1-testkey123456") },
        inserter = inserter,
        scope = scope
    )
}

private class RecordingInserter : TextInserter {
    val inserted = mutableListOf<String>()

    override val isAvailable: Boolean = true

    override fun insert(text: String): TextInsertionResult {
        inserted += text
        return TextInsertionResult(success = true, route = "teste", message = "ok")
    }
}

private class FakeProofreader(private val fails: Boolean) : ProofreadingClient {
    val received = mutableListOf<String>()

    override suspend fun proofread(text: String, apiKey: String, model: String): String {
        received += text
        if (fails) error("indisponível")
        return text.replaceFirstChar { it.uppercase() } + "."
    }
}

private class ScriptedTranscriptionClient(
    private val texts: Map<Int, String>,
    private val delayMs: Long
) : TranscriptionClient {
    override suspend fun transcribe(window: DictationWindow, apiKey: String, model: String?): TranscriptionResult {
        if (delayMs > 0L) delay(delayMs)
        return TranscriptionResult(texts[window.index] ?: "w${window.index}", "fake")
    }

    override fun cancel() = Unit
}

private class ScriptedAudioCaptureEngine(
    private val frames: List<AudioFrame>,
    private val startError: Throwable?
) : AudioCaptureEngine {
    override val format: AudioFormat = AudioFormat.DEFAULT
    var stopCount = 0
        private set
    private var running = false

    override val isRunning: Boolean
        get() = running

    override suspend fun start(onFrame: suspend (AudioFrame) -> Unit) {
        startError?.let { throw it }
        running = true
        for (frame in frames) {
            if (!running) break
            onFrame(frame)
        }
    }

    override fun stop() {
        stopCount++
        running = false
    }
}
