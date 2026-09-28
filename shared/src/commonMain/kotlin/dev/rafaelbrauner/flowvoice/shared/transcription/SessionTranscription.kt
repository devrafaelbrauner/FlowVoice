package dev.rafaelbrauner.flowvoice.shared.transcription

import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow
import kotlinx.coroutines.flow.StateFlow

// O que o pipeline usa de quem transcreve as janelas de uma sessão: a nuvem (uma requisição por
// janela) ou o motor no aparelho (um fluxo contínuo cortado nas mesmas janelas). Os pedaços saem
// como `TranscriptionSegment` por janela, e daí em diante o caminho é o mesmo — digitação direta com
// as mesmas guardas, pendente, barra de revisão e nota.
interface SessionTranscription {
    val segments: StateFlow<List<TranscriptionSegment>>

    fun submit(window: DictationWindow)

    fun cancel()

    suspend fun awaitIdle()
}
