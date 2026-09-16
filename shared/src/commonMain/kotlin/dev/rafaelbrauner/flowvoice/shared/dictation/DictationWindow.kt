package dev.rafaelbrauner.flowvoice.shared.dictation

class DictationWindow(
    val index: Int,
    val pcm: ByteArray,
    val format: AudioFormat,
    val startedAtMs: Long,
    val finishedAtMs: Long,
    val cut: WindowCut = WindowCut.Target,
    val noiseFloor: Int? = null,
    // Fim do áudio da janela anterior (P143), repetido só no que vai ao modelo. Fica fora do `pcm` de
    // propósito: a linha do tempo, a soma dos bytes da sessão e o silêncio digital (P124) continuam
    // olhando apenas o áudio próprio da janela.
    val contextPcm: ByteArray = ByteArray(0),
    // Tempo de fala própria, medido só no `pcm` e com o limiar de fala do endpointer (P144). Nulo =
    // não medido (sem endpointing): conta como tendo fala, para nunca pular por falta de medição.
    val voicedMs: Long? = null
) {
    init {
        require(index >= 0) { "index must be non-negative" }
        require(finishedAtMs >= startedAtMs) { "finishedAtMs must be greater than or equal to startedAtMs" }
    }

    val durationMs: Long
        get() = finishedAtMs - startedAtMs

    val contextDurationMs: Long
        get() = format.durationMs(contextPcm.size)

    // O que é enviado à transcrição: contexto primeiro, depois o áudio da janela.
    val transmittedPcm: ByteArray
        get() = if (contextPcm.isEmpty()) pcm else contextPcm + pcm

    val transmittedDurationMs: Long
        get() = contextDurationMs + durationMs

    val hasOwnSpeech: Boolean
        get() = voicedMs?.let { it > 0L } ?: true

    // Janela que só teria o contexto para transcrever (P144): o modelo devolve o contexto como texto
    // novo e ele entra no campo. Vale só para os cortes que, por construção, não esperam fala —
    // `leading` (cortado antes de a fala começar) e `flush` (resto do fim). `pause` e `ceiling`
    // sempre vão à API, para quem fala baixo não perder o ditado inteiro.
    val onlyContext: Boolean
        get() = !hasOwnSpeech && (cut == WindowCut.Leading || cut == WindowCut.Flush)

    override fun toString(): String =
        "DictationWindow(index=$index, pcmBytes=${pcm.size}, durationMs=$durationMs, cut=$cut, " +
            "contextMs=$contextDurationMs)"
}

// Por que a janela terminou onde terminou; vai no log `dictation_window` para medir o corte (P140).
enum class WindowCut {
    // Alvo fixo, com ou sem busca do trecho mais silencioso perto dele.
    Target,
    // Pausa natural depois de fala, no meio da pausa.
    Pause,
    // No teto, logo antes da fala que veio depois de um trecho sem fala.
    Leading,
    // No teto, sem pausa: trecho mais silencioso perto do alvo.
    Ceiling,
    // Resto do áudio no fim da sessão.
    Flush
}
