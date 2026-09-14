package dev.rafaelbrauner.flowvoice.shared.dictation

class DictationWindow(
    val index: Int,
    val pcm: ByteArray,
    val format: AudioFormat,
    val startedAtMs: Long,
    val finishedAtMs: Long
) {
    init {
        require(index >= 0) { "index must be non-negative" }
        require(finishedAtMs >= startedAtMs) { "finishedAtMs must be greater than or equal to startedAtMs" }
    }

    val durationMs: Long
        get() = finishedAtMs - startedAtMs

    override fun toString(): String =
        "DictationWindow(index=$index, pcmBytes=${pcm.size}, durationMs=$durationMs)"
}