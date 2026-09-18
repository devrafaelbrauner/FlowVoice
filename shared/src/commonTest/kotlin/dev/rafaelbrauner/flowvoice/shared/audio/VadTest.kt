package dev.rafaelbrauner.flowvoice.shared.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class ScriptedVad(private val script: ArrayDeque<Boolean>) : VoiceActivityDetector {
    override val frameSamples: Int = 4
    override fun pushFrame(frame: FloatArray): VadFrame =
        if (script.removeFirstOrNull() == true) VadFrame.Speech(frame.copyOf()) else VadFrame.Noise
    override fun setHangoverFrames(frames: Int) = Unit
    override fun tailReport(): VadTailReport? = null
    override fun reset() = Unit
}

private fun frame(value: Float, size: Int = 4): FloatArray = FloatArray(size) { value }

private fun smoothed(script: List<Boolean>, onsetFrames: Int = 2): SmoothedVad =
    SmoothedVad(ScriptedVad(ArrayDeque(script)), prefillFrames = 3, hangoverFrames = 2, onsetFrames = onsetFrames)

class VadConfigTest {

    @Test
    fun framesForDurationPreservesHandyTimingsFor480() {
        assertEquals(15, framesForDurationMs(450, 480))
        assertEquals(55, framesForDurationMs(1650, 480))
        assertEquals(2, framesForDurationMs(60, 480))
    }

    @Test
    fun framesForDurationRoundsUpFor256() {
        assertEquals(29, framesForDurationMs(450, 256))
        assertEquals(104, framesForDurationMs(1650, 256))
        assertEquals(4, framesForDurationMs(60, 256))
    }

    @Test
    fun defaultConfigMatchesHandyProfile() {
        val config = VadConfig()
        assertEquals(16000, config.sampleRateHz)
        assertEquals(480, config.frameSamples)
        assertEquals(15, config.prefillFrames)
        assertEquals(15, config.offlineHangoverFrames)
        assertEquals(55, config.streamingHangoverFrames)
        assertEquals(2, config.onsetFrames)
    }
}

class SmoothedVadTest {

    @Test
    fun onsetRequiresTwoConsecutiveVoicedFrames() {
        val vad = smoothed(listOf(false, true, true))
        assertFalse(vad.pushFrame(frame(0.1f)).isSpeech)
        assertFalse(vad.pushFrame(frame(0.9f)).isSpeech)
        assertTrue(vad.pushFrame(frame(0.9f)).isSpeech)
    }

    @Test
    fun hangoverEmitsTailAfterSpeechEnds() {
        val vad = smoothed(listOf(true, true, false, false, false))
        assertFalse(vad.pushFrame(frame(0.1f)).isSpeech)
        assertTrue(vad.pushFrame(frame(0.9f)).isSpeech)
        assertTrue(vad.pushFrame(frame(0.1f)).isSpeech)
        assertTrue(vad.pushFrame(frame(0.1f)).isSpeech)
        assertFalse(vad.pushFrame(frame(0.1f)).isSpeech)
    }

    @Test
    fun tailReportCountsWithheldOnsetTail() {
        val vad = smoothed(listOf(false, true))
        vad.pushFrame(frame(0.1f))
        vad.pushFrame(frame(0.9f))
        val report = vad.tailReport()!!
        assertEquals(2, report.withheldFrames)
        assertEquals(1, report.withheldVoicedFrames)
        assertEquals(1, report.onsetCounter)
        assertFalse(report.inSpeech)
    }

    @Test
    fun tailReportCountsOnlyTrailingRun() {
        val vad = smoothed(listOf(true, true, false, false, false, false))
        repeat(4) { vad.pushFrame(frame(0.2f)) }
        vad.pushFrame(frame(0.1f))
        vad.pushFrame(frame(0.1f))
        val report = vad.tailReport()!!
        assertEquals(2, report.withheldFrames)
        assertEquals(0, report.withheldVoicedFrames)
    }

    @Test
    fun resetClearsSmoothingState() {
        val vad = smoothed(listOf(true, true, true, true))
        vad.pushFrame(frame(0.1f))
        vad.pushFrame(frame(0.9f))
        vad.reset()
        val report = vad.tailReport()!!
        assertEquals(0, report.withheldFrames)
        assertEquals(0, report.onsetCounter)
        assertFalse(report.inSpeech)
    }
}

class EnergyVadTest {

    @Test
    fun silenceIsNoiseAndLoudIsSpeech() {
        val vad = EnergyVad(frameSamples = 4, energyThreshold = 0.01f)
        assertFalse(vad.pushFrame(frame(0.0f)).isSpeech)
        assertTrue(vad.pushFrame(frame(0.9f)).isSpeech)
    }
}
