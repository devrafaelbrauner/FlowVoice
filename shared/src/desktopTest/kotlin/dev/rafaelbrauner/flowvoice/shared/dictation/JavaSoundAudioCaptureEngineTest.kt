package dev.rafaelbrauner.flowvoice.shared.dictation

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.TargetDataLine
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

    // Y3: a linha parada ainda guarda o fim da última palavra. Ele é lido (só o `available()`, sem
    // bloquear) e entra na sessão antes de a linha ser fechada.
    @Test
    fun audioStillBufferedWhenTheLineStopsEntersTheSession() = runBlocking {
        val stopped = AtomicBoolean(false)
        val reads = AtomicInteger(0)
        val tail = AtomicInteger(TAIL_BYTES)
        var open = false
        val line = Proxy.newProxyInstance(
            TargetDataLine::class.java.classLoader,
            arrayOf(TargetDataLine::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "open" -> { open = true; null }
                "close" -> { open = false; null }
                "stop" -> { stopped.set(true); null }
                "isOpen", "isActive", "isRunning" -> open
                "available" -> if (stopped.get()) tail.get() else 0
                "getBufferSize" -> LINE_BUFFER_BYTES
                "read" -> when {
                    stopped.get() -> minOf(args[2] as Int, tail.get()).also { tail.addAndGet(-it) }
                    reads.getAndIncrement() == 0 -> FRAME_BYTES
                    else -> {
                        while (!stopped.get()) Thread.sleep(POLL_MS)
                        0
                    }
                }
                "getFormat" -> AudioFormat.DEFAULT.toJavaSoundFormat()
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "TailTargetDataLine"
                else -> null
            }
        } as TargetDataLine
        val controller = DictationSessionController(JavaSoundAudioCaptureEngine(openLine = { line }))

        controller.start()
        withTimeout(TIMEOUT_MS) { while (controller.capturedDurationMs == 0L) delay(POLL_MS) }
        controller.finalize()

        assertEquals(DictationSessionState.Finalized, controller.state.value)
        assertEquals(150L, controller.capturedDurationMs)
    }

    private companion object {
        const val FRAME_BYTES = 3_200
        const val TAIL_BYTES = 1_600
        const val LINE_BUFFER_BYTES = 12_800
        const val TIMEOUT_MS = 5_000L
        const val POLL_MS = 5L
    }
}
