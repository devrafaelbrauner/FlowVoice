package dev.rafaelbrauner.flowvoice.shared.dictation

import kotlin.math.max

// Frames de captura de 100 ms (P140). A janela só é cortada quando um frame chega, então o tamanho do
// frame limita a resolução da pausa e soma até esse tanto à latência (no S26 eram 256 ms).
object CaptureFrames {
    const val FRAME_DURATION_MS = 100L
    private const val RECORD_BUFFER_READS = 4

    fun readBytes(format: AudioFormat): Int =
        (format.sampleRate * FRAME_DURATION_MS / 1_000L).toInt().coerceAtLeast(1) * format.bytesPerFrame

    // O buffer do sistema guarda algumas leituras, para a thread de captura poder atrasar um pouco sem
    // perder áudio. Ele não atrasa a leitura, que é bloqueante e volta assim que há um frame.
    fun recordBufferBytes(format: AudioFormat, platformMinimumBytes: Int): Int {
        require(platformMinimumBytes > 0) { "platformMinimumBytes must be positive" }
        val wanted = max(platformMinimumBytes * 2, readBytes(format) * RECORD_BUFFER_READS)
        val frame = format.bytesPerFrame
        return (wanted + frame - 1) / frame * frame
    }
}
