package dev.rafaelbrauner.flowvoice.shared.dictation

import kotlinx.coroutines.test.runTest
import javax.sound.sampled.LineUnavailableException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import javax.sound.sampled.AudioFormat as JavaSoundFormat

class JavaSoundAudioCaptureEngineTest {
    @Test
    fun javaSoundFormatIsSignedLittleEndian16KhzMono() {
        val javaFormat = AudioFormat.DEFAULT.toJavaSoundFormat()

        assertEquals(16_000f, javaFormat.sampleRate)
        assertEquals(16, javaFormat.sampleSizeInBits)
        assertEquals(1, javaFormat.channels)
        assertEquals(2, javaFormat.frameSize)
        assertEquals(JavaSoundFormat.Encoding.PCM_SIGNED, javaFormat.encoding)
        assertFalse(javaFormat.isBigEndian)
    }

    @Test
    fun rejectsNon16BitFormat() {
        assertFailsWith<IllegalArgumentException> {
            JavaSoundAudioCaptureEngine(AudioFormat(sampleBits = 8))
        }
    }

    @Test
    fun unavailableLineBecomesCaptureExceptionAndAllowsRetry() = runTest {
        val engine = JavaSoundAudioCaptureEngine(openLine = { throw LineUnavailableException("busy") })

        repeat(2) {
            val error = assertFailsWith<AudioCaptureException> { engine.start { } }
            assertIs<LineUnavailableException>(error.cause)
        }
        assertFalse(engine.isRunning)
    }
}
