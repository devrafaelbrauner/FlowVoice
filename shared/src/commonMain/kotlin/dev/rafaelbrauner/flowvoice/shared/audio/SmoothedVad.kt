package dev.rafaelbrauner.flowvoice.shared.audio

import kotlin.math.sqrt

class SmoothedVad(
    private val inner: VoiceActivityDetector,
    prefillFrames: Int,
    hangoverFrames: Int,
    private val onsetFrames: Int
) : VoiceActivityDetector {

    private data class BufferedFrame(
        val samples: FloatArray,
        var emitted: Boolean = false,
        var voiced: Boolean = false
    )

    private val buffer = ArrayDeque<BufferedFrame>()
    private var prefillFrames: Int = prefillFrames
    private var hangoverFrames: Int = hangoverFrames
    private var hangoverCounter: Int = 0
    private var onsetCounter: Int = 0
    private var inSpeech: Boolean = false

    init {
        require(prefillFrames >= 0) { "prefillFrames must be non-negative" }
        require(hangoverFrames >= 0) { "hangoverFrames must be non-negative" }
        require(onsetFrames > 0) { "onsetFrames must be positive" }
    }

    override val frameSamples: Int get() = inner.frameSamples

    override fun pushFrame(frame: FloatArray): VadFrame {
        buffer.addLast(BufferedFrame(frame.copyOf()))
        while (buffer.size > prefillFrames + 1) buffer.removeFirst()

        val isVoice = inner.pushFrame(frame).isSpeech
        buffer.last().voiced = isVoice

        return when {
            !inSpeech && isVoice -> {
                onsetCounter += 1
                if (onsetCounter >= onsetFrames) {
                    inSpeech = true
                    hangoverCounter = hangoverFrames
                    onsetCounter = 0
                    val out = mutableListOf<Float>()
                    for (buffered in buffer) {
                        out.addAll(buffered.samples.asList())
                        buffered.emitted = true
                    }
                    VadFrame.Speech(out.toFloatArray())
                } else {
                    VadFrame.Noise
                }
            }
            inSpeech && isVoice -> {
                hangoverCounter = hangoverFrames
                buffer.last().emitted = true
                VadFrame.Speech(frame.copyOf())
            }
            inSpeech && !isVoice -> {
                if (hangoverCounter > 0) {
                    hangoverCounter -= 1
                    buffer.last().emitted = true
                    VadFrame.Speech(frame.copyOf())
                } else {
                    inSpeech = false
                    VadFrame.Noise
                }
            }
            else -> {
                onsetCounter = 0
                VadFrame.Noise
            }
        }
    }

    override fun setHangoverFrames(frames: Int) {
        hangoverFrames = frames
    }

    override fun tailReport(): VadTailReport? {
        var withheld = 0
        var withheldVoiced = 0
        for (frame in buffer.asReversed()) {
            if (frame.emitted) break
            withheld += 1
            if (frame.voiced) withheldVoiced += 1
        }
        return VadTailReport(
            withheldFrames = withheld,
            withheldVoicedFrames = withheldVoiced,
            inSpeech = inSpeech,
            onsetCounter = onsetCounter,
            hangoverCounter = hangoverCounter
        )
    }

    override fun reset() {
        inner.reset()
        buffer.clear()
        hangoverCounter = 0
        onsetCounter = 0
        inSpeech = false
    }
}

class EnergyVad(
    override val frameSamples: Int = 480,
    private val energyThreshold: Float = 0.001f
) : VoiceActivityDetector {

    override fun pushFrame(frame: FloatArray): VadFrame {
        require(frame.size == frameSamples) {
            "expected $frameSamples samples, got ${frame.size}"
        }
        var sum = 0.0
        for (sample in frame) sum += sample * sample
        val rms = sqrt(sum / frame.size).toFloat()
        return if (rms > energyThreshold) VadFrame.Speech(frame.copyOf()) else VadFrame.Noise
    }

    override fun setHangoverFrames(frames: Int) = Unit

    override fun tailReport(): VadTailReport? = null

    override fun reset() = Unit
}
