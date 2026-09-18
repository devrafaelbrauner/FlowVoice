package dev.rafaelbrauner.flowvoice.shared.audio

import kotlin.math.ceil

data class VadConfig(
    val sampleRateHz: Int = 16000,
    val frameMs: Int = 30,
    val frameSamples: Int = 480,
    val prefillMs: Long = 450,
    val offlineHangoverMs: Long = 450,
    val streamingHangoverMs: Long = 1650,
    val onsetMs: Long = 60,
    val threshold: Float = 0.5f,
    val extraRecordingBufferMs: Long = 0L,
    val vadEnabled: Boolean = true
) {
    val prefillFrames: Int = framesForDurationMs(prefillMs, frameSamples, sampleRateHz)
    val offlineHangoverFrames: Int = framesForDurationMs(offlineHangoverMs, frameSamples, sampleRateHz)
    val streamingHangoverFrames: Int = framesForDurationMs(streamingHangoverMs, frameSamples, sampleRateHz)
    val onsetFrames: Int = framesForDurationMs(onsetMs, frameSamples, sampleRateHz)
}

fun framesForDurationMs(ms: Long, frameSamples: Int, sampleRateHz: Int = 16000): Int {
    require(frameSamples > 0) { "frameSamples must be positive" }
    return ceil(ms * sampleRateHz.toDouble() / (frameSamples * 1000)).toInt()
}
