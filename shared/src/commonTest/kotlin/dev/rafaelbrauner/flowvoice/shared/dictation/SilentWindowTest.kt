package dev.rafaelbrauner.flowvoice.shared.dictation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SilentWindowTest {
    @Test
    fun zeroedPcmIsSilent() {
        assertTrue(SilentWindow.detect(window(ShortArray(1_600))))
    }

    @Test
    fun ditherUpToTheThresholdIsStillSilent() {
        val samples = ShortArray(1_600) { if (it % 2 == 0) SilentWindow.MAX_PEAK.toShort() else (-SilentWindow.MAX_PEAK).toShort() }
        assertTrue(SilentWindow.detect(window(samples)))
    }

    @Test
    fun aSingleSampleAboveTheThresholdIsNotSilent() {
        val samples = ShortArray(1_600).also { it[800] = (-(SilentWindow.MAX_PEAK + 1)).toShort() }
        assertFalse(SilentWindow.detect(window(samples)))
    }

    @Test
    fun softSignalIsNotSilent() {
        val samples = ShortArray(1_600) { if (it % 2 == 0) 200 else -200 }
        assertFalse(SilentWindow.detect(window(samples)))
    }

    @Test
    fun formatOtherThan16BitsIsAlwaysSent() {
        val format = AudioFormat(sampleBits = 8)
        assertFalse(SilentWindow.detect(DictationWindow(0, ByteArray(1_600), format, 0L, 100L)))
    }

    private fun window(samples: ShortArray): DictationWindow {
        val pcm = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, sample ->
            pcm[i * 2] = (sample.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        return DictationWindow(0, pcm, AudioFormat.DEFAULT, 0L, 100L)
    }
}
