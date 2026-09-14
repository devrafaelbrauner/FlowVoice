package dev.rafaelbrauner.flowvoice.shared.benchmark

import dev.rafaelbrauner.flowvoice.shared.dictation.AudioFormat
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionClient
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionError
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BenchmarkRunnerTest {
    @Test
    fun ranksByWerThenLatencyAndPicksFallback() = runTest {
        val client = FakeClient(
            transcripts = mapOf(
                "slow-accurate" to "o medico pediu o exame de sangue para amanha de manha",
                "fast-noisy" to "o medico pediu o cafe"
            ),
            latencyByModel = mapOf(
                "slow-accurate" to 40L,
                "fast-noisy" to 5L
            ),
            costByModel = mapOf(
                "slow-accurate" to 0.02,
                "fast-noisy" to 0.01
            )
        )
        val runner = BenchmarkRunner(
            client = client,
            apiKeyProvider = { "sk-or-v1-testkey123456" },
            models = listOf("slow-accurate", "fast-noisy")
        )

        val report = runner.run(listOf(clip()), BenchmarkBudget(maxUsd = 1.0, maxRequests = 10))

        assertEquals("slow-accurate", report.defaultModel)
        assertEquals("fast-noisy", report.fallbackModel)
        assertEquals(listOf("slow-accurate", "fast-noisy"), report.models.map { it.model })
        assertEquals(2, report.requests)
        assertEquals(0.03, report.spentUsd, 1e-9)
        assertNull(report.stoppedReason)
    }

    @Test
    fun stopsWhenUsdBudgetWouldContinue() = runTest {
        val client = FakeClient(
            transcripts = mapOf(
                "a" to "o medico pediu o exame de sangue para amanha de manha",
                "b" to "o medico pediu o exame de sangue para amanha de manha"
            ),
            costByModel = mapOf("a" to 0.6, "b" to 0.6)
        )
        val runner = BenchmarkRunner(
            client = client,
            apiKeyProvider = { "sk-or-v1-testkey123456" },
            models = listOf("a", "b")
        )

        val report = runner.run(listOf(clip()), BenchmarkBudget(maxUsd = 0.5, maxRequests = 10))

        assertEquals(listOf("a"), client.models)
        assertEquals("usd", report.stoppedReason)
        assertEquals(1, report.requests)
    }

    @Test
    fun modelFailureDoesNotAbortOthers() = runTest {
        val client = FakeClient(
            transcripts = mapOf("ok" to "o medico pediu o exame de sangue para amanha de manha"),
            failures = mapOf("boom" to TranscriptionError.Server(500))
        )
        val runner = BenchmarkRunner(
            client = client,
            apiKeyProvider = { "sk-or-v1-testkey123456" },
            models = listOf("boom", "ok")
        )

        val report = runner.run(listOf(clip()), BenchmarkBudget())

        assertEquals(2, report.requests)
        assertEquals("ok", report.defaultModel)
        assertEquals(1, report.models.first { it.model == "boom" }.failures)
        assertTrue(report.rankingText().contains("ok"))
    }

    private fun clip(): BenchmarkClip = BenchmarkClip(
        id = "clinico-exame",
        reference = "O médico pediu o exame de sangue para amanhã de manhã.",
        window = DictationWindow(
            index = 0,
            pcm = ByteArray(32),
            format = AudioFormat.DEFAULT,
            startedAtMs = 0L,
            finishedAtMs = 1_000L
        )
    )

    private class FakeClient(
        private val transcripts: Map<String, String>,
        private val failures: Map<String, TranscriptionError> = emptyMap(),
        private val latencyByModel: Map<String, Long> = emptyMap(),
        private val costByModel: Map<String, Double> = emptyMap()
    ) : TranscriptionClient {
        val models = mutableListOf<String>()

        override suspend fun transcribe(
            window: DictationWindow,
            apiKey: String,
            model: String?
        ): TranscriptionResult {
            val used = model ?: "missing"
            models += used
            val wait = latencyByModel[used] ?: 0L
            if (wait > 0L) kotlinx.coroutines.delay(wait)
            failures[used]?.let { throw it }
            return TranscriptionResult(
                text = transcripts[used] ?: "",
                model = used,
                costUsd = costByModel[used]
            )
        }

        override fun cancel() {}
    }
}
