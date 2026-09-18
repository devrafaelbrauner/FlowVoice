package dev.rafaelbrauner.flowvoice.shared.audio

enum class VadFrameKind {
    SPEECH,
    NOISE
}

sealed interface VadFrame {
    val kind: VadFrameKind
    val isSpeech: Boolean get() = kind == VadFrameKind.SPEECH

    data class Speech(val data: FloatArray) : VadFrame {
        override val kind: VadFrameKind = VadFrameKind.SPEECH
        override fun equals(other: Any?): Boolean =
            other is Speech && data.contentEquals(other.data)
        override fun hashCode(): Int = data.contentHashCode()
    }

    data object Noise : VadFrame {
        override val kind: VadFrameKind = VadFrameKind.NOISE
    }
}

data class VadTailReport(
    val withheldFrames: Int,
    val withheldVoicedFrames: Int,
    val inSpeech: Boolean,
    val onsetCounter: Int,
    val hangoverCounter: Int
)

interface VoiceActivityDetector {
    val frameSamples: Int
    fun pushFrame(frame: FloatArray): VadFrame
    fun setHangoverFrames(frames: Int)
    fun tailReport(): VadTailReport?
    fun reset()
}
