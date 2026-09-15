package dev.rafaelbrauner.flowvoice.shared.transcription

data class OpenRouterConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_MODEL,
    val language: String = DEFAULT_LANGUAGE,
    val temperature: Double = DEFAULT_TEMPERATURE,
    val prompt: String? = null,
    val connectTimeoutMs: Long = DEFAULT_CONNECT_TIMEOUT_MS,
    val requestTimeoutMs: Long = DEFAULT_REQUEST_TIMEOUT_MS,
    val maxRetries: Int = DEFAULT_MAX_RETRIES,
    val initialBackoffMs: Long = DEFAULT_INITIAL_BACKOFF_MS,
    val maxBackoffMs: Long = DEFAULT_MAX_BACKOFF_MS,
    val maxRequestsPerSession: Int = DEFAULT_MAX_REQUESTS_PER_SESSION
) {
    init {
        require(baseUrl.isNotBlank()) { "baseUrl must not be blank" }
        require(model.isNotBlank()) { "model must not be blank" }
        require(temperature in 0.0..1.0) { "temperature must be in [0, 1]" }
        require(connectTimeoutMs > 0L) { "connectTimeoutMs must be positive" }
        require(requestTimeoutMs > 0L) { "requestTimeoutMs must be positive" }
        require(maxRetries >= 0) { "maxRetries must be non-negative" }
        require(initialBackoffMs > 0L) { "initialBackoffMs must be positive" }
        require(maxBackoffMs >= initialBackoffMs) { "maxBackoffMs must be >= initialBackoffMs" }
        require(maxRequestsPerSession > 0) { "maxRequestsPerSession must be positive" }
    }

    val transcriptionsPath: String
        get() = "/api/v1/audio/transcriptions"

    val modelsPath: String
        get() = "/api/v1/models"

    companion object {
        const val DEFAULT_BASE_URL = "https://openrouter.ai"
        // Benchmark F05 no S26 (P136, docs/PLAN.md): WER 0 nas rodadas de 13/set e 15/set.
        const val DEFAULT_MODEL = "openai/gpt-transcribe"
        const val DEFAULT_LANGUAGE = "pt"
        // Nem a OpenRouter nem a OpenAI documentam o padrão de temperature na transcrição; 0 é o
        // valor mais determinístico e não depende do padrão de cada provedor (P137).
        const val DEFAULT_TEMPERATURE = 0.0
        const val DEFAULT_CONNECT_TIMEOUT_MS = 10_000L
        const val DEFAULT_REQUEST_TIMEOUT_MS = 30_000L
        const val DEFAULT_MAX_RETRIES = 3
        const val DEFAULT_INITIAL_BACKOFF_MS = 500L
        const val DEFAULT_MAX_BACKOFF_MS = 8_000L
        const val DEFAULT_MAX_REQUESTS_PER_SESSION = 30
    }
}
