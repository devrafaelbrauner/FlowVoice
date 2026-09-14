package dev.rafaelbrauner.flowvoice.shared.transcription

sealed class TranscriptionError(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    class Network(cause: Throwable) : TranscriptionError("network error", cause)
    class Timeout(cause: Throwable? = null) : TranscriptionError("timeout", cause)
    class RateLimit(val retryAfterMs: Long? = null) : TranscriptionError("rate limited")
    class InvalidKey : TranscriptionError("invalid api key")
    class InvalidResponse(detail: String) : TranscriptionError(detail)
    class Server(val statusCode: Int) : TranscriptionError("server error $statusCode")
    class SessionBudgetExceeded : TranscriptionError("session request budget exceeded")

    val isRetryable: Boolean
        get() = this is Network || this is Timeout || this is RateLimit || this is Server

    val kind: String
        get() = when (this) {
            is Network -> "network"
            is Timeout -> "timeout"
            is RateLimit -> "rate_limit"
            is InvalidKey -> "invalid_key"
            is InvalidResponse -> "invalid_response"
            is Server -> "server"
            is SessionBudgetExceeded -> "budget"
        }
}
