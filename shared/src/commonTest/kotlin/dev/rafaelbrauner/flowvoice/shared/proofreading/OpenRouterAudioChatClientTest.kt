package dev.rafaelbrauner.flowvoice.shared.proofreading

import dev.rafaelbrauner.flowvoice.shared.dictation.AudioFormat
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow
import dev.rafaelbrauner.flowvoice.shared.model.CloudModels
import dev.rafaelbrauner.flowvoice.shared.model.Reasoning
import dev.rafaelbrauner.flowvoice.shared.transcription.OpenRouterConfig
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionError
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.decodeBase64Bytes
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class OpenRouterAudioChatClientTest {
    private val window = DictationWindow(
        index = 0,
        pcm = ByteArray(3_200),
        format = AudioFormat.DEFAULT,
        startedAtMs = 0L,
        finishedAtMs = 100L
    )

    private fun client(engine: MockEngine) = OpenRouterAudioChatClient(
        HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout)
        },
        OpenRouterConfig()
    )

    private fun answering(content: String, status: HttpStatusCode = HttpStatusCode.OK) = MockEngine {
        respond(
            content = ByteReadChannel("""{"choices":[{"message":{"role":"assistant","content":$content}}]}"""),
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }

    private fun body(engine: MockEngine): JsonObject =
        Json.parseToJsonElement((engine.requestHistory.single().body as TextContent).text).jsonObject

    @Test
    fun theWholeAudioGoesAsWavInTheUserMessageWithTheTranscriptionPromptAndNoKey() = runTest {
        val engine = answering("\" Paciente do leito 12 segue com dispneia. \"")

        val text = client(engine).transcribeFormatted(window, "sk-or-v1-testkey123456", "openai/gpt-audio")

        assertEquals("Paciente do leito 12 segue com dispneia.", text)
        assertEquals("/api/v1/chat/completions", engine.requestHistory.single().url.encodedPath)
        val body = body(engine)
        assertFalse(body.toString().contains("sk-or-v1-testkey123456"))
        val (system, user) = body.getValue("messages").jsonArray.map { it.jsonObject }
        assertEquals(OpenRouterAudioChatClient.PROMPT, system.getValue("content").jsonPrimitive.content)
        val part = user.getValue("content").jsonArray.single().jsonObject
        assertEquals("input_audio", part.getValue("type").jsonPrimitive.content)
        val audio = part.getValue("input_audio").jsonObject
        assertEquals("wav", audio.getValue("format").jsonPrimitive.content)
        val wav = audio.getValue("data").jsonPrimitive.content.decodeBase64Bytes()
        assertEquals("RIFF", wav.copyOfRange(0, 4).decodeToString())
        assertEquals(44 + window.pcm.size, wav.size)
        assertEquals("0.0", body.getValue("temperature").jsonPrimitive.content)
    }

    // O Gemini Flash raciocina por padrão e não aceita desligar: vai no mínimo, como na medição.
    @Test
    fun aMeasuredModelGetsItsReasoningSettingAndAnUnmeasuredOneGetsNone() = runTest {
        val measured = CloudModels.oneStep.first { it.reasoning == Reasoning.Minimal }.id
        val withReasoning = answering("\"ok\"")
        val without = answering("\"ok\"")

        client(withReasoning).transcribeFormatted(window, "k", measured)
        client(without).transcribeFormatted(window, "k", "vendor/nao-medido")

        assertEquals("minimal", body(withReasoning).getValue("reasoning").jsonObject.getValue("effort").jsonPrimitive.content)
        assertNull(body(without)["reasoning"])
    }

    // Resposta só de raciocínio (conteúdo nulo) vira texto vazio: a passada final fica com o rascunho.
    @Test
    fun aNullAnswerIsEmptyText() = runTest {
        assertEquals("", client(answering("null")).transcribeFormatted(window, "k", "openai/gpt-audio"))
    }

    @Test
    fun aServerErrorIsAClassifiedTranscriptionError() = runTest {
        val engine = answering("\"\"", HttpStatusCode.InternalServerError)

        assertFailsWith<TranscriptionError.Server> {
            client(engine).transcribeFormatted(window, "k", "openai/gpt-audio")
        }
    }
}
