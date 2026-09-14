package dev.rafaelbrauner.flowvoice.shared.proofreading

import dev.rafaelbrauner.flowvoice.shared.transcription.OpenRouterConfig
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionError
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionErrorClassifier
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class OpenRouterProofreadingClient(
    private val http: HttpClient,
    private val config: OpenRouterConfig
) : ProofreadingClient {
    override suspend fun proofread(text: String, apiKey: String, model: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return trimmed
        return try {
            val response = http.post("${config.baseUrl}/api/v1/chat/completions") {
                timeout {
                    connectTimeoutMillis = config.connectTimeoutMs
                    requestTimeoutMillis = config.requestTimeoutMs
                    socketTimeoutMillis = config.requestTimeoutMs
                }
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header("X-Title", "FlowVoice")
                contentType(ContentType.Application.Json)
                setBody(
                    ChatRequest(
                        model = model,
                        messages = listOf(
                            ChatMessage(
                                role = "system",
                                content = SYSTEM_PROMPT
                            ),
                            ChatMessage(role = "user", content = trimmed)
                        )
                    )
                )
            }
            val raw = response.bodyAsText()
            if (!response.status.isSuccess()) {
                throw TranscriptionErrorClassifier.fromHttpStatus(
                    response.status.value,
                    response.headers[HttpHeaders.RetryAfter]
                )
            }
            val payload = json.decodeFromString(ChatResponse.serializer(), raw)
            payload.choices.firstOrNull()?.message?.content?.trim().orEmpty().ifBlank { trimmed }
        } catch (error: CancellationException) {
            throw error
        } catch (error: TranscriptionError) {
            throw error
        } catch (error: Throwable) {
            throw TranscriptionErrorClassifier.fromThrowable(error)
        }
    }

    companion object {
        const val SYSTEM_PROMPT =
            "Revise pontuação e ortografia em português brasileiro. Devolva apenas o texto corrigido, sem aspas nem comentários."
        private val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
internal data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>
)

@Serializable
internal data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
internal data class ChatResponse(
    val choices: List<ChatChoice> = emptyList()
)

@Serializable
internal data class ChatChoice(
    val message: ChatMessage? = null
)
