package dev.rafaelbrauner.flowvoice.shared.proofreading

import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow

// Um passo só da passada final: o áudio vai a um modelo de chat que ouve e devolve o texto já formatado.
interface AudioChatClient {
    suspend fun transcribeFormatted(window: DictationWindow, apiKey: String, model: String): String
}
