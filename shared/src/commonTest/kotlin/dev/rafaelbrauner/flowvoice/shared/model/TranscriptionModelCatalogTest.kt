package dev.rafaelbrauner.flowvoice.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.TestTimeSource
import kotlinx.coroutines.test.runTest

class TranscriptionCatalogTest {
    @Test
    fun normalizesDuplicatesAndSorts() {
        val models = TranscriptionCatalog.normalize(
            listOf(
                ModelInfo(id = "openai/gpt-transcribe", name = null),
                ModelInfo(id = " deepgram/nova-3 ", name = "Nova 3"),
                ModelInfo(id = "openai/gpt-transcribe", name = "GPT Transcribe"),
                ModelInfo(id = "  ", name = "vazio")
            )
        )
        assertEquals(
            listOf("deepgram/nova-3", "openai/gpt-transcribe"),
            models.map { it.id }
        )
        assertEquals("Nova 3", models[0].name)
        assertEquals("GPT Transcribe", models[1].name)
    }
}

class TranscriptionModelCatalogTest {
    @Test
    fun missingKeyFailsWithoutRequest() = runTest {
        val catalog = TranscriptionModelCatalog(FakeModelsApi())
        val result = catalog.load("  ")
        assertIs<TranscriptionCatalogResult.Failed>(result)
        assertEquals(CatalogFailure.NO_KEY, result.reason)
    }

    @Test
    fun cachesWithinTtlAndRefetchesAfterExpiry() = runTest {
        var calls = 0
        val fake = FakeModelsApi(onCall = { calls++ })
        val time = TestTimeSource()
        val catalog = TranscriptionModelCatalog(fake, timeSource = time)
        assertIs<TranscriptionCatalogResult.Ready>(catalog.load("sk-or-v1-chave"))
        assertIs<TranscriptionCatalogResult.Ready>(catalog.load("sk-or-v1-chave"))
        assertEquals(1, calls)
        time += TranscriptionModelCatalog.CACHE_TTL
        assertIs<TranscriptionCatalogResult.Ready>(catalog.load("sk-or-v1-chave"))
        assertEquals(2, calls)
    }
    @Test
    fun keyChangeInvalidatesCache() = runTest {
        var calls = 0
        val fake = FakeModelsApi(onCall = { calls++ })
        val catalog = TranscriptionModelCatalog(fake)
        assertIs<TranscriptionCatalogResult.Ready>(catalog.load("sk-or-v1-aaaa"))
        assertIs<TranscriptionCatalogResult.Ready>(catalog.load("sk-or-v1-bbbb"))
        assertEquals(2, calls)
    }

    @Test
    fun invalidKeyMapsToInvalidState() = runTest {
        val catalog = TranscriptionModelCatalog(FakeModelsApi(failure = IllegalStateException("HTTP 401")))
        val result = catalog.load("sk-or-v1-ruim")
        assertIs<TranscriptionCatalogResult.Failed>(result)
        assertEquals(CatalogFailure.INVALID_KEY, result.reason)
    }

    @Test
    fun networkFailureMapsToUnavailable() = runTest {
        val catalog = TranscriptionModelCatalog(FakeModelsApi(failure = IllegalStateException("timeout")))
        val result = catalog.load("sk-or-v1-chave")
        assertIs<TranscriptionCatalogResult.Failed>(result)
        assertEquals(CatalogFailure.UNAVAILABLE, result.reason)
    }

    @Test
    fun emptyCatalogMapsToEmpty() = runTest {
        val catalog = TranscriptionModelCatalog(FakeModelsApi(models = emptyList()))
        assertIs<TranscriptionCatalogResult.Empty>(catalog.load("sk-or-v1-chave"))
    }

    private class FakeModelsApi(
        private val models: List<ModelInfo> = listOf(ModelInfo(id = "openai/gpt-transcribe")),
        private val onCall: (() -> Unit)? = null,
        private val failure: Throwable? = null
    ) : dev.rafaelbrauner.flowvoice.shared.api.OpenRouterApi {
        override suspend fun listTranscriptionModels(apiKey: String): List<ModelInfo> {
            onCall?.invoke()
            failure?.let { throw it }
            assertTrue(apiKey.isNotBlank())
            return models
        }
    }
}