package dev.rafaelbrauner.flowvoice.shared.model

import dev.rafaelbrauner.flowvoice.shared.api.OpenRouterApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.json.Json
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

    // Y9: um 429 ou 5xx com corpo JSON de erro virava lista vazia, guardada por 24 h: o seletor de
    // modelo ficava vazio o dia todo. Agora é falha, e o próximo carregamento pergunta de novo.
    @Test
    fun aNon2xxModelsResponseIsAFailureAndIsNotCached() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            if (calls == 1) {
                respond(
                    content = ByteReadChannel("""{"error":{"code":429,"message":"Rate limit exceeded"}}"""),
                    status = HttpStatusCode.TooManyRequests,
                    headers = jsonHeaders()
                )
            } else {
                respond(
                    content = ByteReadChannel("""{"data":[{"id":"openai/gpt-transcribe"}]}"""),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders()
                )
            }
        }
        val catalog = TranscriptionModelCatalog(OpenRouterApiClient(httpClient(engine)))

        val first = catalog.load("sk-or-v1-chave")
        val second = catalog.load("sk-or-v1-chave")

        assertIs<TranscriptionCatalogResult.Failed>(first)
        assertEquals(CatalogFailure.UNAVAILABLE, first.reason)
        assertIs<TranscriptionCatalogResult.Ready>(second)
        assertEquals(2, calls)
    }

    // Y9: lista vazia não se guarda: é mais provável ser um soluço do serviço do que a OpenRouter sem
    // nenhum modelo de transcrição, e guardá-la esconderia os modelos por 24 h.
    @Test
    fun anEmptyCatalogIsNotCached() = runTest {
        var calls = 0
        val fake = FakeModelsApi(models = emptyList(), onCall = { calls++ })
        val catalog = TranscriptionModelCatalog(fake)

        assertIs<TranscriptionCatalogResult.Empty>(catalog.load("sk-or-v1-chave"))
        assertIs<TranscriptionCatalogResult.Empty>(catalog.load("sk-or-v1-chave"))

        assertEquals(2, calls)
    }

    private fun httpClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

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