package dev.rafaelbrauner.flowvoice.shared.dictation

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.lang.reflect.Proxy
import javax.sound.sampled.TargetDataLine
import kotlin.test.Test
import kotlin.test.assertIs

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
    }
}
