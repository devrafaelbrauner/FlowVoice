package dev.rafaelbrauner.flowvoice.shared.transcription

data class TranscriptionResult(
    val text: String,
    val model: String,
    val durationMs: Long? = null,
    val costUsd: Double? = null
)
