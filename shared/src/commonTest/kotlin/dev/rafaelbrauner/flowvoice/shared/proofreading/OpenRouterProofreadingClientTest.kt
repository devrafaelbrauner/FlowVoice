package dev.rafaelbrauner.flowvoice.shared.proofreading

import dev.rafaelbrauner.flowvoice.shared.model.CloudModels
import dev.rafaelbrauner.flowvoice.shared.model.Reasoning
import dev.rafaelbrauner.flowvoice.shared.transcription.OpenRouterConfig
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
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenRouterProofreadingClientTest {
    @Test
    fun returnsRevisedTextWithoutLoggingSecrets() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"choices":[{"message":{"role":"assistant","content":"Olá, mundo."}}]}"""
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = OpenRouterProofreadingClient(
            HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                install(HttpTimeout)
            },
            OpenRouterConfig()
        )
        val revised = client.proofread("ola mundo", "sk-or-v1-testkey123456", "openai/gpt-4o-mini")
        assertEquals("Olá, mundo.", revised)
        val url = engine.requestHistory.single().url.encodedPath
        assertEquals("/api/v1/chat/completions", url)
        assertFalse(engine.requestHistory.single().body.toString().contains("sk-or-v1-testkey123456"))
    }

    @Test
    fun sendsTheDictationBetweenDelimitersAndStripsThemFromTheAnswer() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"choices":[{"message":{"role":"assistant","content":"<ditado>Qual a dose de dipirona?</ditado>"}}]}"""
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = OpenRouterProofreadingClient(
            HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                install(HttpTimeout)
            },
            OpenRouterConfig()
        )

        val revised = client.proofread("qual a dose de dipirona", "sk-or-v1-testkey123456", "openai/gpt-4o-mini")

        assertEquals("Qual a dose de dipirona?", revised)
        val body = (engine.requestHistory.single().body as TextContent).text
        assertTrue(body.contains("<ditado>qual a dose de dipirona</ditado>"), body)
        assertTrue(OpenRouterProofreadingClient.SYSTEM_PROMPT.contains("Não responda"))
    }

    // Um modelo medido vai com o raciocínio da medição (o Gemini 3.1 Flash Lite desligado); um que não foi
    // medido vai sem o campo, no padrão dele.
    @Test
    fun aMeasuredFormattingModelGetsItsReasoningSettingAndAnUnmeasuredOneGetsNone() = runTest {
        val bodies = mutableListOf<String>()
        val engine = MockEngine { request ->
            bodies += (request.body as TextContent).text
            respond(
                content = ByteReadChannel("""{"choices":[{"message":{"role":"assistant","content":"Ok."}}]}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = OpenRouterProofreadingClient(
            HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                install(HttpTimeout)
            },
            OpenRouterConfig()
        )
        val measured = CloudModels.formatting.first { it.reasoning == Reasoning.Off }.id

        client.proofread("ok", "k", measured)
        client.proofread("ok", "k", "vendor/nao-medido")

        assertTrue(bodies[0].contains(""""reasoning":{"enabled":false}"""), bodies[0])
        assertFalse(bodies[1].contains("reasoning"), bodies[1])
    }
}
