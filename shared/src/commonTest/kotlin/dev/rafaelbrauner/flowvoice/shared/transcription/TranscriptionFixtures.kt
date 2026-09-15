package dev.rafaelbrauner.flowvoice.shared.transcription

import dev.rafaelbrauner.flowvoice.shared.dictation.AudioFormat
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow

internal fun testWindow(index: Int = 0, durationMs: Long = 1_000L, silent: Boolean = false): DictationWindow {
    val sampleCount = ((durationMs * AudioFormat.DEFAULT.sampleRate) / 1_000L).toInt().coerceAtLeast(1)
    val byteCount = sampleCount * AudioFormat.DEFAULT.bytesPerFrame
    return DictationWindow(
        index = index,
        pcm = if (silent) ByteArray(byteCount) else voicedPcm(byteCount),
        format = AudioFormat.DEFAULT,
        startedAtMs = index * durationMs,
        finishedAtMs = (index + 1) * durationMs
    )
}

// PCM 16 bits com amostras de ±1000: sinal audível, ao contrário do ByteArray zerado (silêncio digital).
internal fun voicedPcm(byteCount: Int): ByteArray = ByteArray(byteCount) { VOICED_PATTERN[it % VOICED_PATTERN.size] }

private val VOICED_PATTERN = byteArrayOf(0xE8.toByte(), 0x03, 0x18, 0xFC.toByte())
