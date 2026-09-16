package dev.rafaelbrauner.flowvoice.shared.dictation

// Corte da janela na pausa natural da fala (P140). Os níveis são a média do valor absoluto das
// amostras de 16 bits em blocos de 20 ms: silêncio digital dá 0, ruído de sala algumas dezenas a
// centenas e fala perto do microfone passa de mil.
data class SpeechEndpointing(
    val minBufferedMs: Long = MIN_BUFFERED_MS,
    val minPauseMs: Long = MIN_PAUSE_MS,
    val minVoicedMs: Long = MIN_VOICED_MS,
    val minVoicedRunMs: Long = MIN_VOICED_RUN_MS,
    val leadingPadMs: Long = LEADING_PAD_MS,
    val noiseHistoryMs: Long = NOISE_HISTORY_MS,
    val noisePercentile: Int = NOISE_PERCENTILE,
    val contrastPercentile: Int = CONTRAST_PERCENTILE,
    val pauseFactor: Double = PAUSE_FACTOR,
    val speechFactor: Double = SPEECH_FACTOR,
    val minPauseLevel: Int = MIN_PAUSE_LEVEL,
    val minSpeechLevel: Int = MIN_SPEECH_LEVEL
) {
    init {
        require(minBufferedMs > 0L) { "minBufferedMs must be positive" }
        require(minPauseMs >= BLOCK_MS) { "minPauseMs must be at least one block" }
        require(minVoicedMs >= BLOCK_MS) { "minVoicedMs must be at least one block" }
        require(minVoicedRunMs in BLOCK_MS..minVoicedMs) { "minVoicedRunMs must be in [BLOCK_MS, minVoicedMs]" }
        require(leadingPadMs >= 0L) { "leadingPadMs must not be negative" }
        require(noiseHistoryMs >= BLOCK_MS) { "noiseHistoryMs must be at least one block" }
        require(noisePercentile in 0..100) { "noisePercentile must be in [0, 100]" }
        require(contrastPercentile in noisePercentile..100) { "contrastPercentile must be in [noisePercentile, 100]" }
        require(pauseFactor >= 1.0) { "pauseFactor must be at least 1" }
        require(speechFactor >= pauseFactor) { "speechFactor must be >= pauseFactor" }
        require(minPauseLevel >= 0) { "minPauseLevel must not be negative" }
        require(minSpeechLevel >= minPauseLevel) { "minSpeechLevel must be >= minPauseLevel" }
    }

    companion object {
        const val BLOCK_MS = 20L
        const val MIN_BUFFERED_MS = 1_200L
        const val MIN_PAUSE_MS = 300L
        const val MIN_VOICED_MS = 400L
        const val MIN_VOICED_RUN_MS = 80L
        const val LEADING_PAD_MS = 200L
        const val NOISE_HISTORY_MS = 10_000L
        const val NOISE_PERCENTILE = 10
        const val CONTRAST_PERCENTILE = 90
        const val PAUSE_FACTOR = 2.0
        const val SPEECH_FACTOR = 3.0
        const val MIN_PAUSE_LEVEL = 100
        const val MIN_SPEECH_LEVEL = 250
    }
}
