package dev.rafaelbrauner.flowvoice.shared.dictation

// Decide, sem rede e sem Android, se a janela vale uma requisição de transcrição (P151).
//
// A P144 já barrava a janela sem fala, mas só nos cortes `leading` e `flush`; `ceiling` ia sempre à
// API. Com o piso de ruído alto isso custa caro: no S26 (2026-09-16 10:42), nos 28 s entre o fim da
// fala e o toque que encerrou, as janelas 6, 7 e 8 foram enviadas como `ceiling` (`durationMs=3180`,
// `3680` e `4180`, `contextMs=1000`) e voltaram com `chars=0` — três chamadas pagas, nenhuma
// palavra. O que decide agora é a fala medida (`voicedMs`), não o corte.
//
// Limiar = `MIN_VOICED_RUN_MS` (80 ms), o menor valor possível acima de zero: `voicedMs` é sempre um
// múltiplo de blocos de trechos com pelo menos um `minVoicedRunMs`, então exigir 80 ms é o mesmo que
// exigir **um** trecho contínuo de fala e nada mais. Um limiar maior começaria a comer palavra curta
// de verdade ("sim", "não", "parar", 200–400 ms), e o risco aqui é engolir fala.
//
// Fala baixa continua indo: o limiar de fala do endpointer acompanha o ruído da sessão
// (`max(minSpeechLevel, speechFactor × piso)`), nunca é fixo (P124), e 400 ms de voz real dão
// `voicedMs=400`, cinco vezes a barreira. `voicedMs` nulo é "não medido" (sem endpointing) e vai
// à API: falta de medição nunca vira silêncio.
object WindowSpeechGate {
    const val MIN_VOICED_MS = SpeechEndpointing.MIN_VOICED_RUN_MS

    // A janela cortada antes da fala (ou o resto do fim) só teria o contexto sobreposto da P143 para
    // transcrever, e o modelo devolve o contexto como texto novo, que entra no campo (P144).
    const val REASON_ONLY_CONTEXT = "sem_fala"

    // Mesma condição, nos cortes que a P144 não cobria (`pause` e `ceiling`): razão própria para a
    // economia da P151 aparecer separada no log do aparelho.
    const val REASON_NOT_ENOUGH_SPEECH = "fala_insuficiente"

    // A janela não é mais alta que outra que já voltou vazia nesta sessão (P153): o modelo já disse,
    // e já foi pago, que naquele nível não há palavra.
    const val REASON_LEVEL_ALREADY_EMPTY = "nivel_ja_vazio"

    fun skipReason(window: DictationWindow): String? {
        val voicedMs = window.voicedMs ?: return null
        if (voicedMs >= MIN_VOICED_MS) return null
        return when (window.cut) {
            WindowCut.Leading, WindowCut.Flush -> REASON_ONLY_CONTEXT
            else -> REASON_NOT_ENOUGH_SPEECH
        }
    }
}
