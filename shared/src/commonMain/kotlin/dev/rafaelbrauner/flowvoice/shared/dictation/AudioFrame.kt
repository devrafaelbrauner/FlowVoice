package dev.rafaelbrauner.flowvoice.shared.dictation

class AudioFrame(
    val pcm: ByteArray,
    val format: AudioFormat = AudioFormat.DEFAULT
) {
    val durationMs: Long
        get() = format.durationMs(pcm.size)

    override fun toString(): String = "AudioFrame(pcmBytes=${pcm.size}, format=$format)"
}