package dev.rafaelbrauner.flowvoice.shared.transcription

data class OpenRouterConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    // Modelo vazio herda o padrão do benchmark F05 em tempo de uso: `TranscriptionModels.resolve`
    // lê o `PreferencesStore` via `TranscriptionModel` (AppModule) e cai para `DEFAULT_MODEL` quando
    // o aparelho ainda não escolheu nada. Nunca persista "" como escolha — é só o estado inicial.
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

    // Informações da chave usada na chamada: 200 para chave válida, 401 para chave inválida ou
    // ausente (https://openrouter.ai/docs/api/api-reference/api-keys/get-current-key). Não gasta
    // crédito. Conferido por curl em 2026-09-27: 401 com chave falsa e sem chave.
    val keyPath: String
        get() = "/api/v1/key"

    companion object {
        const val DEFAULT_BASE_URL = "https://openrouter.ai"
        // Medição de modelos da nuvem (docs/medicao-modelos-nuvem.md, 2026-09-28): a melhor nota de ortografia e
        // pontuação entre as 24 transcrições da OpenRouter, e a menor latência entre as cinco melhores. Antes,
        // `openai/gpt-transcribe` (benchmark F05 no S26, P136), agora o 2º.
        const val DEFAULT_MODEL = "deepgram/nova-3"
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
        // no que é enviado. Com ~2,5 s de média, 90 janelas dão uma sessão útil de ~3,7 min, mais que
        // os ~3 min de antes da P143: o teto de 90 segue valendo. O teto continua contado na
        // submissão (P107), e silêncio só sai no teto de ~4 s, então uma captura muda ainda para, em
        // ~6 min.
        //
        // O teto conta janelas, não chamadas HTTP (N5). Cada janela pode virar até 1 + `maxRetries`
        // chamadas (408, 429, 5xx, rede, timeout), e a primeira janela com contexto de cada modelo
        // pode refazer o pedido uma vez em `json` quando o `verbose_json` é recusado. Contar essas
        // chamadas no teto fecharia o microfone mais cedo justamente numa rede ruim, no meio de uma
        // nota; o teto é o limite de duração da sessão, e o gasto por janela já é limitado pelas
        // tentativas. Custo com o gpt-transcribe (~US$ 0,0011 por 15 s de áudio): 90 × (4,3 s + 1 s)
        // ≈ US$ 0,035 quando cada janela vai uma vez só; o pior caso, com todas as 4 tentativas
        // cobradas em todas as janelas, é ~4× isso, ≈ US$ 0,14.
        const val DEFAULT_MAX_REQUESTS_PER_SESSION = 90
    }
}
