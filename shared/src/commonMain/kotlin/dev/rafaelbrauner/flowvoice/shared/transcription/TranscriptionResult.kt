package dev.rafaelbrauner.flowvoice.shared.transcription

data class TranscriptionResult(
    val text: String,
    val model: String,
    val durationMs: Long? = null,
    val costUsd: Double? = null,
    // Quanto do contexto sobreposto (P143) ainda pode estar no começo de `text` (ContextTrim). Nulo: não
    // se sabe, e vale o contexto inteiro da janela.
    val contextInTextMs: Long? = null
)
