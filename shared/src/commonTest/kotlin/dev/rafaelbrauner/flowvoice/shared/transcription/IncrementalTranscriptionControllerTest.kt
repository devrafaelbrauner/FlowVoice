package dev.rafaelbrauner.flowvoice.shared.transcription

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
    fun windowTextIsLoggedOnlyWhenTextLoggingIsEnabled() = runTest {
        val results = mapOf(0 to TranscriptionResult("terceiro colocado", "fake"))
        val enabledLog = RecordingLog()
        val enabled = IncrementalTranscriptionController(
            client = FakeTranscriptionClient(results = results),
            config = OpenRouterConfig(),
            scope = this,
            apiKeyProvider = { "sk-or-v1-testkey123456" },
            eventLog = enabledLog,
            logText = true
        )
        val disabledLog = RecordingLog()
        val disabled = IncrementalTranscriptionController(
            client = FakeTranscriptionClient(results = results),
            config = OpenRouterConfig(),
            scope = this,
            apiKeyProvider = { "sk-or-v1-testkey123456" },
            eventLog = disabledLog
        )

        enabled.submit(testWindow(0))
        disabled.submit(testWindow(0))
        advanceUntilIdle()

        val text = enabledLog.events.single { it.event == "transcription_window_text" }
        assertEquals("0", text.metadata["window"])
        assertEquals("terceiro colocado", text.metadata["text"])
        assertTrue(disabledLog.events.none { event -> event.metadata.values.any { it.contains("colocado") } })
    }

    private class FakeTranscriptionClient(
        private val results: Map<Int, TranscriptionResult> = emptyMap(),
        private val failures: Map<Int, TranscriptionError> = emptyMap(),
        private val delayMs: Long = 0L
    ) : TranscriptionClient {
        val started = mutableListOf<Int>()
        var cancelCount = 0
            private set

        override suspend fun transcribe(
            window: dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow,
            apiKey: String,
            model: String?
        ): TranscriptionResult {
            started += window.index
            if (delayMs > 0L) delay(delayMs)
            failures[window.index]?.let { throw it }
            return results[window.index] ?: TranscriptionResult("w${window.index}", "fake")
        }

        override fun cancel() {
            cancelCount++
        }
    }
}
