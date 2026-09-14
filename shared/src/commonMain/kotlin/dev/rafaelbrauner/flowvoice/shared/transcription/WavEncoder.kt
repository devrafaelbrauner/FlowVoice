package dev.rafaelbrauner.flowvoice.shared.transcription

import dev.rafaelbrauner.flowvoice.shared.dictation.AudioFormat

object WavEncoder {
    fun encode(pcm: ByteArray, format: AudioFormat): ByteArray {
        val headerSize = 44
        val wav = ByteArray(headerSize + pcm.size)
        writeAscii(wav, 0, "RIFF")
        writeIntLe(wav, 4, 36 + pcm.size)
        writeAscii(wav, 8, "WAVE")
        writeAscii(wav, 12, "fmt ")
        writeIntLe(wav, 16, 16)
        writeShortLe(wav, 20, 1)
        writeShortLe(wav, 22, format.channels)
        writeIntLe(wav, 24, format.sampleRate)
        writeIntLe(wav, 28, format.sampleRate * format.channels * format.bytesPerSample)
        writeShortLe(wav, 32, format.channels * format.bytesPerSample)
        writeShortLe(wav, 34, format.sampleBits)
        writeAscii(wav, 36, "data")
        writeIntLe(wav, 40, pcm.size)
        pcm.copyInto(wav, headerSize)
        return wav
    }

    private fun writeAscii(target: ByteArray, offset: Int, value: String) {
        val bytes = value.encodeToByteArray()
        bytes.copyInto(target, offset)
    }

    private fun writeIntLe(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value and 0xFF).toByte()
        target[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        target[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        target[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    private fun writeShortLe(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value and 0xFF).toByte()
        target[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }
}
