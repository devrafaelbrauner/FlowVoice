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
    fun retriesRequestTimeoutThenSucceeds() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            if (calls == 1) {
                respond(content = ByteReadChannel(""), status = HttpStatusCode.RequestTimeout)
            } else {
                respond(
                    content = ByteReadChannel("""{"text":"depois do timeout"}"""),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders()
                )
            }
        }
        val delays = mutableListOf<Long>()
        val client = retryingClient(engine, delays)

        val result = client.transcribe(testWindow(), SECRET_KEY)

        assertEquals("depois do timeout", result.text)
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

    @Test
    fun logsEachRetryWithAttemptKindAndDelay() = runTest {
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
        val log = RecordingLog()
        val client = retryingClient(engine, delays, log = log)

        client.transcribe(testWindow(index = 3), SECRET_KEY)

        val retry = log.events.single { it.event == "transcription_retry" }
        assertEquals("3", retry.metadata["window"])
        assertEquals("1", retry.metadata["attempt"])
        assertEquals("server", retry.metadata["kind"])
        assertEquals(delays.single().toString(), retry.metadata["delayMs"])
    }

    // Y1: a OpenRouter pode responder 200 com o erro do provedor no corpo, quando o erro vem depois de
    // a resposta ter começado. Isso não é transcrição vazia: com código 5xx é falha passageira, e a
    // janela é pedida de novo em vez de virar "nada foi dito".
    @Test
    fun anOkResponseCarryingAServerErrorIsRetriedNotTakenAsSilence() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            val body = if (calls == 1) {
                """{"error":{"message":"Provider returned error","code":502}}"""
            } else {
                """{"text":"Nega febre."}"""
            }
            respond(content = ByteReadChannel(body), status = HttpStatusCode.OK, headers = jsonHeaders())
        }
        val client = retryingClient(engine, mutableListOf())

        val result = client.transcribe(testWindow(), SECRET_KEY)

        assertEquals("Nega febre.", result.text)
        assertEquals(2, calls)
    }

    // Y1: 200 sem o campo `text` não diz que a janela era silêncio. É resposta inválida, e nunca
    // texto vazio.
    @Test
    fun anOkResponseWithoutTextIsAnInvalidResponseNotAnEmptyTranscription() = runTest {
        val engine = MockEngine {
            respond(content = ByteReadChannel("""{"usage":{"cost":0.001}}"""), status = HttpStatusCode.OK, headers = jsonHeaders())
        }
        val client = retryingClient(engine, mutableListOf())

        assertFailsWith<TranscriptionError.InvalidResponse> { client.transcribe(testWindow(), SECRET_KEY) }
    }

    private fun retryingClient(
        engine: MockEngine,
        delays: MutableList<Long>,
        config: OpenRouterConfig = OpenRouterConfig(maxRetries = 3),
        log: TranscriptionEventLog = TranscriptionEventLog.NoOp
    ): TranscriptionClient {
        val http = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout)
        }
        return RetryingTranscriptionClient(
            delegate = OpenRouterTranscriptionClient(http, config),
            config = config,
            sleeper = { delays += it },
            random = { 0.0 },
            eventLog = log
        )
    }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")
}
