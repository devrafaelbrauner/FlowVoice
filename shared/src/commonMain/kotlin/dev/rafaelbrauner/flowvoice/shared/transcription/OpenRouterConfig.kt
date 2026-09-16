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
        // Janelas cortadas na pausa (P140, P143) têm de ~2 a 4,3 s, mais 1 s de contexto sobreposto
        // no que é enviado. Com ~2,5 s de média, 90 pedidos dão uma sessão útil de ~3,7 min, mais que
        // os ~3 min de antes da P143: o teto de 90 segue valendo. O teto continua contado na
        // submissão (P107), e silêncio só sai no teto de ~4 s, então uma captura muda ainda para, em
        // ~6 min. Custo máximo por sessão com o gpt-transcribe (~US$ 0,0011 por 15 s de áudio):
        // 90 × (4,3 s + 1 s) ≈ US$ 0,035.
        const val DEFAULT_MAX_REQUESTS_PER_SESSION = 90
    }
}
