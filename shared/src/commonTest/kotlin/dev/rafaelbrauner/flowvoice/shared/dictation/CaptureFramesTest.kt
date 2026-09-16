package dev.rafaelbrauner.flowvoice.shared.dictation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CaptureFramesTest {
    @Test
    fun eachReadIs100MsOfPcm() {
        assertEquals(3_200, CaptureFrames.readBytes(AudioFormat.DEFAULT))
        assertEquals(100L, AudioFormat.DEFAULT.durationMs(CaptureFrames.readBytes(AudioFormat.DEFAULT)))
        assertEquals(1_600, CaptureFrames.readBytes(AudioFormat(sampleRate = 8_000)))
        assertEquals(19_200, CaptureFrames.readBytes(AudioFormat(sampleRate = 48_000, channels = 2)))
    }

    @Test
    fun readIsAlwaysAWholeNumberOfSampleFrames() {
        val format = AudioFormat(sampleRate = 11_025, channels = 2)

        assertEquals(0, CaptureFrames.readBytes(format) % format.bytesPerFrame)
    }

    // O buffer do sistema guarda algumas leituras: se a thread de captura atrasar um pouco (a janela
    // saindo), o áudio espera ali em vez de se perder. Ele não atrasa a leitura, que é bloqueante e
    // volta assim que há 100 ms.
    @Test
    fun recordBufferHoldsSeveralReadsAndRespectsThePlatformMinimum() {
        assertEquals(12_800, CaptureFrames.recordBufferBytes(AudioFormat.DEFAULT, platformMinimumBytes = 1_280))
        assertEquals(32_000, CaptureFrames.recordBufferBytes(AudioFormat.DEFAULT, platformMinimumBytes = 16_000))
        assertEquals(40_004, CaptureFrames.recordBufferBytes(AudioFormat(channels = 2), platformMinimumBytes = 20_001))
    }

    @Test
    fun platformMinimumMustBePositive() {
        assertFailsWith<IllegalArgumentException> { CaptureFrames.recordBufferBytes(AudioFormat.DEFAULT, 0) }
    }
}
