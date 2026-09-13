package dev.rafaelbrauner.flowvoice.shared.dictation

interface AudioCaptureEngine {
    val format: AudioFormat
    val isRunning: Boolean

    suspend fun start(onFrame: suspend (AudioFrame) -> Unit)
    fun stop()
}