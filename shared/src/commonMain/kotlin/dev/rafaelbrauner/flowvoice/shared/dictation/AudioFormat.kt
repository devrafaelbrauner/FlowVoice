package dev.rafaelbrauner.flowvoice.shared.dictation

data class AudioFormat(
    val sampleRate: Int = SAMPLE_RATE,
    val sampleBits: Int = SAMPLE_BITS,
    val channels: Int = CHANNELS
) {
    init {
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(sampleBits > 0 && sampleBits % 8 == 0) { "sampleBits must be a positive multiple of 8" }
        require(channels > 0) { "channels must be positive" }
    }

    val bytesPerSample: Int
        get() = sampleBits / 8

    val bytesPerFrame: Int
        get() = bytesPerSample * channels

    fun durationMs(pcmSize: Int): Long {
        if (pcmSize <= 0) return 0L
        return pcmSize.toLong() * 1000L / (bytesPerFrame * sampleRate.toLong())
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val SAMPLE_BITS = 16
        const val CHANNELS = 1
        val DEFAULT = AudioFormat()
    }
}