package dev.rafaelbrauner.flowvoice.shared.pipeline

import dev.rafaelbrauner.flowvoice.shared.dictation.AudioCaptureEngine
import dev.rafaelbrauner.flowvoice.shared.dictation.AudioCaptureException
import dev.rafaelbrauner.flowvoice.shared.dictation.AudioFormat
import dev.rafaelbrauner.flowvoice.shared.dictation.AudioFrame
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationSessionController
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow
import dev.rafaelbrauner.flowvoice.shared.dictionary.InMemoryPersonalDictionary
import dev.rafaelbrauner.flowvoice.shared.insertion.TextInserter
import dev.rafaelbrauner.flowvoice.shared.insertion.TextInsertionResult
import dev.rafaelbrauner.flowvoice.shared.localasr.FakeSpeechEngine
import dev.rafaelbrauner.flowvoice.shared.localasr.FakeSpeechEngines
import dev.rafaelbrauner.flowvoice.shared.localasr.TranscriptionEngine
import dev.rafaelbrauner.flowvoice.shared.notes.InMemoryNoteStore
import dev.rafaelbrauner.flowvoice.shared.notes.NoteDictationCoordinator
import dev.rafaelbrauner.flowvoice.shared.prefs.AppPreferences
import dev.rafaelbrauner.flowvoice.shared.prefs.InMemoryPreferencesStore
import dev.rafaelbrauner.flowvoice.shared.proofreading.ProofreadingClient
import dev.rafaelbrauner.flowvoice.shared.transcription.InMemorySecretStore
import dev.rafaelbrauner.flowvoice.shared.transcription.OpenRouterConfig
import dev.rafaelbrauner.flowvoice.shared.transcription.RecordingLog
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionClient
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionResult
import dev.rafaelbrauner.flowvoice.shared.transcription.voicedPcm
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

// O motor no aparelho pelo pipeline inteiro: cada janela cortada fecha um pedaço do fluxo contínuo,
// e o pedaço segue o mesmo caminho das janelas da nuvem (digitação direta, revisão, nota).
@OptIn(ExperimentalCoroutinesApi::class)
class DictationPipelineLocalEngineTest {
    @Test
    fun theDirectDictationTypesWholeWordsPerCutKeepsThePartialOffTheFieldAndFlushesAtStop() = runTest {
        val env = LocalEnv(
            this,
            FakeSpeechEngine(
                emitted = mapOf(1 to listOf(" Bom", " d"), 2 to listOf("ia", " tudo"), 3 to listOf(" bem")),
                heldUntilFinalize = mapOf(3 to listOf("?"))
            )
        )

        env.pipeline.start(DictationTarget.ActiveField)
        env.speak(1)

        assertEquals("Bom", env.field.toString())
        assertEquals("d", env.pipeline.livePartial.value)
        assertTrue(env.pipeline.liveText().provisional.endsWith("d"))

        env.speak(2)
        env.speak(3)
        assertEquals("Bom dia tudo", env.field.toString(), "a palavra partida no corte só entra inteira")

        val completed = assertIs<DictationPipelineStatus.Completed>(env.pipeline.finalize())

        assertEquals("Bom dia tudo bem?", env.field.toString())
        assertEquals("Bom dia tudo bem?", completed.text)
        assertEquals(listOf("Bom", " dia", " tudo", " bem?"), env.inserter.writes)
        assertEquals("", env.pipeline.livePartial.value)
        assertTrue(env.client.requested.isEmpty(), "nada vai à nuvem")
        assertEquals(1, env.engines.created.size, "um fluxo só por ditado")
    }

    @Test
    fun theLocalEngineStartsWithoutKeyAndSkipsTheAiRevisionForLackOfIt() = runTest {
        val env = LocalEnv(
            this,
            FakeSpeechEngine(emitted = mapOf(1 to listOf(" exame", " normal"))),
            preferences = AppPreferences(transcriptionEngine = TranscriptionEngine.Local, proofreadingEnabled = true),
            apiKey = null
        )

        env.pipeline.start(DictationTarget.ActiveField)
        assertEquals(DictationPipelineStatus.Recording, env.pipeline.status.value)
        env.speak(1)
        val completed = assertIs<DictationPipelineStatus.Completed>(env.pipeline.finalize())

        assertEquals("exame normal", completed.text)
        assertTrue(env.proofreader.received.isEmpty())
        assertEquals(
            DictationProofread.REASON_NO_KEY,
            env.log.events.single { it.event == "dictation_proofread_skipped" }.metadata["reason"]
        )
    }

    @Test
    fun aWordRepeatedAcrossPiecesIsKeptBecauseTheStreamNeverOverlaps() = runTest {
        val env = LocalEnv(
            this,
            FakeSpeechEngine(emitted = mapOf(1 to listOf(" muito", " muito"), 2 to listOf(" bem", "."), 3 to listOf(" ")))
        )

        env.pipeline.start(DictationTarget.ActiveField)
        env.speak(1)
        env.speak(2)
        env.speak(3)
        env.pipeline.finalize()

        assertEquals("muito muito bem.", env.field.toString())
    }

    @Test
    fun theReviewBarGetsTheWholeLocalTextToInsert() = runTest {
        val env = LocalEnv(
            this,
            FakeSpeechEngine(emitted = mapOf(1 to listOf(" Hemograma"), 2 to listOf(" sem", " alterações"))),
            preferences = AppPreferences(transcriptionEngine = TranscriptionEngine.Local, reviewBeforeInsert = true)
        )

        env.pipeline.start(DictationTarget.ActiveField)
        env.speak(1)
        assertEquals("Hemograma", env.pipeline.liveText().provisional)
        env.speak(2)
        val ready = assertIs<DictationPipelineStatus.Ready>(env.pipeline.finalizeForReview())
        assertEquals("Hemograma sem alterações", ready.text)
        assertTrue(env.inserter.writes.isEmpty(), "nada é digitado antes do Inserir")

        env.pipeline.insertReady()
        assertEquals("Hemograma sem alterações", env.field.toString())
    }

    @Test
    fun aNoteReceivesTheLocalText() = runTest {
        val env = LocalEnv(
            this,
            FakeSpeechEngine(emitted = mapOf(1 to listOf(" Paciente", " estável")), heldUntilFinalize = mapOf(1 to listOf(".")))
        )
        val store = InMemoryNoteStore()
        val note = store.create(body = "")
        val coordinator = NoteDictationCoordinator(store).apply { attach(env.pipeline.session, backgroundScope) }
        coordinator.begin(note.id, env.pipeline.session.value)

        env.pipeline.start(DictationTarget.Note)
        env.speak(1)
        assertEquals("estável", env.pipeline.livePartial.value)
        val completed = assertIs<DictationPipelineStatus.Completed>(env.pipeline.finalize())
        runCurrent()

        assertEquals("Paciente estável.", completed.text)
        assertEquals("Paciente estável.", store.get(note.id)?.body)
        assertTrue(env.inserter.writes.isEmpty())
    }

    @Test
    fun anEngineFailureMidDictationKeepsTheTextFinishesWithWarningAndNeverSwitchesToTheCloud() = runTest {
        val env = LocalEnv(
            this,
            FakeSpeechEngine(emitted = mapOf(1 to listOf(" Pressão", " doze"), 2 to listOf(" por")), failOnChunk = 3),
            apiKey = "sk-or-v1-testkey123456"
        )

        env.pipeline.start(DictationTarget.ActiveField)
        env.speak(1)
        env.speak(2)
        env.speak(3)
        runCurrent()

        val completed = assertIs<DictationPipelineStatus.Completed>(env.pipeline.status.value)
        assertEquals("Pressão doze por", completed.text)
        assertEquals("Pressão doze por", env.field.toString())
        assertTrue(completed.warning.orEmpty().startsWith("Motor no aparelho falhou"), completed.warning)
        assertTrue(env.client.requested.isEmpty(), "a nuvem não entra no lugar do motor sem o usuário escolher")
        assertTrue(env.log.events.any { it.event == "dictation_engine_stop" })
    }

    @Test
    fun anEngineThatNeverLoadsFailsTheDictationWithoutText() = runTest {
        val env = LocalEnv(this, FakeSpeechEngine(failOnStart = true))

        env.pipeline.start(DictationTarget.ActiveField)
        env.speak(1)
        runCurrent()

        val failed = assertIs<DictationPipelineStatus.Failed>(env.pipeline.status.value)
        assertEquals("O motor no aparelho falhou e nada foi transcrito", failed.message)
    }

    @Test
    fun theLocalDictationStopsAtTheTimeCapWithTheTextSoFar() = runTest {
        val env = LocalEnv(this, FakeSpeechEngine(emitted = mapOf(1 to listOf(" longo", " ditado"))))

        env.pipeline.start(DictationTarget.ActiveField)
        env.speak(1)
        advanceTimeBy(DictationPipeline.LOCAL_SESSION_CAP - 1.seconds)
        assertEquals(DictationPipelineStatus.Recording, env.pipeline.status.value)
        advanceTimeBy(2.seconds)
        runCurrent()

        val completed = assertIs<DictationPipelineStatus.Completed>(env.pipeline.status.value)
        assertEquals("longo ditado", completed.text)
        assertEquals(DictationPipeline.LOCAL_SESSION_CAP_WARNING, completed.warning)
        assertTrue(env.log.events.any { it.event == "dictation_time_cap_stop" })
    }

    @Test
    fun withoutTheModelTheCloudRulesApplyAndTheKeyIsRequired() = runTest {
        val env = LocalEnv(this, FakeSpeechEngine(), installed = false, apiKey = null)

        assertEquals(TranscriptionEngine.Cloud, env.pipeline.effectiveEngine())
        env.pipeline.start(DictationTarget.ActiveField)

        assertIs<DictationPipelineStatus.Failed>(env.pipeline.status.value)
        assertTrue(env.engines.created.isEmpty())
    }

    @Test
    fun choosingTheCloudKeepsUsingTheCloudEvenWithTheModelInstalled() = runTest {
        val env = LocalEnv(
            this,
            FakeSpeechEngine(),
            preferences = AppPreferences(transcriptionEngine = TranscriptionEngine.Cloud),
            apiKey = "sk-or-v1-testkey123456"
        )

        env.pipeline.start(DictationTarget.ActiveField)
        env.speak(1)
        env.pipeline.finalize()

        assertEquals(listOf(0), env.client.requested)
        assertTrue(env.engines.created.isEmpty())
        assertEquals("nuvem", env.field.toString())
    }

    @Test
    fun cancelDiscardsThePartialAndClosesTheStream() = runTest {
        val engine = FakeSpeechEngine(emitted = mapOf(1 to listOf(" Bom", " d")))
        val env = LocalEnv(this, engine)

        env.pipeline.start(DictationTarget.ActiveField)
        env.speak(1)
        env.pipeline.cancel()
        runCurrent()

        assertEquals(DictationPipelineStatus.Cancelled, env.pipeline.status.value)
        assertEquals("", env.pipeline.livePartial.value)
        assertEquals("Bom", env.field.toString(), "o que já foi digitado fica; o parcial não entra")
        assertEquals(1, engine.closed)
    }
}

private class LocalEnv(
    scope: TestScope,
    engine: FakeSpeechEngine,
    preferences: AppPreferences = AppPreferences(transcriptionEngine = TranscriptionEngine.Local),
    apiKey: String? = null,
    installed: Boolean = true
) {
    private val testScope = scope
    val log = RecordingLog()
    val capture = PushedAudioCaptureEngine()
    val inserter = FieldInserter()
    val field: StringBuilder get() = inserter.field
    val proofreader = RecordingProofreader()
    val client = CloudClient()
    val engines = FakeSpeechEngines(StandardTestDispatcher(scope.testScheduler), installed) { engine }
    val pipeline = DictationPipeline(
        controller = DictationSessionController(capture, windowTargetDurationMs = 100L),
        client = client,
        config = OpenRouterConfig(),
        dictionary = InMemoryPersonalDictionary(),
        proofreading = proofreader,
        preferences = InMemoryPreferencesStore(preferences),
        secrets = InMemorySecretStore().apply { apiKey?.let { writeOpenRouterKey(it) } },
        inserter = inserter,
        scope = scope.backgroundScope,
        eventLog = log,
        localEngines = engines
    )

    // Um trecho de fala de 100 ms: fecha uma janela no teto (sem pausa).
    @Suppress("UNUSED_PARAMETER")
    suspend fun speak(chunk: Int) {
        capture.push(AudioFrame(voicedPcm(100 * 16 * 2), AudioFormat.DEFAULT))
        testScope.runCurrent()
    }
}

private class PushedAudioCaptureEngine : AudioCaptureEngine {
    override val format: AudioFormat = AudioFormat.DEFAULT
    private var sink: (suspend (AudioFrame) -> Unit)? = null
    private var running = false

    override val isRunning: Boolean
        get() = running

    override suspend fun start(onFrame: suspend (AudioFrame) -> Unit) = start(onFrame, onError = {})

    override suspend fun start(onFrame: suspend (AudioFrame) -> Unit, onError: suspend (AudioCaptureException) -> Unit) {
        sink = onFrame
        running = true
    }

    override fun stop() {
        running = false
    }

    suspend fun push(frame: AudioFrame) {
        checkNotNull(sink) { "engine not started" }.invoke(frame)
    }
}

// Campo de texto que o app lê e escreve, como o da acessibilidade quando tudo dá certo.
private class FieldInserter : TextInserter {
    val field = StringBuilder()
    val writes = mutableListOf<String>()

    override val isAvailable: Boolean = true

    override fun insert(text: String): TextInsertionResult {
        field.append(text)
        return TextInsertionResult(success = true, route = "teste", message = "ok")
    }

    override fun insertWithoutTap(text: String, deleteBefore: Int): TextInsertionResult {
        field.setLength((field.length - deleteBefore).coerceAtLeast(0))
        field.append(text)
        writes += text
        return TextInsertionResult(success = true, route = "teste", message = "ok")
    }

    override fun readBeforeCursor(limit: Int): String = field.takeLast(limit).toString()
}

private class RecordingProofreader : ProofreadingClient {
    val received = mutableListOf<String>()

    override suspend fun proofread(text: String, apiKey: String, model: String): String {
        received += text
        return text
    }
}

private class CloudClient : TranscriptionClient {
    val requested = mutableListOf<Int>()

    override suspend fun transcribe(window: DictationWindow, apiKey: String, model: String?): TranscriptionResult {
        requested += window.index
        return TranscriptionResult("nuvem", "fake")
    }
}
