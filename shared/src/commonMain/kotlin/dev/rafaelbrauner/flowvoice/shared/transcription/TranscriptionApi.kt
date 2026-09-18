package dev.rafaelbrauner.flowvoice.shared.transcription

import kotlinx.serialization.Serializable

enum class OutputLanguage {
    PT,
    EN,
    AUTO,
    UNKNOWN
}

data class TranscriptionRequest(
    val audio: ByteArray,
    val model: String,
    val language: String = "pt",
    val prompt: String? = null,
    val temperature: Float? = null,
    val translateToEnglish: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TranscriptionRequest) return false
        return model == other.model &&
            language == other.language &&
            prompt == other.prompt &&
            temperature == other.temperature &&
            translateToEnglish == other.translateToEnglish &&
            audio.contentEquals(other.audio)
    }

    override fun hashCode(): Int {
        var result = audio.contentHashCode()
        result = 31 * result + model.hashCode()
        result = 31 * result + language.hashCode()
        result = 31 * result + (prompt?.hashCode() ?: 0)
        result = 31 * result + (temperature?.hashCode() ?: 0)
        result = 31 * result + translateToEnglish.hashCode()
        return result
    }
}

data class TranscriptionResult(
    val text: String,
    val outputLanguage: OutputLanguage,
    val modelId: String,
    val latencyMs: Long
)

@Serializable
data class TranscriptionResponse(
    val text: String = ""
)

sealed class TranscriptionError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class MissingApiKey : TranscriptionError("Chave da API não configurada")
    class InvalidApiKey : TranscriptionError("Chave da API inválida")
    class NoCredits : TranscriptionError("Sem créditos na OpenRouter")
    class ModelNotFound(model: String) : TranscriptionError("Modelo não encontrado — verifique o nome nas configurações ($model)")
    class BadRequest(detail: String) : TranscriptionError(
        "Requisição rejeitada (400) — áudio/modelo/parâmetros inválidos" +
            if (detail.isNotBlank()) ": $detail" else ""
    )
    class RateLimited : TranscriptionError("Limite de requisições atingido — tentando de novo")
    class ServerError(code: Int) : TranscriptionError("Erro $code do servidor")
    class NetworkFailure(cause: Throwable) : TranscriptionError("Falha de rede — verifique a conexão", cause)
    class Timeout : TranscriptionError("Tempo esgotado — áudio muito longo ou rede lenta")
}

interface TranscriptionApi {
    suspend fun transcribe(request: TranscriptionRequest, apiKey: String): TranscriptionResult
}
