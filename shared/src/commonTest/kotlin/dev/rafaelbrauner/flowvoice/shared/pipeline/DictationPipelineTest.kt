package dev.rafaelbrauner.flowvoice.shared.pipeline

import dev.rafaelbrauner.flowvoice.shared.dictation.AudioCaptureEngine
import dev.rafaelbrauner.flowvoice.shared.dictation.AudioFormat
import dev.rafaelbrauner.flowvoice.shared.dictation.AudioFrame
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationSessionController
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationSessionState
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow
import dev.rafaelbrauner.flowvoice.shared.dictionary.InMemoryPersonalDictionary
import dev.rafaelbrauner.flowvoice.shared.insertion.TextInserter
import dev.rafaelbrauner.flowvoice.shared.insertion.TextInsertionResult
import dev.rafaelbrauner.flowvoice.shared.notes.InMemoryNoteStore
import dev.rafaelbrauner.flowvoice.shared.notes.NoteDictationCoordinator
import dev.rafaelbrauner.flowvoice.shared.prefs.AppPreferences
import dev.rafaelbrauner.flowvoice.shared.prefs.InMemoryPreferencesStore
import dev.rafaelbrauner.flowvoice.shared.proofreading.ProofreadingClient
import dev.rafaelbrauner.flowvoice.shared.transcription.InMemorySecretStore
import dev.rafaelbrauner.flowvoice.shared.transcription.OpenRouterConfig
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionClient
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionError
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
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
    fun failedMiddleWindowInsertsPartialTextWithWarning() = runTest {
        val env = PipelineEnv(
            scope = backgroundScope,
            frames = listOf(frame(100L), frame(100L), frame(50L)),
            texts = mapOf(0 to "o médico", 2 to "de sangue"),
            failures = mapOf(1 to TranscriptionError.Timeout())
        )

        env.pipeline.start()
        runCurrent()
        val completed = assertIs<DictationPipelineStatus.Completed>(env.pipeline.finalize())

        assertEquals(listOf("o médico de sangue"), env.inserter.inserted)
        assertEquals("Trecho 2 de 3 falhou (timeout): texto incompleto", completed.warning)
    }

    @Test
    fun allWindowsFailingWithoutKeyReportsKeyAndSkipsInsertion() = runTest {
        val env = PipelineEnv(
            scope = backgroundScope,
            frames = listOf(frame(100L), frame(50L)),
            apiKey = null
        )

        env.pipeline.start()
        runCurrent()
        val failed = assertIs<DictationPipelineStatus.Failed>(env.pipeline.finalize())

        assertTrue(failed.message.contains("chave OpenRouter"), failed.message)
        assertTrue(env.inserter.inserted.isEmpty())
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

    @Test
    fun secondStartWhileStartingIsIgnored() = runTest {
        val gated = GatedAudioCaptureEngine()
        val env = PipelineEnv(scope = backgroundScope, frames = emptyList(), captureEngine = gated)

        val starting = async { env.pipeline.start() }
        runCurrent()
        assertEquals(DictationPipelineStatus.Starting, env.pipeline.status.value)
        env.pipeline.start()
        assertEquals(DictationPipelineStatus.Starting, env.pipeline.status.value)
        gated.release()
        starting.await()

        assertEquals(DictationPipelineStatus.Recording, env.pipeline.status.value)
        assertEquals(1, gated.startCount)
    }

    @Test
    fun cancelWhileStartingEndsCancelledAndStopsCapture() = runTest {
        val gated = GatedAudioCaptureEngine()
        val env = PipelineEnv(scope = backgroundScope, frames = emptyList(), captureEngine = gated)

        val starting = async { env.pipeline.start() }
        runCurrent()
        assertEquals(DictationPipelineStatus.Starting, env.pipeline.status.value)
        env.pipeline.cancel()
        gated.release()
        starting.await()
        advanceUntilIdle()

        assertEquals(DictationPipelineStatus.Cancelled, env.pipeline.status.value)
        assertIs<DictationSessionState.Cancelled>(env.pipeline.sessionState.value)
        assertFalse(gated.isRunning)
        assertTrue(env.inserter.inserted.isEmpty())
    }

    @Test
    fun twoConsecutiveSessionsInSamePipelineUseFreshTextAndCounters() = runTest {
        val env = PipelineEnv(
            scope = backgroundScope,
            frames = listOf(frame(100L), frame(50L)),
            texts = mapOf(0 to "primeira", 1 to "sessão")
        )

        env.pipeline.start()
        runCurrent()
        val first = assertIs<DictationPipelineStatus.Completed>(env.pipeline.finalize())
        env.client.texts = mapOf(0 to "segunda", 1 to "fala")
        env.pipeline.start()
        runCurrent()
        val second = assertIs<DictationPipelineStatus.Completed>(env.pipeline.finalize())

        assertEquals("primeira sessão", first.text)
        assertEquals("segunda fala", second.text)
        assertEquals(listOf("primeira sessão", "segunda fala"), env.inserter.inserted)
        assertEquals(2, env.pipeline.sessionWindows.value.size)
    }

    @Test
    fun failingInserterCompletesWithoutSuccess() = runTest {
        val env = PipelineEnv(
            scope = backgroundScope,
            frames = listOf(frame(50L)),
            texts = mapOf(0 to "texto"),
            inserterSucceeds = false
        )

        env.pipeline.start()
        runCurrent()
        val completed = assertIs<DictationPipelineStatus.Completed>(env.pipeline.finalize())

        assertEquals("texto", completed.text)
        assertFalse(completed.insertion.success)
        assertEquals(listOf("texto"), env.inserter.inserted)
    }

    @Test
    fun captureErrorWhileRecordingReportsFailed() = runTest {
        val manual = ManualAudioCaptureEngine()
        val env = PipelineEnv(scope = backgroundScope, frames = emptyList(), captureEngine = manual)

        env.pipeline.start()
        assertEquals(DictationPipelineStatus.Recording, env.pipeline.status.value)
        manual.push(AudioFrame(ByteArray(3_200), AudioFormat(sampleRate = 8_000)))
        runCurrent()

        val failed = assertIs<DictationPipelineStatus.Failed>(env.pipeline.status.value)
        assertTrue(failed.message.contains("frame does not match capture format"), failed.message)
        assertFalse(manual.isRunning)
        assertTrue(env.inserter.inserted.isEmpty())
    }

    @Test
    fun framesDeliveredFromAnotherThreadAfterStartStillIncludeLastWindow() = runTest {
        val threaded = BackgroundAudioCaptureEngine(listOf(frame(100L), frame(100L), frame(50L)))
        val env = PipelineEnv(
            scope = backgroundScope,
            frames = emptyList(),
            texts = mapOf(0 to "o médico", 1 to "pediu o exame", 2 to "de sangue"),
            captureEngine = threaded
        )

        env.pipeline.start()
        threaded.awaitDelivered()
        val completed = assertIs<DictationPipelineStatus.Completed>(env.pipeline.finalize())

        assertEquals("o médico pediu o exame de sangue", completed.text)
        assertEquals(3, env.pipeline.sessionWindows.value.size)
        assertEquals(listOf("o médico pediu o exame de sangue"), env.inserter.inserted)
    }

    @Test
    fun finalizeForReviewStopsAtReadyWithoutInserting() = runTest {
        val env = PipelineEnv(
            scope = backgroundScope,
            frames = listOf(frame(100L), frame(50L)),
            texts = mapOf(0 to "o médico", 1 to "pediu o exame")
        )

        env.pipeline.start()
        runCurrent()
        val ready = assertIs<DictationPipelineStatus.Ready>(env.pipeline.finalizeForReview())

        assertEquals("o médico pediu o exame", ready.text)
        assertEquals(ready, env.pipeline.status.value)
        assertTrue(env.pipeline.status.value.isBusy)
        assertTrue(ready.latencyMs != null)
        assertTrue(env.inserter.inserted.isEmpty())
    }

    @Test
    fun insertReadyInsertsOnce() = runTest {
        val env = PipelineEnv(
            scope = backgroundScope,
            frames = listOf(frame(50L)),
            texts = mapOf(0 to "o médico")
        )

        env.pipeline.start()
        runCurrent()
        val ready = assertIs<DictationPipelineStatus.Ready>(env.pipeline.finalizeForReview())
        val completed = assertIs<DictationPipelineStatus.Completed>(env.pipeline.insertReady())
        env.pipeline.insertReady()

        assertTrue(completed.insertion.success)
        assertEquals("o médico", completed.text)
        assertEquals(ready.latencyMs, completed.latencyMs)
        assertEquals(completed, env.pipeline.status.value)
        assertEquals(listOf("o médico"), env.inserter.inserted)
    }

    @Test
    fun cancelFromReadyDoesNotInsert() = runTest {
        val env = PipelineEnv(
            scope = backgroundScope,
            frames = listOf(frame(50L)),
            texts = mapOf(0 to "o médico")
        )

        env.pipeline.start()
        runCurrent()
        assertIs<DictationPipelineStatus.Ready>(env.pipeline.finalizeForReview())
        env.pipeline.cancel()
        env.pipeline.insertReady()

        assertEquals(DictationPipelineStatus.Cancelled, env.pipeline.status.value)
        assertTrue(env.inserter.inserted.isEmpty())
    }

    @Test
    fun readyCarriesWarningWhenAWindowFails() = runTest {
        val env = PipelineEnv(
            scope = backgroundScope,
            frames = listOf(frame(100L), frame(100L), frame(50L)),
            texts = mapOf(0 to "o médico", 2 to "de sangue"),
            failures = mapOf(1 to TranscriptionError.Timeout())
        )

        env.pipeline.start()
        runCurrent()
        val ready = assertIs<DictationPipelineStatus.Ready>(env.pipeline.finalizeForReview())

        assertEquals("o médico de sangue", ready.text)
        assertEquals("Trecho 2 de 3 falhou (timeout): texto incompleto", ready.warning)
        assertTrue(env.inserter.inserted.isEmpty())
    }

    @Test
    fun reviewWithoutAnyTranscribedWindowFails() = runTest {
        val env = PipelineEnv(
            scope = backgroundScope,
            frames = listOf(frame(100L), frame(50L)),
            apiKey = null
        )

        env.pipeline.start()
        runCurrent()
        val failed = assertIs<DictationPipelineStatus.Failed>(env.pipeline.finalizeForReview())

        assertTrue(failed.message.contains("chave OpenRouter"), failed.message)
        assertTrue(env.inserter.inserted.isEmpty())
    }

    @Test
    fun startWhileReadyIsIgnored() = runTest {
        val env = PipelineEnv(
            scope = backgroundScope,
            frames = listOf(frame(50L)),
            texts = mapOf(0 to "o médico")
        )

        env.pipeline.start()
        runCurrent()
        val ready = assertIs<DictationPipelineStatus.Ready>(env.pipeline.finalizeForReview())
        env.pipeline.start()

        assertEquals(ready, env.pipeline.status.value)
        assertTrue(env.inserter.inserted.isEmpty())
    }

    @Test
    fun startCapturesInsertionTargetBeforeRecording() = runTest {
        val env = PipelineEnv(scope = backgroundScope, frames = listOf(frame(50L)))

        env.pipeline.start()

        assertEquals(1, env.inserter.captures)
        assertEquals(DictationPipelineStatus.Recording, env.pipeline.status.value)
    }

    @Test
    fun refusedInsertionKeepsReadyWithTextAndReason() = runTest {
        val inserter = ScriptedInserter(outcomes = mutableListOf(false, true))
        val env = PipelineEnv(
            scope = backgroundScope,
            frames = listOf(frame(50L)),
            texts = mapOf(0 to "o médico"),
            textInserter = inserter
        )

        env.pipeline.start()
        runCurrent()
        assertIs<DictationPipelineStatus.Ready>(env.pipeline.finalizeForReview())
        val refused = assertIs<DictationPipelineStatus.Ready>(env.pipeline.insertReady())

        assertEquals("o médico", refused.text)
        assertEquals("foco mudou", refused.refusal)
        assertEquals(refused, env.pipeline.status.value)
        assertTrue(env.pipeline.status.value.isBusy)

        val completed = assertIs<DictationPipelineStatus.Completed>(env.pipeline.insertReady())
        assertTrue(completed.insertion.success)
        assertEquals(listOf("o médico", "o médico"), inserter.attempts)
        assertEquals(listOf("o médico"), inserter.inserted)
    }

    @Test
    fun cancelAfterRefusedInsertionDoesNotInsert() = runTest {
        val inserter = ScriptedInserter(outcomes = mutableListOf(false))
        val env = PipelineEnv(
            scope = backgroundScope,
            frames = listOf(frame(50L)),
            texts = mapOf(0 to "o médico"),
            textInserter = inserter
        )

        env.pipeline.start()
        runCurrent()
        assertIs<DictationPipelineStatus.Ready>(env.pipeline.finalizeForReview())
        assertIs<DictationPipelineStatus.Ready>(env.pipeline.insertReady())
        env.pipeline.cancel()
        env.pipeline.insertReady()

        assertEquals(DictationPipelineStatus.Cancelled, env.pipeline.status.value)
        assertTrue(inserter.inserted.isEmpty())
    }

    @Test
    fun finalizeForReviewCapturesTargetWhenStartSawNoneAndInsertRefusesAnotherApp() = runTest {
        val inserter = TargetInserter()
        val env = PipelineEnv(
            scope = backgroundScope,
            frames = listOf(frame(50L)),
            texts = mapOf(0 to "o médico"),
            textInserter = inserter
        )

        env.pipeline.start()
        runCurrent()
        assertNull(inserter.target)

        inserter.focused = "com.whatsapp"
        assertIs<DictationPipelineStatus.Ready>(env.pipeline.finalizeForReview())
        assertEquals("com.whatsapp", inserter.target)

        inserter.focused = "com.android.chrome"
        val refused = assertIs<DictationPipelineStatus.Ready>(env.pipeline.insertReady())
        assertEquals(TargetInserter.CHANGED, refused.refusal)
        assertTrue(inserter.inserted.isEmpty())

        inserter.focused = "com.whatsapp"
        assertIs<DictationPipelineStatus.Completed>(env.pipeline.insertReady())
        assertEquals(listOf("o médico"), inserter.inserted)
    }

    @Test
    fun budgetStopDoesNotPinTheDestinationSoInsertGoesWhereTheUserTaps() = runTest {
        val inserter = TargetInserter()
        val env = PipelineEnv(
            scope = backgroundScope,
            frames = List(4) { frame(100L) },
            texts = mapOf(0 to "um", 1 to "dois", 2 to "três", 3 to "quatro"),
            textInserter = inserter,
            config = OpenRouterConfig(maxRequestsPerSession = 2)
        )

        env.pipeline.start()
        assertNull(inserter.target)
        inserter.focused = "com.whatsapp"
        runCurrent()
        assertIs<DictationPipelineStatus.Ready>(env.pipeline.status.value)
        assertNull(inserter.target)

        inserter.focused = "com.android.chrome"
        assertIs<DictationPipelineStatus.Completed>(env.pipeline.insertReady())
        assertEquals("com.android.chrome", inserter.target)
        assertEquals(listOf("um dois"), inserter.inserted)
    }

    @Test
    fun retryAfterRefusedInsertRecapturesTheDestination() = runTest {
        val inserter = TargetInserter()
        val env = PipelineEnv(
            scope = backgroundScope,
            frames = listOf(frame(50L)),
            texts = mapOf(0 to "o médico"),
            textInserter = inserter
        )

        inserter.focused = "com.whatsapp"
        env.pipeline.start()
        runCurrent()
        assertIs<DictationPipelineStatus.Ready>(env.pipeline.finalizeForReview())

        inserter.focused = "com.android.chrome"
        val refused = assertIs<DictationPipelineStatus.Ready>(env.pipeline.insertReady())
        assertEquals(TargetInserter.CHANGED, refused.refusal)
        assertTrue(inserter.inserted.isEmpty())

        assertIs<DictationPipelineStatus.Completed>(env.pipeline.insertReady())
        assertEquals("com.android.chrome", inserter.target)
        assertEquals(listOf("o médico"), inserter.inserted)
    }

    @Test
    fun noteSessionReachingRequestBudgetDeliversToTheNoteWithoutTouchingTheInserter() = runTest {
        val inserter = CallRecordingInserter()
        val env = PipelineEnv(
            scope = backgroundScope,
            frames = List(4) { frame(100L) },
            texts = mapOf(0 to "um", 1 to "dois", 2 to "três", 3 to "quatro"),
            textInserter = inserter,
            config = OpenRouterConfig(maxRequestsPerSession = 2)
        )
        val note = NoteHarness(env.pipeline, backgroundScope)

        env.pipeline.start(DictationTarget.Note)
        runCurrent()

        val completed = assertIs<DictationPipelineStatus.Completed>(env.pipeline.status.value)
        assertEquals(1, env.engine.stopCount)
        assertEquals("um dois", completed.text)
        assertTrue(completed.warning.orEmpty().contains("teto de requisições da sessão"), completed.warning)
        assertFalse(completed.insertion.success)
        assertEquals(emptyList(), inserter.calls)
        assertEquals("um dois", note.body())
    }

    @Test
    fun noteSessionFinalizedManuallyDeliversToTheNoteWithoutTouchingTheInserter() = runTest {
        val inserter = CallRecordingInserter()
        val env = PipelineEnv(
            scope = backgroundScope,
            frames = listOf(frame(100L), frame(50L)),
            texts = mapOf(0 to "o médico", 1 to "pediu o exame"),
            textInserter = inserter
        )
        val note = NoteHarness(env.pipeline, backgroundScope)

        env.pipeline.start(DictationTarget.Note)
        runCurrent()
        val completed = assertIs<DictationPipelineStatus.Completed>(env.pipeline.finalize())
        runCurrent()

        assertEquals("o médico pediu o exame", completed.text)
        assertEquals(emptyList(), inserter.calls)
        assertEquals("o médico pediu o exame", note.body())
    }

    @Test
    fun noteSessionDrivenByTheOverlayControlsNeverInserts() = runTest {
        val inserter = CallRecordingInserter()
        val env = PipelineEnv(
            scope = backgroundScope,
            frames = listOf(frame(50L)),
            texts = mapOf(0 to "o médico"),
            textInserter = inserter
        )
        val note = NoteHarness(env.pipeline, backgroundScope)

        env.pipeline.start(DictationTarget.Note)
        runCurrent()
        env.pipeline.finalizeForReview()
        env.pipeline.insertReady()
        runCurrent()

        assertEquals(emptyList(), inserter.calls)
        assertEquals("o médico", note.body())
        assertFalse(env.pipeline.status.value.isBusy)
    }

    @Test
    fun noteSessionStoppedFromTheBubbleGoesStraightToTheNoteAndReleasesThePipeline() = runTest {
        val inserter = CallRecordingInserter()
        val env = PipelineEnv(
            scope = backgroundScope,
            frames = listOf(frame(100L), frame(50L)),
            texts = mapOf(0 to "o médico", 1 to "pediu o exame"),
            textInserter = inserter
        )
        val note = NoteHarness(env.pipeline, backgroundScope)

        env.pipeline.start(DictationTarget.Note)
        runCurrent()
        val completed = assertIs<DictationPipelineStatus.Completed>(env.pipeline.finalizeForReview())
        runCurrent()

        assertEquals("o médico pediu o exame", completed.text)
        assertFalse(env.pipeline.status.value.isBusy)
        assertEquals("o médico pediu o exame", note.body())
        assertEquals(emptyList(), inserter.calls)
    }

    @Test
    fun startWithoutKeyFailsBeforeOpeningTheMicrophone() = runTest {
        val env = PipelineEnv(scope = backgroundScope, frames = listOf(frame(100L)), apiKey = null)

        env.pipeline.start()
        runCurrent()

        val failed = assertIs<DictationPipelineStatus.Failed>(env.pipeline.status.value)
        assertEquals("chave OpenRouter ausente ou inválida", failed.message)
        assertEquals(0, env.engine.startCount)
        assertEquals(0, env.inserter.captures)
        assertTrue(env.inserter.inserted.isEmpty())
    }

    @Test
    fun noteSessionWithoutKeyTellsTheNoteWithoutTouchingTheInserter() = runTest {
        val inserter = CallRecordingInserter()
        val env = PipelineEnv(
            scope = backgroundScope,
            frames = listOf(frame(100L)),
            apiKey = null,
            textInserter = inserter
        )
        val note = NoteHarness(env.pipeline, backgroundScope)

        env.pipeline.start(DictationTarget.Note)
        runCurrent()

        assertIs<DictationPipelineStatus.Failed>(env.pipeline.status.value)
        assertEquals("chave OpenRouter ausente ou inválida", note.message())
        assertEquals(emptyList(), inserter.calls)
        assertEquals("", note.body())
    }

    @Test
    fun secondNoteAttemptWithoutKeyEndsThatSessionSoAnotherAppsTextNeverReachesTheNote() = runTest {
        val env = PipelineEnv(
            scope = backgroundScope,
            frames = listOf(frame(50L)),
            texts = mapOf(0 to "texto do whatsapp"),
            apiKey = null
        )
        val note = NoteHarness(env.pipeline, backgroundScope)

        env.pipeline.start(DictationTarget.Note)
        runCurrent()
        note.begin()
        env.pipeline.start(DictationTarget.Note)
        runCurrent()

        assertEquals("chave OpenRouter ausente ou inválida", note.message())
        assertNull(note.activeNoteId())

        env.secrets.writeOpenRouterKey("sk-or-v1-testkey123456")
        env.pipeline.start()
        runCurrent()
        env.pipeline.finalizeForReview()
        env.pipeline.insertReady()
        runCurrent()

        assertEquals(listOf("texto do whatsapp"), env.inserter.inserted)
        assertEquals("", note.body())
    }

    @Test
    fun keyRejectedMidSessionFailsTheSessionAndStopsCapture() = runTest {
        val env = PipelineEnv(
            scope = backgroundScope,
            frames = List(4) { frame(100L) },
            texts = mapOf(0 to "um"),
            failures = mapOf(1 to TranscriptionError.InvalidKey())
        )

        env.pipeline.start()
        runCurrent()

        val failed = assertIs<DictationPipelineStatus.Failed>(env.pipeline.status.value)
        assertEquals("chave OpenRouter ausente ou inválida", failed.message)
        assertEquals(1, env.engine.stopCount)
        assertFalse(env.engine.isRunning)
        assertTrue(env.inserter.inserted.isEmpty())
    }

    @Test
    fun reachingRequestBudgetInActiveFieldSessionStopsCaptureAtReady() = runTest {
        val env = PipelineEnv(
            scope = backgroundScope,
            frames = List(4) { frame(100L) },
            texts = mapOf(0 to "um", 1 to "dois", 2 to "três", 3 to "quatro"),
            config = OpenRouterConfig(maxRequestsPerSession = 2)
        )

        env.pipeline.start()
        runCurrent()

        val ready = assertIs<DictationPipelineStatus.Ready>(env.pipeline.status.value)
        assertEquals(1, env.engine.stopCount)
        assertEquals("um dois", ready.text)
        assertTrue(ready.warning.orEmpty().contains("teto de requisições da sessão"), ready.warning)
        assertTrue(env.inserter.inserted.isEmpty())
    }

    private fun frame(durationMs: Long): AudioFrame =
        AudioFrame(ByteArray((durationMs * 16).toInt() * 2), AudioFormat.DEFAULT)

    private class TargetInserter : TextInserter {
        var focused: String? = null
        var target: String? = null
        val inserted = mutableListOf<String>()

        override val isAvailable: Boolean = true

        override fun captureTarget() {
            target = focused
        }

        override fun captureTargetIfUnknown() {
            if (target == null) target = focused
        }

        override fun insert(text: String): TextInsertionResult {
            val expected = target
            if (expected != null && focused != null && focused != expected) {
                return TextInsertionResult(success = false, route = "teste", message = CHANGED)
            }
            inserted += text
            return TextInsertionResult(success = true, route = "teste", message = "ok")
        }

        companion object {
            const val CHANGED = "o foco mudou de app"
        }
    }
}

private class PipelineEnv(
    scope: CoroutineScope,
    frames: List<AudioFrame>,
    texts: Map<Int, String> = emptyMap(),
    preferences: AppPreferences = AppPreferences(),
    proofreadingFails: Boolean = false,
    transcriptionDelayMs: Long = 0L,
    startError: Throwable? = null,
    failures: Map<Int, Throwable> = emptyMap(),
    apiKey: String? = "sk-or-v1-testkey123456",
    captureEngine: AudioCaptureEngine? = null,
    inserterSucceeds: Boolean = true,
    textInserter: TextInserter? = null,
    config: OpenRouterConfig = OpenRouterConfig()
) {
    val engine = ScriptedAudioCaptureEngine(frames, startError)
    val dictionary = InMemoryPersonalDictionary()
    val inserter = RecordingInserter(inserterSucceeds)
    val proofreader = FakeProofreader(proofreadingFails)
    val client = ScriptedTranscriptionClient(texts, transcriptionDelayMs, failures)
    val secrets = InMemorySecretStore().apply { apiKey?.let { writeOpenRouterKey(it) } }
    val pipeline = DictationPipeline(
        controller = DictationSessionController(captureEngine ?: engine, windowTargetDurationMs = 100L),
        client = client,
        config = config,
        dictionary = dictionary,
        proofreading = proofreader,
        preferences = InMemoryPreferencesStore(preferences),
        secrets = secrets,
        inserter = textInserter ?: inserter,
        scope = scope
    )
}

private class NoteHarness(private val pipeline: DictationPipeline, scope: CoroutineScope) {
    private val store = InMemoryNoteStore()
    private val coordinator = NoteDictationCoordinator(store).apply { attach(pipeline.session, scope) }
    private val noteId = store.create(body = "").id

    init {
        begin()
    }

    fun begin() {
        coordinator.begin(noteId, pipeline.session.value)
    }

    fun activeNoteId(): String? = coordinator.activeNoteId

    fun body(): String? = store.get(noteId)?.body

    fun message(): String? = coordinator.state.value.message?.text
}

private class CallRecordingInserter : TextInserter {
    val calls = mutableListOf<String>()

    override val isAvailable: Boolean = true

    override fun captureTarget() {
        calls += "captureTarget"
    }

    override fun captureTargetIfUnknown() {
        calls += "captureTargetIfUnknown"
    }

    override fun insert(text: String): TextInsertionResult {
        calls += "insert:$text"
        return TextInsertionResult(success = true, route = "teste", message = "ok")
    }
}

private class ScriptedInserter(private val outcomes: MutableList<Boolean>) : TextInserter {
    val attempts = mutableListOf<String>()
    val inserted = mutableListOf<String>()

    override val isAvailable: Boolean = true

    override fun insert(text: String): TextInsertionResult {
        attempts += text
        val succeeds = if (outcomes.isEmpty()) true else outcomes.removeAt(0)
        if (!succeeds) return TextInsertionResult(success = false, route = "teste", message = "foco mudou")
        inserted += text
        return TextInsertionResult(success = true, route = "teste", message = "ok")
    }
}

private class RecordingInserter(private val succeeds: Boolean = true) : TextInserter {
    val inserted = mutableListOf<String>()
    var captures = 0

    override val isAvailable: Boolean = true

    override fun captureTarget() {
        captures += 1
    }

    override fun insert(text: String): TextInsertionResult {
        inserted += text
        return if (succeeds) {
            TextInsertionResult(success = true, route = "teste", message = "ok")
        } else {
            TextInsertionResult(success = false, route = "teste", message = "campo indisponível")
        }
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
    var texts: Map<Int, String>,
    private val delayMs: Long,
    private val failures: Map<Int, Throwable> = emptyMap()
) : TranscriptionClient {
    override suspend fun transcribe(window: DictationWindow, apiKey: String, model: String?): TranscriptionResult {
        if (delayMs > 0L) delay(delayMs)
        failures[window.index]?.let { throw it }
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
    var startCount = 0
        private set
    private var running = false

    override val isRunning: Boolean
        get() = running

    override suspend fun start(onFrame: suspend (AudioFrame) -> Unit) {
        startCount++
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

private class GatedAudioCaptureEngine : AudioCaptureEngine {
    override val format: AudioFormat = AudioFormat.DEFAULT
    private val gate = CompletableDeferred<Unit>()
    var startCount = 0
        private set
    private var running = false

    override val isRunning: Boolean
        get() = running

    override suspend fun start(onFrame: suspend (AudioFrame) -> Unit) {
        startCount++
        running = true
        gate.await()
    }

    override fun stop() {
        running = false
    }

    fun release() {
        gate.complete(Unit)
    }
}

private class ManualAudioCaptureEngine : AudioCaptureEngine {
    override val format: AudioFormat = AudioFormat.DEFAULT
    private var sink: (suspend (AudioFrame) -> Unit)? = null
    private var running = false

    override val isRunning: Boolean
        get() = running

    override suspend fun start(onFrame: suspend (AudioFrame) -> Unit) {
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

private class BackgroundAudioCaptureEngine(private val frames: List<AudioFrame>) : AudioCaptureEngine {
    override val format: AudioFormat = AudioFormat.DEFAULT
    private var delivery: Job? = null
    private var running = false

    override val isRunning: Boolean
        get() = running

    override suspend fun start(onFrame: suspend (AudioFrame) -> Unit) {
        running = true
        delivery = CoroutineScope(Dispatchers.Default).launch {
            for (frame in frames) {
                if (!running) break
                onFrame(frame)
            }
        }
    }

    override fun stop() {
        running = false
    }

    suspend fun awaitDelivered() {
        checkNotNull(delivery) { "engine not started" }.join()
    }
}
