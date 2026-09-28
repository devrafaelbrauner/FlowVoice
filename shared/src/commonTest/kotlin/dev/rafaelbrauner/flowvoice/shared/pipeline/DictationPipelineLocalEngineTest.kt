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
import dev.rafaelbrauner.flowvoice.shared.model.CloudModels
import dev.rafaelbrauner.flowvoice.shared.notes.InMemoryNoteStore
import dev.rafaelbrauner.flowvoice.shared.notes.NoteDictationCoordinator
import dev.rafaelbrauner.flowvoice.shared.prefs.AppPreferences
import dev.rafaelbrauner.flowvoice.shared.prefs.FormattingMode
import dev.rafaelbrauner.flowvoice.shared.prefs.InMemoryPreferencesStore
import dev.rafaelbrauner.flowvoice.shared.proofreading.AudioChatClient
import dev.rafaelbrauner.flowvoice.shared.proofreading.ProofreadingClient
import dev.rafaelbrauner.flowvoice.shared.transcription.InMemorySecretStore
import dev.rafaelbrauner.flowvoice.shared.transcription.OpenRouterConfig
import dev.rafaelbrauner.flowvoice.shared.transcription.RecordingLog
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionClient
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionError
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionResult
import dev.rafaelbrauner.flowvoice.shared.transcription.voicedPcm
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
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
    fun theLocalEngineStartsWithoutKeyAndKeepsTheDraftWithoutTheFinalPass() = runTest {
        val env = LocalEnv(
            this,
            FakeSpeechEngine(emitted = mapOf(1 to listOf(" exame", " normal"))),
            preferences = AppPreferences(formattingMode = FormattingMode.Llm, transcriptionEngine = TranscriptionEngine.Local, proofreadingEnabled = true),
            apiKey = null
        )

        env.pipeline.start(DictationTarget.ActiveField)
        assertEquals(DictationPipelineStatus.Recording, env.pipeline.status.value)
        env.speak(1)
        val completed = assertIs<DictationPipelineStatus.Completed>(env.pipeline.finalize())

        assertEquals("exame normal", completed.text)
        assertEquals(null, completed.warning, "sem chave não é falha: nada foi tentado")
        assertTrue(env.client.requested.isEmpty())
        assertTrue(env.proofreader.received.isEmpty())
        assertEquals(
            DictationProofread.REASON_NO_KEY,
            env.log.events.single { it.event == "final_pass_skipped" }.metadata["reason"]
        )
    }

    // Duas passadas: o Nemotron digita o rascunho ao vivo; ao parar, o áudio inteiro vai uma vez à
    // nuvem, a formatação acerta a concordância e o texto final troca o rascunho no campo.
    @Test
    fun theDraftIsTypedLiveAndReplacedByTheFinalTextFromTheWholeAudio() = runTest {
        val env = finalPassEnv(this, FakeSpeechEngine(emitted = mapOf(1 to listOf(" os", " exame"), 2 to listOf(" foi", " pedido"), 3 to listOf(" ontem"))))
        env.client.reply = { "os exame foi pedido ontem" }
        env.proofreader.reply = { "Os exames foram pedidos ontem." }

        env.pipeline.start(DictationTarget.ActiveField)
        env.speak(1)
        env.speak(2)
        env.speak(3)
        assertEquals("os exame foi pedido", env.field.toString(), "o rascunho entra ao vivo")

        val completed = assertIs<DictationPipelineStatus.Completed>(env.pipeline.finalize())

        assertEquals("Os exames foram pedidos ontem.", env.field.toString())
        assertEquals("Os exames foram pedidos ontem.", completed.text)
        assertEquals(null, completed.warning)
        assertEquals(listOf(3 * FRAME_BYTES), env.client.pcmSizes, "um pedido só, com o áudio inteiro da sessão")
        assertEquals(listOf("os exame foi pedido ontem"), env.proofreader.received, "a formatação recebe a transcrição da nuvem")
        assertTrue(env.log.events.any { it.event == "final_pass_applied" })
        val done = env.log.events.single { it.event == "final_pass_done" }.metadata
        assertEquals("300", done["audioMs"])
        assertEquals("ok", done["format"])
        assertTrue(env.log.events.none { it.metadata.values.any { value -> value.contains("exame") } }, "texto ditado nunca vai ao log")
        assertFalse(env.pipeline.directInsertion.value.proofreading)
    }

    @Test
    fun aFailedCloudTranscriptionKeepsTheWholeDraftWithoutAWarning() = runTest {
        val env = finalPassEnv(this, FakeSpeechEngine(emitted = mapOf(1 to listOf(" exame", " normal"))))
        env.client.reply = { throw TranscriptionError.Network(IllegalStateException("sem rede")) }

        env.pipeline.start(DictationTarget.ActiveField)
        env.speak(1)
        val completed = assertIs<DictationPipelineStatus.Completed>(env.pipeline.finalize())

        assertEquals("exame normal", env.field.toString())
        assertNull(completed.warning, "sem trecho falho, o rascunho está inteiro: nada a avisar")
        assertTrue(env.proofreader.received.isEmpty())
        assertEquals(FinalPass.REASON_TRANSCRIPTION_ERROR, env.log.events.single { it.event == "final_pass_skipped" }.metadata["reason"])
    }

    @Test
    fun aCloudThatDoesNotAnswerWithinTheCapKeepsTheDraft() = runTest {
        val env = finalPassEnv(this, FakeSpeechEngine(emitted = mapOf(1 to listOf(" exame", " normal"))))
        env.client.reply = {
            delay(FinalPass.TIMEOUT + 1.seconds)
            "Exame normal."
        }

        env.pipeline.start(DictationTarget.ActiveField)
        env.speak(1)
        val completed = assertIs<DictationPipelineStatus.Completed>(env.pipeline.finalize())

        assertEquals("exame normal", env.field.toString())
        assertNull(completed.warning, "sem trecho falho, o rascunho está inteiro: nada a avisar")
        assertEquals(FinalPass.REASON_TIMEOUT, env.log.events.single { it.event == "final_pass_skipped" }.metadata["reason"])
    }

    @Test
    fun aCloudAnswerMuchShorterThanTheDraftKeepsTheDraft() = runTest {
        val env = finalPassEnv(this, FakeSpeechEngine(emitted = mapOf(1 to listOf(" paciente", " estável", " sem", " febre", " desde", " ontem"))))
        env.client.reply = { "Paciente." }

        env.pipeline.start(DictationTarget.ActiveField)
        env.speak(1)
        env.pipeline.finalize()

        assertEquals("paciente estável sem febre desde ontem", env.field.toString())
        assertEquals(FinalPass.REASON_COVERAGE, env.log.events.single { it.event == "final_pass_skipped" }.metadata["reason"])
    }

    // Só a formatação falhou ou foi recusada: vale a transcrição da nuvem como veio, que já é melhor
    // que o rascunho e não tem contra o que ser conferida além dela mesma.
    @Test
    fun whenOnlyTheFormattingFailsTheRawCloudTranscriptReplacesTheDraft() = runTest {
        val env = finalPassEnv(this, FakeSpeechEngine(emitted = mapOf(1 to listOf(" eu", " pnica")), heldUntilFinalize = mapOf(1 to listOf(" hoje"))))
        env.client.reply = { "Eupneica hoje." }
        env.proofreader.reply = { throw TranscriptionError.Timeout() }

        env.pipeline.start(DictationTarget.ActiveField)
        env.speak(1)
        val completed = assertIs<DictationPipelineStatus.Completed>(env.pipeline.finalize())

        assertEquals("Eupneica hoje.", env.field.toString())
        assertEquals(null, completed.warning)
        assertEquals("erro_formatacao", env.log.events.single { it.event == "final_pass_done" }.metadata["format"])
    }

    // A escolha de Ajustes vale para o motor do aparelho também: com o passo único, o áudio inteiro vai ao
    // modelo de áudio e a transcrição da nuvem não é pedida.
    @Test
    fun theOneStepChoiceAlsoDrivesTheLocalFinalPass() = runTest {
        val env = finalPassEnv(
            this,
            FakeSpeechEngine(emitted = mapOf(1 to listOf(" a", " paciente", " está", " eu", " pnica"))),
            formattingMode = FormattingMode.OneStep
        )
        env.audioChat.reply = { "A paciente está eupneica." }

        env.pipeline.start(DictationTarget.ActiveField)
        env.speak(1)
        env.pipeline.finalize()

        assertEquals("A paciente está eupneica.", env.field.toString())
        assertEquals(listOf(CloudModels.DEFAULT_ONE_STEP_MODEL), env.audioChat.models)
        assertTrue(env.client.requested.isEmpty())
        assertTrue(env.proofreader.received.isEmpty())
    }

    @Test
    fun aFormattingThatSwapsAWordIsMergedBackAgainstTheCloudTranscript() = runTest {
        val env = finalPassEnv(this, FakeSpeechEngine(emitted = mapOf(1 to listOf(" suspendi", " a", " predinisona")))) 
        env.client.reply = { "suspendi a prednisona" }
        env.proofreader.reply = { "Suspendi a prednisolona." }

        env.pipeline.start(DictationTarget.ActiveField)
        env.speak(1)
        env.pipeline.finalize()

        assertEquals("Suspendi a prednisona.", env.field.toString(), "o remédio fica o da transcrição; a pontuação entra")
        assertEquals("1", env.log.events.single { it.event == "final_pass_done" }.metadata["keptWords"])
    }

    @Test
    fun cancelDiscardsTheSessionAudioAndNothingGoesToTheCloud() = runTest {
        val env = finalPassEnv(this, FakeSpeechEngine(emitted = mapOf(1 to listOf(" Bom", " dia"))))

        env.pipeline.start(DictationTarget.ActiveField)
        env.speak(1)
        env.speak(2)
        assertEquals(2, env.pipeline.sessionWindows.value.size)
        env.pipeline.cancel()
        runCurrent()

        assertTrue(env.pipeline.sessionWindows.value.isEmpty(), "o áudio da sessão sai da memória")
        assertTrue(env.client.requested.isEmpty())
        assertTrue(env.log.events.none { it.event.startsWith("final_pass") })
    }

    // B (S26, 2026-09-28): o toque que chegava como cancelar enquanto a passada final estava a caminho
    // descartava o texto final e deixava o rascunho. Depois do toque de parar, o fim segue e aplica.
    @Test
    fun aCancelDuringTheFinalPassStillAppliesTheFinalText() = runTest {
        val env = finalPassEnv(this, FakeSpeechEngine(emitted = mapOf(1 to listOf(" exame", " normal"))))
        env.client.reply = {
            delay(2.seconds)
            "Exame normal."
        }

        env.pipeline.start(DictationTarget.ActiveField)
        env.speak(1)
        val finalizing = backgroundScope.async { env.pipeline.finalize() }
        advanceTimeBy(1.seconds)
        assertTrue(env.pipeline.directInsertion.value.proofreading, "o cartão mostra revisando…")
        env.pipeline.cancel()
        advanceTimeBy(2.seconds)
        runCurrent()

        assertIs<DictationPipelineStatus.Completed>(finalizing.await())
        assertEquals("Exame normal.", env.field.toString())
        assertTrue(env.log.events.any { it.event == "final_pass_applied" })
        assertTrue(env.log.events.any { it.event == "dictation_cancel_ignored" })
    }

    // Y2: depois de "Inserir aqui" noutro campo o ditado não está mais inteiro antes do cursor, e a
    // troca apagaria texto que não é do FlowVoice.
    @Test
    fun afterInsertHereInAnotherFieldTheFinalPassDoesNotReplace() = runTest {
        val env = finalPassEnv(this, FakeSpeechEngine(emitted = mapOf(1 to listOf(" primeiro"), 2 to listOf(" segundo"), 3 to listOf(" terceiro"))))

        env.pipeline.start(DictationTarget.ActiveField)
        env.speak(1)
        env.inserter.refuseNext = true
        env.speak(2)
        assertEquals("primeiro", env.pipeline.directInsertion.value.pending, "o foco foi para outro campo")
        advanceTimeBy(1_100)
        env.pipeline.insertPending()
        env.speak(3)
        env.pipeline.finalize()

        assertEquals("primeiro segundo terceiro", env.field.toString())
        assertTrue(env.client.requested.isEmpty(), "sem troca possível, o áudio nem vai à nuvem")
        assertEquals(DictationProofread.REASON_NOT_CONTIGUOUS, env.log.events.single { it.event == "final_pass_skipped" }.metadata["reason"])
    }

    @Test
    fun aNoteReceivesTheFinalTextInsteadOfTheDraft() = runTest {
        val env = finalPassEnv(this, FakeSpeechEngine(emitted = mapOf(1 to listOf(" paciente", " estavel"))))
        env.client.reply = { "paciente estável" }
        env.proofreader.reply = { "Paciente estável." }
        val store = InMemoryNoteStore()
        val note = store.create(body = "")
        NoteDictationCoordinator(store).apply { attach(env.pipeline.session, backgroundScope) }.begin(note.id, env.pipeline.session.value)

        env.pipeline.start(DictationTarget.Note)
        env.speak(1)
        val completed = assertIs<DictationPipelineStatus.Completed>(env.pipeline.finalize())
        runCurrent()

        assertEquals("Paciente estável.", completed.text)
        assertEquals("Paciente estável.", store.get(note.id)?.body)
        assertTrue(env.inserter.writes.isEmpty())
    }

    @Test
    fun theReviewBarReceivesTheFinalText() = runTest {
        val env = finalPassEnv(
            this,
            FakeSpeechEngine(emitted = mapOf(1 to listOf(" hemograma", " sem", " alteracao"))),
            reviewBeforeInsert = true
        )
        env.client.reply = { "hemograma sem alterações" }
        env.proofreader.reply = { "Hemograma sem alterações." }

        env.pipeline.start(DictationTarget.ActiveField)
        env.speak(1)
        val ready = assertIs<DictationPipelineStatus.Ready>(env.pipeline.finalizeForReview())

        assertEquals("Hemograma sem alterações.", ready.text)
        assertTrue(env.inserter.writes.isEmpty())
    }

    // Acima do limite de processamento de um pedido o áudio vai em pedaços de até 50 s, transcritos
    // juntos e emendados na ordem.
    @Test
    fun aLongDictationGoesInPiecesOfAtMostFiftySecondsJoinedInOrder() = runTest {
        val env = finalPassEnv(this, FakeSpeechEngine(emitted = mapOf(1 to listOf(" começo"), 600 to listOf(" fim"))))
        env.client.reply = { window -> if (window.index == 0) "Começo do ditado" else "e o fim." }
        env.proofreader.reply = { it }

        env.pipeline.start(DictationTarget.ActiveField)
        repeat(600) { env.speak(it + 1) }
        env.pipeline.finalize()

        assertEquals(listOf(50_000L * FRAME_BYTES / 100, 10_000L * FRAME_BYTES / 100).map { it.toInt() }, env.client.pcmSizes)
        assertEquals("Começo do ditado e o fim.", env.field.toString())
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
            preferences = AppPreferences(formattingMode = FormattingMode.Llm, transcriptionEngine = TranscriptionEngine.Local, reviewBeforeInsert = true)
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
    fun aPeriodThatArrivesInTheNextPieceStaysGluedToTheWordInTheNote() = runTest {
        // No S26 o ponto de "desconforto respiratório." saiu sozinho no pedaço seguinte à pausa, e a
        // nota ficou "respiratório . O hemograma".
        val env = LocalEnv(
            this,
            FakeSpeechEngine(emitted = mapOf(1 to listOf(" desconforto", " respiratório", " "), 2 to listOf(".", " O"), 3 to listOf(" hemograma", " ")))
        )
        val store = InMemoryNoteStore()
        val note = store.create(body = "")
        NoteDictationCoordinator(store).apply { attach(env.pipeline.session, backgroundScope) }.begin(note.id, env.pipeline.session.value)

        env.pipeline.start(DictationTarget.Note)
        env.speak(1)
        env.speak(2)
        env.speak(3)
        val completed = assertIs<DictationPipelineStatus.Completed>(env.pipeline.finalize())

        assertEquals(".", env.pipeline.segments.value.single { it.windowIndex == 1 }.text, "o ponto veio sozinho")
        assertEquals("desconforto respiratório. O hemograma", completed.text)
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
            preferences = AppPreferences(formattingMode = FormattingMode.Llm, transcriptionEngine = TranscriptionEngine.Cloud),
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

private const val FRAME_BYTES = 100 * 16 * 2

private fun finalPassEnv(
    scope: TestScope,
    engine: FakeSpeechEngine,
    reviewBeforeInsert: Boolean = false,
    formattingMode: FormattingMode = FormattingMode.Llm
) = LocalEnv(
    scope,
    engine,
    preferences = AppPreferences(
        transcriptionEngine = TranscriptionEngine.Local,
        proofreadingEnabled = true,
        reviewBeforeInsert = reviewBeforeInsert,
        formattingMode = formattingMode
    ),
    apiKey = "sk-or-v1-testkey123456"
)

private class LocalEnv(
    scope: TestScope,
    engine: FakeSpeechEngine,
    preferences: AppPreferences = AppPreferences(formattingMode = FormattingMode.Llm, transcriptionEngine = TranscriptionEngine.Local),
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
    val audioChat = RecordingAudioChat()
    val engines = FakeSpeechEngines(StandardTestDispatcher(scope.testScheduler), installed) { engine }
    val pipeline = DictationPipeline(
        controller = DictationSessionController(capture, windowTargetDurationMs = 100L),
        client = client,
        config = OpenRouterConfig(),
        dictionary = InMemoryPersonalDictionary(),
        proofreading = proofreader,
        audioChat = audioChat,
        preferences = InMemoryPreferencesStore(preferences),
        secrets = InMemorySecretStore().apply { apiKey?.let { writeOpenRouterKey(it) } },
        inserter = inserter,
        scope = scope.backgroundScope,
        eventLog = log,
        timeSource = scope.testScheduler.timeSource,
        localEngines = engines,
        localWindowTargetDurationMs = 100L
    )

    // Um trecho de fala de 100 ms: fecha uma janela no teto (sem pausa).
    @Suppress("UNUSED_PARAMETER")
    suspend fun speak(chunk: Int) {
        capture.push(AudioFrame(voicedPcm(FRAME_BYTES), AudioFormat.DEFAULT))
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

// Campo de texto que o app lê e escreve, como o da acessibilidade quando tudo dá certo. `refuseNext`
// recusa a próxima escrita sem toque, como a trava de destino quando o foco foi para outro app.
private class FieldInserter : TextInserter {
    val field = StringBuilder()
    val writes = mutableListOf<String>()
    var refuseNext = false

    override val isAvailable: Boolean = true

    override fun insert(text: String): TextInsertionResult {
        field.append(text)
        return TextInsertionResult(success = true, route = "teste", message = "ok")
    }

    override fun insertWithoutTap(text: String, deleteBefore: Int): TextInsertionResult {
        if (refuseNext) {
            refuseNext = false
            return TextInsertionResult(success = false, route = "trava:destino", message = "outro app em foco")
        }
        field.setLength((field.length - deleteBefore).coerceAtLeast(0))
        field.append(text)
        writes += text
        return TextInsertionResult(success = true, route = "teste", message = "ok")
    }

    override fun readBeforeCursor(limit: Int): String = field.takeLast(limit).toString()
}

private class RecordingProofreader : ProofreadingClient {
    val received = mutableListOf<String>()
    var reply: suspend (String) -> String = { it }

    override suspend fun proofread(text: String, apiKey: String, model: String): String {
        received += text
        return reply(text)
    }
}

private class RecordingAudioChat : AudioChatClient {
    val models = mutableListOf<String>()
    var reply: suspend (DictationWindow) -> String = { "Nuvem num passo." }

    override suspend fun transcribeFormatted(window: DictationWindow, apiKey: String, model: String): String {
        models += model
        return reply(window)
    }
}

private class CloudClient : TranscriptionClient {
    val requested = mutableListOf<Int>()
    val pcmSizes = mutableListOf<Int>()
    var reply: suspend (DictationWindow) -> String = { "nuvem" }

    override suspend fun transcribe(window: DictationWindow, apiKey: String, model: String?): TranscriptionResult {
        requested += window.index
        pcmSizes += window.transmittedPcm.size
        return TranscriptionResult(reply(window), "fake")
    }
}
