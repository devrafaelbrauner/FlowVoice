package dev.rafaelbrauner.flowvoice.shared.proofreading

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
}
