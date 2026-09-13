package dev.rafaelbrauner.flowvoice.shared.transcription

import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow

interface TranscriptionClient {
    suspend fun transcribe(
        window: DictationWindow,
        apiKey: String,
        model: String? = null
    ): TranscriptionResult
    fun cancel()
}
