package dev.rafaelbrauner.flowvoice.shared.dictation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AudioFormatTest {
    @Test
    fun defaultIs16Khz16BitMono() {
        val format = AudioFormat.DEFAULT

        assertEquals(16_000, format.sampleRate)
        assertEquals(16, format.sampleBits)
        assertEquals(1, format.channels)
        assertEquals(2, format.bytesPerSample)
        assertEquals(2, format.bytesPerFrame)
    }

    @Test
    fun durationCalculatesMillisecondsFromPcmSize() {
        val format = AudioFormat.DEFAULT

        assertEquals(100L, format.durationMs(3_200))
        assertEquals(250L, format.durationMs(8_000))
        assertEquals(0L, format.durationMs(0))
        assertEquals(0L, format.durationMs(-3_200))
    }

    @Test
    fun bytesPerFrameAccountsChannels() {
        val stereo44Khz = AudioFormat(sampleRate = 44_100, sampleBits = 16, channels = 2)

        assertEquals(4, stereo44Khz.bytesPerFrame)
        assertEquals(500L, stereo44Khz.durationMs(88_200))
    }

    @Test
    fun invalidFormatThrows() {
        assertFailsWith<IllegalArgumentException> { AudioFormat(sampleRate = 0) }
        assertFailsWith<IllegalArgumentException> { AudioFormat(sampleBits = 12) }
        assertFailsWith<IllegalArgumentException> { AudioFormat(channels = 0) }
    }
}