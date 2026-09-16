package dev.rafaelbrauner.flowvoice.shared.transcription

import dev.rafaelbrauner.flowvoice.shared.dictation.AudioFormat
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow
import dev.rafaelbrauner.flowvoice.shared.dictation.WindowCut
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class IncrementalTranscriptionControllerTest {
    @Test
    fun transcribesWindowsInOrderAndAccumulatesText() = runTest {
        val client = FakeTranscriptionClient(
            results = mapOf(
                0 to TranscriptionResult("olá", "fake"),
                1 to TranscriptionResult("mundo", "fake")
            )
        )
        val controller = IncrementalTranscriptionController(
            client = client,
            config = OpenRouterConfig(),
            scope = this,
            apiKeyProvider = { "sk-or-v1-testkey123456" }
        )

        controller.submit(testWindow(0))
        controller.submit(testWindow(1))
        advanceUntilIdle()

        assertEquals(listOf(0, 1), client.started)
        assertEquals("olá mundo", controller.provisionalText.value)
        assertEquals(
            listOf(TranscriptionSegment.Status.Ok, TranscriptionSegment.Status.Ok),
            controller.segments.value.map { it.status }
        )
    }

    // P144: janela sem fala própria só teria o contexto da P143 para transcrever, e o modelo devolve
    // o contexto como texto novo. Ela é pulada pelo mesmo caminho do silêncio digital (P124).
    @Test
    fun windowWithoutItsOwnSpeechIsSkippedWithoutARequest() = runTest {
        val client = FakeTranscriptionClient()
        val controller = IncrementalTranscriptionController(
            client = client,
            config = OpenRouterConfig(),
            scope = this,
            apiKeyProvider = { "sk-or-v1-testkey123456" }
        )

        controller.submit(quietWindow(0, WindowCut.Leading))
        advanceUntilIdle()

        assertTrue(client.started.isEmpty(), "não pode gastar requisição")
        val segment = controller.segments.value.single()
        assertEquals(TranscriptionSegment.Status.Ok, segment.status)
        assertEquals("", segment.text)
        assertEquals("", controller.provisionalText.value)
    }

    // P107: a vaga do teto é ocupada na submissão, senão uma captura só de silêncio nunca terminaria.
    @Test
    fun theSkippedWindowStillTakesItsSlotInTheSessionBudget() = runTest {
        val client = FakeTranscriptionClient()
        val controller = IncrementalTranscriptionController(
            client = client,
            config = OpenRouterConfig(maxRequestsPerSession = 1),
            scope = this,
            apiKeyProvider = { "sk-or-v1-testkey123456" }
        )

        controller.submit(quietWindow(0, WindowCut.Leading))
        controller.submit(testWindow(1))
        advanceUntilIdle()

        assertTrue(controller.budgetExhausted.value, "a janela pulada tem de contar no teto")
        assertEquals(TranscriptionSegment.Status.Failed, controller.segments.value[1].status)
    }

    // Protege quem fala baixo: o que decide é a fala medida, não o corte. Uma janela de teto com
    // 400 ms de voz numa janela de 4 s — cinco vezes o limiar de 80 ms — continua indo à API, com o
    // mesmo ruído de sala das janelas que a P151 barra.
    @Test
    fun aShortButRealUtteranceInACeilingWindowIsStillTranscribed() = runTest {
        val client = FakeTranscriptionClient()
        val controller = IncrementalTranscriptionController(
            client = client,
            config = OpenRouterConfig(),
            scope = this,
            apiKeyProvider = { "sk-or-v1-testkey123456" }
        )

        controller.submit(noiseWindow(index = 0, cut = WindowCut.Ceiling, durationMs = 4_000L, voicedMs = 400L))
        advanceUntilIdle()

        assertEquals(listOf(0), client.started)
    }

    // P151: no S26 (2026-09-16 10:42), nos 28 s entre o fim da fala e o toque que encerrou, as
    // janelas 6, 7 e 8 foram enviadas e voltaram vazias (`transcription_request durationMs=3180` →
    // `transcription_success chars=0`), com o piso de ruído alto (`noiseFloor=87`). Elas saem como
    // `ceiling`, o corte que a P144 mandava enviar sempre — e nenhuma tinha fala medida.
    @Test
    fun theCeilingWindowWithoutMeasuredSpeechDoesNotCostARequest() = runTest {
        val eventLog = RecordingLog()
        val client = FakeTranscriptionClient()
        val controller = IncrementalTranscriptionController(
            client = client,
            config = OpenRouterConfig(),
            scope = this,
            apiKeyProvider = { "sk-or-v1-testkey123456" },
            eventLog = eventLog
        )

        controller.submit(noiseWindow(index = 6, cut = WindowCut.Ceiling, durationMs = 3_180L))
        advanceUntilIdle()

        assertTrue(client.started.isEmpty(), "janela sem fala medida não pode gastar requisição")
        val skipped = eventLog.events.single { it.event == "transcription_silent_window" }
        assertEquals("fala_insuficiente", skipped.metadata["reason"])
        assertEquals("ceiling", skipped.metadata["cut"])
        assertEquals("0", skipped.metadata["voicedMs"])
    }

    @Test
    fun windowFailureDoesNotDropFollowingWindows() = runTest {
        val client = FakeTranscriptionClient(
            results = mapOf(1 to TranscriptionResult("depois", "fake")),
            failures = mapOf(0 to TranscriptionError.Server(500))
        )
        val controller = IncrementalTranscriptionController(
            client = client,
            config = OpenRouterConfig(),
            scope = this,
            apiKeyProvider = { "sk-or-v1-testkey123456" }
        )

        controller.submit(testWindow(0))
        controller.submit(testWindow(1))
        advanceUntilIdle()

        assertEquals(listOf(0, 1), client.started)
        assertEquals("depois", controller.provisionalText.value)
        assertEquals(TranscriptionSegment.Status.Failed, controller.segments.value[0].status)
        assertEquals("server_500", controller.segments.value[0].errorKind)
        assertEquals(TranscriptionSegment.Status.Ok, controller.segments.value[1].status)
    }

    @Test
    fun cancelStopsQueueAndActiveRequest() = runTest {
        val client = FakeTranscriptionClient(delayMs = 10_000L)
        val controller = IncrementalTranscriptionController(
            client = client,
            config = OpenRouterConfig(),
            scope = this,
            apiKeyProvider = { "sk-or-v1-testkey123456" }
        )

        controller.submit(testWindow(0))
        controller.submit(testWindow(1))
        runCurrent()
        controller.cancel()
        runCurrent()
        controller.submit(testWindow(2))
        advanceUntilIdle()

        assertEquals(1, client.cancelCount)
        assertTrue(client.started.size <= 1)
        assertEquals("", controller.provisionalText.value)
        assertEquals(0, controller.segments.value.count { it.windowIndex == 2 })
    }

    @Test
    fun enforcesSessionRequestBudget() = runTest {
        val client = FakeTranscriptionClient()
        val controller = IncrementalTranscriptionController(
            client = client,
            config = OpenRouterConfig(maxRequestsPerSession = 1),
            scope = this,
            apiKeyProvider = { "sk-or-v1-testkey123456" }
        )

        controller.submit(testWindow(0))
        controller.submit(testWindow(1))
        advanceUntilIdle()

        assertEquals(listOf(0), client.started)
        assertEquals(TranscriptionSegment.Status.Ok, controller.segments.value[0].status)
        assertEquals(TranscriptionSegment.Status.Failed, controller.segments.value[1].status)
        assertEquals("budget", controller.segments.value[1].errorKind)
    }

    @Test
    fun budgetIsCountedAtSubmissionSoSlowRequestsCannotLetCaptureRunPastIt() = runTest {
        val client = FakeTranscriptionClient(delayMs = 10_000L)
        val controller = IncrementalTranscriptionController(
            client = client,
            config = OpenRouterConfig(maxRequestsPerSession = 2),
            scope = this,
            apiKeyProvider = { "sk-or-v1-testkey123456" }
        )
        var submitted = 0
        var submittedWhenExhausted = -1

        val watcher = launch {
            controller.budgetExhausted.first { it }
            submittedWhenExhausted = submitted
        }
        launch {
            repeat(12) {
                controller.submit(testWindow(it))
                submitted++
                delay(4_000L)
            }
        }
        advanceUntilIdle()
        watcher.join()

        assertEquals(3, submittedWhenExhausted, "janelas capturadas ao esgotar")
        assertEquals(listOf(0, 1), client.started)
    }

    @Test
    fun digitallySilentWindowIsNotSentAndLeavesNoText() = runTest {
        val client = FakeTranscriptionClient(
            results = mapOf(
                0 to TranscriptionResult("o médico", "fake"),
                1 to TranscriptionResult("texto inventado", "fake"),
                2 to TranscriptionResult("pediu o exame", "fake")
            )
        )
        val controller = IncrementalTranscriptionController(
            client = client,
            config = OpenRouterConfig(),
            scope = this,
            apiKeyProvider = { "sk-or-v1-testkey123456" }
        )

        controller.submit(testWindow(0))
        controller.submit(testWindow(1, silent = true))
        controller.submit(testWindow(2))
        advanceUntilIdle()

        assertEquals(listOf(0, 2), client.started)
        assertEquals("o médico pediu o exame", controller.provisionalText.value)
        assertEquals(TranscriptionSegment(1, TranscriptionSegment.Status.Ok), controller.segments.value[1])
    }

    @Test
    fun silentWindowsStillCountTowardTheSessionBudgetSoAMutedCaptureEnds() = runTest {
        val client = FakeTranscriptionClient()
        val controller = IncrementalTranscriptionController(
            client = client,
            config = OpenRouterConfig(maxRequestsPerSession = 2),
            scope = this,
            apiKeyProvider = { "sk-or-v1-testkey123456" }
        )

        repeat(3) { controller.submit(testWindow(it, silent = true)) }
        advanceUntilIdle()

        assertEquals(emptyList(), client.started)
        assertTrue(controller.budgetExhausted.value)
        assertEquals("budget", controller.segments.value[2].errorKind)
    }

    @Test
    fun missingOrRejectedKeyBecomesTerminalAndStopsFurtherRequests() = runTest {
        val missing = IncrementalTranscriptionController(
            client = FakeTranscriptionClient(),
            config = OpenRouterConfig(),
            scope = this,
            apiKeyProvider = { null }
        )
        missing.submit(testWindow(0))
        advanceUntilIdle()
        assertIs<TranscriptionError.InvalidKey>(missing.fatalError.value)

        val client = FakeTranscriptionClient(failures = mapOf(0 to TranscriptionError.InvalidKey()))
        val rejected = IncrementalTranscriptionController(
            client = client,
            config = OpenRouterConfig(),
            scope = this,
            apiKeyProvider = { "sk-or-v1-testkey123456" }
        )
        repeat(3) { rejected.submit(testWindow(it)) }
        advanceUntilIdle()

        assertIs<TranscriptionError.InvalidKey>(rejected.fatalError.value)
        assertEquals(listOf(0), client.started)
        assertEquals("invalid_key", rejected.segments.value[2].errorKind)

        rejected.reset()
        assertNull(rejected.fatalError.value)
    }

    @Test
    fun windowTextGoesOnlyToTheTextLogNeverToTheEventLog() = runTest {
        val eventLog = RecordingLog()
        val textLog = RecordingLog()
        val controller = IncrementalTranscriptionController(
            client = FakeTranscriptionClient(results = mapOf(0 to TranscriptionResult("terceiro colocado", "fake"))),
            config = OpenRouterConfig(),
            scope = this,
            apiKeyProvider = { "sk-or-v1-testkey123456" },
            eventLog = eventLog,
            textLog = textLog
        )

        controller.submit(testWindow(0))
        advanceUntilIdle()

        val text = textLog.events.single { it.event == "transcription_window_text" }
        assertEquals("0", text.metadata["window"])
        assertEquals("terceiro colocado", text.metadata["text"])
        assertTrue(eventLog.events.none { event -> event.metadata.values.any { it.contains("colocado") } })
    }

    @Test
    fun apiKeyIsReadOncePerSessionSoAVaultHiccupMidSessionDoesNotEndIt() = runTest {
        val client = FakeTranscriptionClient()
        var reads = 0
        val vault = listOf("sk-or-v1-first", null, null)
        val controller = IncrementalTranscriptionController(
            client = client,
            config = OpenRouterConfig(),
            scope = this,
            apiKeyProvider = { vault.getOrElse(reads++) { "sk-or-v1-second" } }
        )

        repeat(3) { controller.submit(testWindow(it)) }
        advanceUntilIdle()

        assertNull(controller.fatalError.value)
        assertEquals(listOf(0, 1, 2), client.started)
        assertEquals(List(3) { "sk-or-v1-first" }, client.keys)
        assertEquals(1, reads)

        controller.reset()
        controller.submit(testWindow(0))
        advanceUntilIdle()

        assertEquals(2, reads)
    }

    // Ruído de sala (pico 100, bem acima do silêncio digital da P124) e nenhuma fala medida: é a
    // janela que só teria o contexto da P143 para transcrever.
    private fun quietWindow(index: Int, cut: WindowCut) = DictationWindow(
        index = index,
        pcm = ByteArray(32_000) { if (it % 2 == 0) 100.toByte() else 0.toByte() },
        format = AudioFormat.DEFAULT,
        startedAtMs = index * 1_000L,
        finishedAtMs = (index + 1) * 1_000L,
        cut = cut,
        voicedMs = 0L
    )

    // Ruído de sala do fim do ditado (nível 200, bem acima do silêncio digital da P124) e nenhuma
    // fala medida pelo endpointer, com o piso de ruído alto medido no S26 (P151).
    private fun noiseWindow(index: Int, cut: WindowCut, durationMs: Long, voicedMs: Long = 0L): DictationWindow {
        val frames = ((durationMs * AudioFormat.DEFAULT.sampleRate) / 1_000L).toInt()
        return DictationWindow(
            index = index,
            pcm = ByteArray(frames * AudioFormat.DEFAULT.bytesPerFrame) {
                if (it % 2 == 0) 200.toByte() else 0.toByte()
            },
            format = AudioFormat.DEFAULT,
            startedAtMs = index * durationMs,
            finishedAtMs = (index + 1) * durationMs,
            cut = cut,
            noiseFloor = 87,
            voicedMs = voicedMs
        )
    }

    private class FakeTranscriptionClient(
        private val results: Map<Int, TranscriptionResult> = emptyMap(),
        private val failures: Map<Int, TranscriptionError> = emptyMap(),
        private val delayMs: Long = 0L
    ) : TranscriptionClient {
        val started = mutableListOf<Int>()
        val keys = mutableListOf<String>()
        var cancelCount = 0
            private set

        override suspend fun transcribe(
            window: dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow,
            apiKey: String,
            model: String?
        ): TranscriptionResult {
            started += window.index
            keys += apiKey
            if (delayMs > 0L) delay(delayMs)
            failures[window.index]?.let { throw it }
            return results[window.index] ?: TranscriptionResult("w${window.index}", "fake")
        }

        override fun cancel() {
            cancelCount++
        }
    }
}
