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

    suspend fun validate(key: String): KeyValidationResult {
        if (!isValidFormat(key)) return KeyValidationResult.InvalidFormat
        return try {
            val response = http.get("${config.baseUrl}${config.modelsPath}") {
                url.parameters.append("output_modalities", "transcription")
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
        fun isValidFormat(key: String): Boolean {
            val trimmed = key.trim()
            return trimmed.startsWith("sk-") &&
                trimmed.length >= 20 &&
                trimmed.none { it.isWhitespace() }
        }
    }
}
