package dev.rafaelbrauner.flowvoice.shared.transcription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OpenRouterConfigTest {
    @Test
    fun usesTheBenchmarkDefaultModelAndOpenRouterBase() {
        val config = OpenRouterConfig()
        assertEquals("https://openrouter.ai", config.baseUrl)
        assertEquals("openai/gpt-transcribe", config.model)
        assertEquals("pt", config.language)
        assertEquals("/api/v1/audio/transcriptions", config.transcriptionsPath)
        assertEquals(10_000L, config.connectTimeoutMs)
        assertEquals(30_000L, config.requestTimeoutMs)
        assertEquals(3, config.maxRetries)
        assertEquals(30, config.maxRequestsPerSession)
    }

    @Test
    fun rejectsInvalidTimeoutsAndBlankModel() {
        assertFailsWith<IllegalArgumentException> { OpenRouterConfig(model = " ") }
        assertFailsWith<IllegalArgumentException> { OpenRouterConfig(connectTimeoutMs = 0L) }
        assertFailsWith<IllegalArgumentException> { OpenRouterConfig(maxRetries = -1) }
        assertFailsWith<IllegalArgumentException> { OpenRouterConfig(maxRequestsPerSession = 0) }
        assertFailsWith<IllegalArgumentException> {
            OpenRouterConfig(initialBackoffMs = 1_000L, maxBackoffMs = 500L)
        }
    }
}
