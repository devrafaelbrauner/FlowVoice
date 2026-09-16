package dev.rafaelbrauner.flowvoice.shared.dictation

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import javax.sound.sampled.TargetDataLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JavaSoundCaptureFailureTest {
    @Test
    fun readFailureAfterStartMovesSessionToError() = runBlocking {
        val engine = JavaSoundAudioCaptureEngine(openLine = { fakeLine { throw IllegalStateException("device removed") } })
        val controller = DictationSessionController(engine)

        controller.start()
        val state = withTimeout(TIMEOUT_MS) { controller.state.first { it !is DictationSessionState.Capturing } }

        assertIs<DictationSessionState.Error>(state)
        controller.cancel()
    }

    @Test
    fun stalledLineMovesSessionToErrorInsteadOfSpinning() = runBlocking {
        val engine = JavaSoundAudioCaptureEngine(openLine = { fakeLine { 0 } })
        val controller = DictationSessionController(engine)

        controller.start()
        val state = withTimeout(TIMEOUT_MS) { controller.state.first { it !is DictationSessionState.Capturing } }

        assertIs<DictationSessionState.Error>(state)
        controller.cancel()
    }

    // P154: no S26 uma leitura que voltou com zero byte encerrava o ditado antes de o usuário mandar.
    // Zero não é fim de fluxo: a sessão tem de continuar e o áudio que vier depois tem de entrar.
    @Test
    fun emptyReadsDoNotEndTheSession() = runBlocking {
        val reads = AtomicInteger(0)
        val engine = JavaSoundAudioCaptureEngine(
            openLine = { fakeLine { if (reads.getAndIncrement() < EMPTY_READS) 0 else FULL_READ_BYTES } },
            newReadPolicy = { CaptureReadPolicy(emptyToleranceMs = 300L, maxRestarts = 1) }
        )
        val controller = DictationSessionController(engine)

        controller.start()
        withTimeout(TIMEOUT_MS) { while (controller.capturedDurationMs == 0L) delay(POLL_MS) }

        assertEquals(DictationSessionState.Capturing, controller.state.value)
        assertTrue(reads.get() > EMPTY_READS, "as leituras vazias não foram sequer tentadas de novo")
        controller.cancel()
    }

    // Passada a tolerância, a última tentativa antes de desistir é reabrir a captura — e a sessão de
    // ditado continua viva do outro lado.
    @Test
    fun staleLineIsReopenedOnceAndTheSessionSurvives() = runBlocking {
        val opens = AtomicInteger(0)
        val engine = JavaSoundAudioCaptureEngine(
            openLine = {
                val generation = opens.incrementAndGet()
                fakeLine { if (generation == 1) 0 else FULL_READ_BYTES }
            },
            newReadPolicy = { CaptureReadPolicy(emptyToleranceMs = 50L, maxRestarts = 1) }
        )
        val controller = DictationSessionController(engine)

        controller.start()
        withTimeout(TIMEOUT_MS) { while (controller.capturedDurationMs == 0L) delay(POLL_MS) }

        assertEquals(DictationSessionState.Capturing, controller.state.value)
        assertEquals(2, opens.get(), "a captura devia ter sido reaberta uma vez")
        controller.cancel()
    }

    private fun fakeLine(read: () -> Int): TargetDataLine {
        var open = false
        return Proxy.newProxyInstance(
            TargetDataLine::class.java.classLoader,
            arrayOf(TargetDataLine::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "open" -> { open = true; null }
                "close" -> { open = false; null }
                "isOpen", "isActive", "isRunning" -> open
                "read" -> read()
                "getFormat" -> AudioFormat.DEFAULT.toJavaSoundFormat()
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "FakeTargetDataLine"
                else -> defaultValue(method.returnType)
            }
        } as TargetDataLine
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        else -> null
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val POLL_MS = 10L
        const val EMPTY_READS = 3
        const val FULL_READ_BYTES = 3_200
    }
}
