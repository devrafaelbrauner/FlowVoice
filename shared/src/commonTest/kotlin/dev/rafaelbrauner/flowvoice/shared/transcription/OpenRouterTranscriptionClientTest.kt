package dev.rafaelbrauner.flowvoice.shared.transcription

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.decodeBase64Bytes
import io.ktor.utils.io.ByteReadChannel
import dev.rafaelbrauner.flowvoice.shared.dictation.AudioFormat
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val WAV_HEADER = 44

// Resposta `verbose_json` com o contexto de 1 s à frente (P143): "Segundo doutor" é o contexto que o
// modelo retranscreveu de outro jeito, e "Grandmont" é a fala da janela.
private val VERBOSE_WORDS_BODY = """
    {
      "task": "transcribe",
      "language": "portuguese",
      "duration": 2.4,
      "text": "Segundo doutor Grandmont.",
      "words": [
        {"word": "Segundo", "start": 0.1, "end": 0.5},
        {"word": "doutor", "start": 0.5, "end": 0.86},
        {"word": "Grandmont", "start": 1.1, "end": 1.6}
      ]
    }
""".trimIndent()

private val VERBOSE_SEGMENTS_BODY = """
    {
      "task": "transcribe",
      "text": "Avaliado pelo doutor. Grandmont chegou.",
      "segments": [
        {"id": 0, "start": 0.0, "end": 0.85, "text": "Avaliado pelo doutor."},
        {"id": 1, "start": 1.0, "end": 2.4, "text": "Grandmont chegou."}
      ]
    }
""".trimIndent()

class OpenRouterTranscriptionClientTest {
    @Test
    fun postsJsonAudioAndReturnsTextWithoutLoggingSecrets() = runTest {
        val log = RecordingLog()
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("""{"text":"olá mundo"}"""),
                status = HttpStatusCode.OK,
                headers = jsonHeaders()
            )
        }
        val client = OpenRouterTranscriptionClient(httpClient(engine), OpenRouterConfig(), log)

        val result = client.transcribe(testWindow(index = 2, durationMs = 4_000L), SECRET_KEY)
        val request = engine.requestHistory.single()

        assertEquals("olá mundo", result.text)
        assertEquals(OpenRouterConfig.DEFAULT_MODEL, result.model)
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/audio/transcriptions", request.url.encodedPath)
        assertEquals("Bearer $SECRET_KEY", request.headers[HttpHeaders.Authorization])
        assertTrue(request.body.contentType.toString().contains("json"))
        assertFalse(log.containsSecret())
        assertTrue(log.events.any { it.event == "transcription_request" })
        assertEquals("2", log.events.first().metadata["window"])
        assertEquals("4000", log.events.first().metadata["durationMs"])
    }

    // P143: o áudio enviado é o contexto da janela anterior seguido do áudio da janela. O `durationMs`
    // do log continua sendo o da janela; `contextMs` mede o áudio a mais.
    @Test
    fun sendsThePreviousWindowsTailBeforeTheWindowAudio() = runTest {
        val log = RecordingLog()
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("""{"text":"diarreia"}"""),
                status = HttpStatusCode.OK,
                headers = jsonHeaders()
            )
        }
        val context = ByteArray(3_200) { 7 }
        val own = ByteArray(6_400) { 9 }
        val window = DictationWindow(
            index = 1,
            pcm = own,
            format = AudioFormat.DEFAULT,
            startedAtMs = 1_000L,
            finishedAtMs = 1_200L,
            contextPcm = context
        )
        val client = OpenRouterTranscriptionClient(httpClient(engine), OpenRouterConfig(), log)

        client.transcribe(window, SECRET_KEY)

        val body = (engine.requestHistory.single().body as TextContent).text
        val base64 = Json.parseToJsonElement(body)
            .jsonObject.getValue("input_audio")
            .jsonObject.getValue("data")
            .jsonPrimitive.content
        val wav = base64.decodeBase64Bytes()

        assertEquals(WAV_HEADER + context.size + own.size, wav.size, "o WAV tem de levar contexto + janela")
        assertContentEquals(context + own, wav.copyOfRange(WAV_HEADER, wav.size))
        val request = log.events.first { it.event == "transcription_request" }
        assertEquals("100", request.metadata["contextMs"])
        assertEquals("200", request.metadata["durationMs"])
    }

    @Test
    fun sendsExplicitModelOverride() = runTest {
        val log = RecordingLog()
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("""{"text":"ok"}"""),
                status = HttpStatusCode.OK,
                headers = jsonHeaders()
            )
        }
        val client = OpenRouterTranscriptionClient(httpClient(engine), OpenRouterConfig(), log)
        val result = client.transcribe(testWindow(), SECRET_KEY, "deepgram/nova-3")
        assertEquals("deepgram/nova-3", result.model)
        assertEquals("deepgram/nova-3", log.events.first().metadata["model"])
        assertFalse(log.containsSecret())
    }

    @Test
    fun propagatesHttpErrorsWithoutSecretsInLogs() = runTest {
        val log = RecordingLog()
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("""{"error":"nope"}"""),
                status = HttpStatusCode.InternalServerError,
                headers = jsonHeaders()
            )
        }
        val client = OpenRouterTranscriptionClient(httpClient(engine), OpenRouterConfig(), log)

        val error = assertFailsWith<TranscriptionError.Server> {
            client.transcribe(testWindow(), SECRET_KEY)
        }
        assertEquals(500, error.statusCode)
        assertFalse(log.containsSecret())
        assertEquals("server", log.events.last().metadata["kind"])
    }

    @Test
    fun httpErrorLogCarriesTheStatusCode() = runTest {
        val log = RecordingLog()
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("""{"error":{"code":402,"message":"insufficient credits"}}"""),
                status = HttpStatusCode.PaymentRequired,
                headers = jsonHeaders()
            )
        }
        val client = OpenRouterTranscriptionClient(httpClient(engine), OpenRouterConfig(), log)

        assertFailsWith<TranscriptionError> { client.transcribe(testWindow(), SECRET_KEY) }

        val error = log.events.last()
        assertEquals("transcription_error", error.event)
        assertEquals("402", error.metadata["status"])
        assertFalse(log.containsSecret())
    }

    @Test
    fun sendsTemperatureZeroSoTheResultDoesNotDependOnProviderDefaults() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("""{"text":"ok"}"""),
                status = HttpStatusCode.OK,
                headers = jsonHeaders()
            )
        }
        val client = OpenRouterTranscriptionClient(httpClient(engine), OpenRouterConfig())

        client.transcribe(testWindow(), SECRET_KEY)

        val body = (engine.requestHistory.single().body as TextContent).text
        assertTrue(body.contains("\"temperature\":0.0"), body)
    }

    @Test
    fun mapsUnauthorizedToInvalidKey() = runTest {
        val engine = MockEngine {
            respond(content = ByteReadChannel(""), status = HttpStatusCode.Unauthorized)
        }
        val client = OpenRouterTranscriptionClient(httpClient(engine), OpenRouterConfig())
        assertIs<TranscriptionError.InvalidKey>(
            assertFailsWith<TranscriptionError.InvalidKey> { client.transcribe(testWindow(), SECRET_KEY) }
        )
    }

    @Test
    fun mapsForbiddenToARefusedRequestNotAnInvalidKey() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("""{"error":{"code":403,"message":"Blocked by guardrail"}}"""),
                status = HttpStatusCode.Forbidden,
                headers = jsonHeaders()
            )
        }
        val client = OpenRouterTranscriptionClient(httpClient(engine), OpenRouterConfig())

        val error = assertFailsWith<TranscriptionError> { client.transcribe(testWindow(), SECRET_KEY) }

        assertFalse(error is TranscriptionError.InvalidKey)
        assertEquals("forbidden", error.kind)
    }

    // P146: o tempo por palavra só é pedido quando há contexto sobreposto para cortar. A janela 0 vai
    // sozinha e não tem repetição nenhuma, então não paga uma resposta maior.
    @Test
    fun asksForWordTimestampsOnlyWhenThereIsContextToTrim() = runTest {
        val log = RecordingLog()
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("""{"text":"Avaliado pelo doutor."}"""),
                status = HttpStatusCode.OK,
                headers = jsonHeaders()
            )
        }
        val client = OpenRouterTranscriptionClient(httpClient(engine), OpenRouterConfig(), log)

        client.transcribe(testWindow(index = 0), SECRET_KEY)
        client.transcribe(testWindow(index = 1, contextMs = 1_000L), SECRET_KEY)

        val first = (engine.requestHistory[0].body as TextContent).text
        val second = (engine.requestHistory[1].body as TextContent).text
        assertFalse(first.contains("response_format"), first)
        assertTrue(second.contains("\"response_format\":\"verbose_json\""), second)
        assertTrue(second.contains("\"timestamp_granularities\":[\"word\"]"), second)
        assertTrue(log.events.none { it.event == "transcription_context_trimmed" }, "sem tempos, nada é cortado")
    }

    // O caso medido no S26 (2026-09-16): "Avaliado pelo doutor" voltou como "Segundo doutor" na janela
    // seguinte. A comparação por texto não acharia a repetição; o tempo acha.
    @Test
    fun trimsTheContextByWordTimeAndLogsHowMuchWasDropped() = runTest {
        val log = RecordingLog()
        val engine = MockEngine {
            respond(content = ByteReadChannel(VERBOSE_WORDS_BODY), status = HttpStatusCode.OK, headers = jsonHeaders())
        }
        val client = OpenRouterTranscriptionClient(httpClient(engine), OpenRouterConfig(), log)

        val result = client.transcribe(testWindow(index = 1, contextMs = 1_000L), SECRET_KEY)

        assertEquals("Grandmont.", result.text)
        val trimmed = log.events.first { it.event == "transcription_context_trimmed" }
        assertEquals("1", trimmed.metadata["window"])
        assertEquals("words", trimmed.metadata["strategy"])
        assertEquals("860", trimmed.metadata["droppedMs"])
        assertEquals("2", trimmed.metadata["droppedWords"])
        assertTrue(
            log.events.none { event -> event.metadata.values.any { it.contains("Grandmont") } },
            "o texto transcrito só sai no log de texto da P135"
        )
    }

    @Test
    fun withoutWordsTheContextIsTrimmedBySegment() = runTest {
        val log = RecordingLog()
        val engine = MockEngine {
            respond(content = ByteReadChannel(VERBOSE_SEGMENTS_BODY), status = HttpStatusCode.OK, headers = jsonHeaders())
        }
        val client = OpenRouterTranscriptionClient(httpClient(engine), OpenRouterConfig(), log)

        val result = client.transcribe(testWindow(index = 1, contextMs = 1_000L), SECRET_KEY)

        assertEquals("Grandmont chegou.", result.text)
        val trimmed = log.events.first { it.event == "transcription_context_trimmed" }
        assertEquals("segments", trimmed.metadata["strategy"])
        assertEquals("1", trimmed.metadata["droppedSegments"])
    }

    // Provedor que recusa `verbose_json`: a janela é refeita em `json` na hora, e a sessão inteira
    // segue em `json` — a recusa não se repete a cada janela.
    @Test
    fun aProviderThatRefusesVerboseJsonFallsBackAndTheSessionKeepsPlainJson() = runTest {
        val log = RecordingLog()
        val engine = MockEngine { request ->
            if ((request.body as TextContent).text.contains("verbose_json")) {
                respond(
                    content = ByteReadChannel("""{"error":{"message":"response_format is not supported"}}"""),
                    status = HttpStatusCode.BadRequest,
                    headers = jsonHeaders()
                )
            } else {
                respond(
                    content = ByteReadChannel("""{"text":"Segundo doutor Grandmont."}"""),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders()
                )
            }
        }
        val client = OpenRouterTranscriptionClient(httpClient(engine), OpenRouterConfig(), log)

        val first = client.transcribe(testWindow(index = 1, contextMs = 1_000L), SECRET_KEY)
        val second = client.transcribe(testWindow(index = 2, contextMs = 1_000L), SECRET_KEY)

        assertEquals("Segundo doutor Grandmont.", first.text, "o ditado não pode parar por causa do formato")
        assertEquals("Segundo doutor Grandmont.", second.text)
        assertEquals(3, engine.requestHistory.size, "só a primeira janela paga a recusa")
        assertFalse((engine.requestHistory[2].body as TextContent).text.contains("response_format"))
        val refusal = log.events.single { it.event == "transcription_verbose_unsupported" }
        assertEquals("http_400", refusal.metadata["reason"])
        assertEquals("400", refusal.metadata["status"])
        assertTrue(log.events.none { it.event == "transcription_error" }, "recusa de formato não é falha do trecho")
    }

    // Provedor que aceita o formato e devolve sem tempos: o texto vale, nada é cortado por tempo e as
    // janelas seguintes deixam de pedir a resposta maior.
    @Test
    fun aVerboseAnswerWithoutTimesStopsTheSessionFromAskingAgain() = runTest {
        val log = RecordingLog()
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("""{"text":"Segundo doutor Grandmont."}"""),
                status = HttpStatusCode.OK,
                headers = jsonHeaders()
            )
        }
        val client = OpenRouterTranscriptionClient(httpClient(engine), OpenRouterConfig(), log)

        val result = client.transcribe(testWindow(index = 1, contextMs = 1_000L), SECRET_KEY)
        client.transcribe(testWindow(index = 2, contextMs = 1_000L), SECRET_KEY)

        assertEquals("Segundo doutor Grandmont.", result.text)
        assertEquals(2, engine.requestHistory.size, "sem tempos não se repete o pedido: o texto já veio")
        assertFalse((engine.requestHistory[1].body as TextContent).text.contains("response_format"))
        val refusal = log.events.single { it.event == "transcription_verbose_unsupported" }
        assertEquals("sem_tempos", refusal.metadata["reason"])
    }

    private fun httpClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout)
    }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")
}
