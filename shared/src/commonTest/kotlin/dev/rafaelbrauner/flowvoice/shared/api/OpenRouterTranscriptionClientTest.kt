package dev.rafaelbrauner.flowvoice.shared.api

import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionError
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.client.plugins.HttpTimeout
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun jsonHttp(engine: MockEngine): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    install(HttpTimeout)
}

class OpenRouterTranscriptionClientTest {

    private fun request() = TranscriptionRequest(
        audio = OpenRouterTranscriptionClient.wavBytes(floatArrayOf(0.1f, -0.1f)),
        model = "openai/whisper-large-v3-turbo",
        language = "pt"
    )

    @Test
    fun successReturnsText() = runTest {
        val client = OpenRouterTranscriptionClient(
            jsonHttp(
                MockEngine {
                    respond(
                        """{"text":"bom dia"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            )
        )
        val result = client.transcribe(request(), "key12345678")
        assertEquals("bom dia", result.text)
        assertEquals("openai/whisper-large-v3-turbo", result.modelId)
    }

    @Test
    fun missingKeyThrows() = runTest {
        val client = OpenRouterTranscriptionClient(jsonHttp(MockEngine { respond("", HttpStatusCode.OK) }))
        assertFailsWith<TranscriptionError.MissingApiKey> {
            client.transcribe(request(), "   ")
        }
    }

    @Test
    fun unauthorizedMapsToInvalidKey() = runTest {
        val client = OpenRouterTranscriptionClient(
            jsonHttp(MockEngine { respond("unauthorized", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.ContentType, "text/plain")) })
        )
        assertFailsWith<TranscriptionError.InvalidApiKey> {
            client.transcribe(request(), "badkey123456")
        }
    }

    @Test
    fun rateLimitedRetriesOnceThenThrows() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls += 1
            respond("slow", HttpStatusCode.TooManyRequests)
        }
        val client = OpenRouterTranscriptionClient(jsonHttp(engine), maxRetries = 1, retryDelayMs = 1)
        assertFailsWith<TranscriptionError.RateLimited> {
            client.transcribe(request(), "key12345678")
        }
        assertEquals(2, calls)
    }

    @Test
    fun serverErrorRetriesOnce() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls += 1
            if (calls == 1) respond("boom", HttpStatusCode.InternalServerError)
            else respond(
                """{"text":"ok"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = OpenRouterTranscriptionClient(jsonHttp(engine), maxRetries = 1, retryDelayMs = 1)
        val result = client.transcribe(request(), "key12345678")
        assertEquals("ok", result.text)
        assertEquals(2, calls)
    }

    @Test
    fun wavBytesHaveValidHeader() {
        val bytes = OpenRouterTranscriptionClient.wavBytes(floatArrayOf(0.5f, -0.5f))
        assertTrue(bytes.size == 44 + 4)
        assertEquals('R'.code.toByte(), bytes[0])
        assertEquals('I'.code.toByte(), bytes[1])
        assertEquals('F'.code.toByte(), bytes[2])
        assertEquals('F'.code.toByte(), bytes[3])
    }
}
