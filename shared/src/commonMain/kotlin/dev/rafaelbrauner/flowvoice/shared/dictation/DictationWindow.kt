package dev.rafaelbrauner.flowvoice.shared.dictation

class DictationWindow(
    val index: Int,
    val pcm: ByteArray,
    val format: AudioFormat,
    val startedAtMs: Long,
    val finishedAtMs: Long,
    val cut: WindowCut = WindowCut.Target,
    val noiseFloor: Int? = null
) {
    init {
        require(index >= 0) { "index must be non-negative" }
        require(finishedAtMs >= startedAtMs) { "finishedAtMs must be greater than or equal to startedAtMs" }
    }

    val durationMs: Long
        get() = finishedAtMs - startedAtMs

    override fun toString(): String =
        "DictationWindow(index=$index, pcmBytes=${pcm.size}, durationMs=$durationMs, cut=$cut)"
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
