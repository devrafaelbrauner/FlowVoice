package dev.rafaelbrauner.flowvoice.shared.transcription

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenRouterKeyValidatorTest {
    @Test
    fun formatRequiresSkPrefixAndLength() {
        assertFalse(OpenRouterKeyValidator.isValidFormat(""))
        assertFalse(OpenRouterKeyValidator.isValidFormat("short"))
        assertFalse(OpenRouterKeyValidator.isValidFormat("sk-short"))
        assertFalse(OpenRouterKeyValidator.isValidFormat("sk-or-v1-with space00000"))
        assertTrue(OpenRouterKeyValidator.isValidFormat("sk-or-v1-testkey123456"))
    }

    @Test
    fun remoteSuccessMarksValid() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("""{"data":[]}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val validator = OpenRouterKeyValidator(httpClient(engine), OpenRouterConfig())
        assertEquals(KeyValidationResult.Valid, validator.validate("sk-or-v1-testkey123456"))
    }

    @Test
    fun remoteUnauthorizedMarksRejected() = runTest {
        val engine = MockEngine {
            respond(content = ByteReadChannel(""), status = HttpStatusCode.Unauthorized)
        }
        val validator = OpenRouterKeyValidator(httpClient(engine), OpenRouterConfig())
        assertEquals(KeyValidationResult.Rejected, validator.validate("sk-or-v1-testkey123456"))
    }

    @Test
    fun validationAsksTheAuthenticatingKeyEndpointWithTheTrimmedKey() = runTest {
        var url: String? = null
        var authorization: String? = null
        val engine = MockEngine { request ->
            url = request.url.toString()
            authorization = request.headers[HttpHeaders.Authorization]
            respond(content = ByteReadChannel("""{"data":{}}"""), status = HttpStatusCode.OK)
        }
        val validator = OpenRouterKeyValidator(httpClient(engine), OpenRouterConfig())

        validator.validate("  sk-or-v1-testkey123456 ")

        assertEquals("https://openrouter.ai/api/v1/key", url)
        assertEquals("Bearer sk-or-v1-testkey123456", authorization)
    }

    @Test
    fun networkFailureIsUnavailableNotRejected() = runTest {
        val engine = MockEngine { throw IllegalStateException("conexão recusada") }
        val validator = OpenRouterKeyValidator(httpClient(engine), OpenRouterConfig())
        assertEquals(KeyValidationResult.Unavailable, validator.validate("sk-or-v1-testkey123456"))
    }

    @Test
    fun serverErrorIsUnavailableNotRejected() = runTest {
        val engine = MockEngine {
            respond(content = ByteReadChannel(""), status = HttpStatusCode.InternalServerError)
        }
        val validator = OpenRouterKeyValidator(httpClient(engine), OpenRouterConfig())
        assertEquals(KeyValidationResult.Unavailable, validator.validate("sk-or-v1-testkey123456"))
    }

    @Test
    fun formatMessageNamesTheRuleThatFailed() {
        assertEquals(KeyFormatProblem.TooShort, OpenRouterKeyValidator.formatProblem("sk-or-v1-FAKE-000"))
        assertTrue(OpenRouterKeyValidator.formatMessage("sk-or-v1-FAKE-000")!!.startsWith("Chave curta demais: tem 17 caracteres"))
        assertEquals(KeyFormatProblem.MissingPrefix, OpenRouterKeyValidator.formatProblem("or-v1-0000000000000000000"))
        assertEquals(KeyFormatProblem.Whitespace, OpenRouterKeyValidator.formatProblem("sk-or-v1-with space00000"))
        assertEquals(KeyFormatProblem.Empty, OpenRouterKeyValidator.formatProblem("   "))
        assertNull(OpenRouterKeyValidator.formatMessage("sk-or-v1-testkey123456"))
    }

    @Test
    fun invalidFormatDoesNotCallNetwork() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            respond(content = ByteReadChannel(""), status = HttpStatusCode.OK)
        }
        val validator = OpenRouterKeyValidator(httpClient(engine), OpenRouterConfig())
        assertEquals(KeyValidationResult.InvalidFormat, validator.validate("nope"))
        assertEquals(0, calls)
    }

    private fun httpClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(HttpTimeout)
    }
}
