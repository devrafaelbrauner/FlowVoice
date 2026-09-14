package dev.rafaelbrauner.flowvoice.shared.transcription

import dev.rafaelbrauner.flowvoice.shared.dictation.AudioFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class WavEncoderTest {
    @Test
    fun wrapsPcmInWavHeader() {
        val pcm = ByteArray(32) { it.toByte() }
        val wav = WavEncoder.encode(pcm, AudioFormat.DEFAULT)
        assertEquals(76, wav.size)
        assertEquals("RIFF", wav.decodeAscii(0, 4))
        assertEquals("WAVE", wav.decodeAscii(8, 4))
        assertEquals("fmt ", wav.decodeAscii(12, 4))
        assertEquals("data", wav.decodeAscii(36, 4))
        assertEquals(16_000, readIntLe(wav, 24))
        assertEquals(32, readIntLe(wav, 40))
        assertEquals(pcm.toList(), wav.copyOfRange(44, wav.size).toList())
    }

    private fun ByteArray.decodeAscii(offset: Int, length: Int): String =
        decodeToString(offset, offset + length)

    private fun readIntLe(bytes: ByteArray, offset: Int): Int {
        val b0 = bytes[offset].toInt() and 0xFF
        val b1 = bytes[offset + 1].toInt() and 0xFF
        val b2 = bytes[offset + 2].toInt() and 0xFF
        val b3 = bytes[offset + 3].toInt() and 0xFF
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }
}
