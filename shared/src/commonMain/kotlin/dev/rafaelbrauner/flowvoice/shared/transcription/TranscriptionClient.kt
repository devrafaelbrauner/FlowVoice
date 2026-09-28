package dev.rafaelbrauner.flowvoice.shared.transcription

import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow

// Cancelar um pedido é cancelar o Job de quem o fez (N7). O cliente é único no app e atende o
// ditado e o benchmark ao mesmo tempo: um `cancel()` no cliente derrubava o pedido de quem chamou
// por último, que podia ser o outro.
interface TranscriptionClient {
    suspend fun transcribe(
        window: DictationWindow,
        apiKey: String,
        model: String? = null
    ): TranscriptionResult
}
