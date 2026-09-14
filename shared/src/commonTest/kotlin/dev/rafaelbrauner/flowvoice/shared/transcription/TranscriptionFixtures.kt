package dev.rafaelbrauner.flowvoice.shared.transcription

import dev.rafaelbrauner.flowvoice.shared.dictation.AudioFormat
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow

internal fun testWindow(index: Int = 0, durationMs: Long = 1_000L): DictationWindow {
    val sampleCount = ((durationMs * AudioFormat.DEFAULT.sampleRate) / 1_000L).toInt().coerceAtLeast(1)
    return DictationWindow(
        index = index,
        pcm = ByteArray(sampleCount * AudioFormat.DEFAULT.bytesPerFrame),
        format = AudioFormat.DEFAULT,
        startedAtMs = index * durationMs,
        finishedAtMs = (index + 1) * durationMs
    )
}
