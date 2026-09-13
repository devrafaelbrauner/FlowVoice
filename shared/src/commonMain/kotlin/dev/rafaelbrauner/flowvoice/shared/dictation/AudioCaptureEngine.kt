package dev.rafaelbrauner.flowvoice.shared.dictation

interface AudioCaptureEngine {
    val format: AudioFormat
    val isRunning: Boolean

    suspend fun start(onFrame: suspend (AudioFrame) -> Unit)

    suspend fun start(
        onFrame: suspend (AudioFrame) -> Unit,
        onError: suspend (AudioCaptureException) -> Unit
    ) = start(onFrame)

    fun stop()
}
