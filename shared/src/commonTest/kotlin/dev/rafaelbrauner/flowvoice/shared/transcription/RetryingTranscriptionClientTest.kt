package dev.rafaelbrauner.flowvoice.shared.transcription

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class RetryingTranscriptionClientTest {
    @Test
    fun retriesServerErrorThenSucceeds() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            if (calls == 1) {
                respond(content = ByteReadChannel(""), status = HttpStatusCode.InternalServerError)
            } else {
                respond(
                    content = ByteReadChannel("""{"text":"ok"}"""),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders()
                )
            }
        }
        val delays = mutableListOf<Long>()
        val client = retryingClient(engine, delays)

        val result = client.transcribe(testWindow(), SECRET_KEY)

        assertEquals("ok", result.text)
        assertEquals(2, calls)
        assertEquals(1, delays.size)
    }

    @Test
    fun respectsRetryAfterOnTooManyRequests() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            if (calls == 1) {
                respond(
                    content = ByteReadChannel(""),
                    status = HttpStatusCode.TooManyRequests,
                    headers = headersOf(HttpHeaders.RetryAfter, "2")
                )
            } else {
                respond(
                    content = ByteReadChannel("""{"text":"later"}"""),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders()
                )
            }
        }
        val delays = mutableListOf<Long>()
        val client = retryingClient(engine, delays)

        val result = client.transcribe(testWindow(), SECRET_KEY)

        assertEquals("later", result.text)
        assertEquals(listOf(2_000L), delays)
        assertEquals(2, calls)
    }

    @Test
    fun failsFastOnUnauthorizedWithoutRetry() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            respond(content = ByteReadChannel(""), status = HttpStatusCode.Unauthorized)
        }
        val delays = mutableListOf<Long>()
        val client = retryingClient(engine, delays)

        assertIs<TranscriptionError.InvalidKey>(
            assertFailsWith<TranscriptionError.InvalidKey> { client.transcribe(testWindow(), SECRET_KEY) }
        )
        assertEquals(1, calls)
        assertEquals(emptyList(), delays)
    }

    @Test
    fun stopsAfterRetryCeiling() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            respond(content = ByteReadChannel(""), status = HttpStatusCode.BadGateway)
        }
        val delays = mutableListOf<Long>()
        val client = retryingClient(
            engine,
            delays,
            OpenRouterConfig(maxRetries = 2, initialBackoffMs = 100L, maxBackoffMs = 400L)
        )

        assertFailsWith<TranscriptionError.Server> { client.transcribe(testWindow(), SECRET_KEY) }
        assertEquals(3, calls)
        assertEquals(2, delays.size)
    }

    private fun retryingClient(
        engine: MockEngine,
        delays: MutableList<Long>,
        config: OpenRouterConfig = OpenRouterConfig(maxRetries = 3)
    ): TranscriptionClient {
        val http = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout)
        }
        return RetryingTranscriptionClient(
            delegate = OpenRouterTranscriptionClient(http, config),
            config = config,
            sleeper = { delays += it },
            random = { 0.0 }
        )
    }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")
}
