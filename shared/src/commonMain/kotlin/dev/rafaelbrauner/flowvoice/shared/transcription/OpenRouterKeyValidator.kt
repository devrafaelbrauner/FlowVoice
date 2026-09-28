package dev.rafaelbrauner.flowvoice.shared.transcription

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException

class OpenRouterKeyValidator(
    private val http: HttpClient,
    private val config: OpenRouterConfig
) {
    fun validateFormat(key: String): Boolean = isValidFormat(key)

    // Vai ao /api/v1/key, que autentica. O /api/v1/models responde 200 com qualquer chave, e até sem
    // chave, então aprovava chave falsa (smoke da 0.6.0 no emulador, 2026-09-27).
    suspend fun validate(key: String): KeyValidationResult {
        if (!isValidFormat(key)) return KeyValidationResult.InvalidFormat
        return try {
            val response = http.get("${config.baseUrl}${config.keyPath}") {
                timeout {
                    connectTimeoutMillis = config.connectTimeoutMs
                    requestTimeoutMillis = config.requestTimeoutMs
                    socketTimeoutMillis = config.requestTimeoutMs
                }
                header(HttpHeaders.Authorization, "Bearer ${key.trim()}")
            }
            response.bodyAsText()
            when (response.status.value) {
                200 -> KeyValidationResult.Valid
                401, 403 -> KeyValidationResult.Rejected
                else -> KeyValidationResult.Unavailable
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            KeyValidationResult.Unavailable
        }
    }

    companion object {
        const val MIN_LENGTH = 20

        fun isValidFormat(key: String): Boolean = formatProblem(key) == null

        fun formatProblem(key: String): KeyFormatProblem? {
            val trimmed = key.trim()
            return when {
                trimmed.isEmpty() -> KeyFormatProblem.Empty
                !trimmed.startsWith("sk-") -> KeyFormatProblem.MissingPrefix
                trimmed.any { it.isWhitespace() } -> KeyFormatProblem.Whitespace
                trimmed.length < MIN_LENGTH -> KeyFormatProblem.TooShort
                else -> null
            }
        }

        // A regra que falhou, não a lista de todas: "começa com sk-" para uma chave que começa com
        // sk- e só é curta confundia (smoke da 0.6.0).
        fun formatMessage(key: String): String? = when (formatProblem(key)) {
            null -> null
            KeyFormatProblem.Empty -> "Cole a chave OpenRouter."
            KeyFormatProblem.MissingPrefix -> "Formato inválido: a chave da OpenRouter começa com sk-."
            KeyFormatProblem.Whitespace -> "Formato inválido: a chave não tem espaços no meio."
            KeyFormatProblem.TooShort ->
                "Chave curta demais: tem ${key.trim().length} caracteres, e uma chave da OpenRouter tem " +
                    "pelo menos $MIN_LENGTH. Copie a chave inteira."
        }
    }
}

enum class KeyFormatProblem { Empty, MissingPrefix, Whitespace, TooShort }
