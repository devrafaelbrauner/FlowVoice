package dev.rafaelbrauner.flowvoice.desktop

import dev.rafaelbrauner.flowvoice.shared.dictation.AudioCaptureEngine
import dev.rafaelbrauner.flowvoice.shared.dictation.AudioFormat
import dev.rafaelbrauner.flowvoice.shared.dictation.AudioFrame
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationSessionController
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationSessionState
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow
import dev.rafaelbrauner.flowvoice.shared.insertion.TextInserter
import dev.rafaelbrauner.flowvoice.shared.insertion.TextInsertionResult
import dev.rafaelbrauner.flowvoice.shared.transcription.InMemorySecretStore
import dev.rafaelbrauner.flowvoice.shared.transcription.OpenRouterConfig
import dev.rafaelbrauner.flowvoice.shared.transcription.SecretStore
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionClient
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopDictationControllerTest {
    private val engine = OneFrameAudioCaptureEngine()
    private val client = GatedTranscriptionClient()
    private lateinit var controller: DesktopDictationController

    @BeforeTest
    fun setUp() {
        startKoin {
            modules(
                module {
                    single<AudioCaptureEngine> { engine }
                    factory { DictationSessionController(get()) }
                    single<SecretStore> { InMemorySecretStore().apply { writeOpenRouterKey(TEST_KEY) } }
                    single<TranscriptionClient> { client }
                    single { OpenRouterConfig() }
                    single<TextInserter> { NoOpTextInserter }
                }
            )
        }
        controller = DesktopDictationController()
    }

    @AfterTest
    fun tearDown() {
        client.release("")
        controller.close()
        stopKoin()
    }

    @Test
    fun startDuringFinalTranscriptionKeepsThePreviousDictation() = runBlocking {
        recordAndFinalize()

        controller.startDictation()
        client.release("texto da primeira sessão")

        val finalText = withTimeout(TIMEOUT_MS) { controller.finalText.first { it.isNotBlank() } }
        delay(SETTLE_MS)
        assertEquals("texto da primeira sessão", finalText)
        assertEquals(1, engine.startCount)
        assertEquals(DictationSessionState.Finalized, controller.sessionState.value)
    }

    @Test
    fun cancelDuringFinalTranscriptionDiscardsItsResult() = runBlocking {
        recordAndFinalize()

        controller.cancelDictation()
        withTimeout(TIMEOUT_MS) { controller.status.first { it == CANCELLED_STATUS } }
        client.release("não deveria aparecer")
        delay(SETTLE_MS)

        assertEquals("", controller.finalText.value)
        assertEquals(CANCELLED_STATUS, controller.status.value)
    }

    @Test
    fun finalizingIsExposedUntilTheFinalTranscriptionEnds() = runBlocking {
        recordAndFinalize()
        assertEquals(true, controller.finalizing.value)

        client.release("texto")

        withTimeout(TIMEOUT_MS) { controller.finalizing.first { !it } }
        assertEquals("texto", controller.finalText.value)
    }

    private suspend fun recordAndFinalize() {
        controller.startDictation()
        withTimeout(TIMEOUT_MS) { controller.sessionState.first { it == DictationSessionState.Capturing } }
        controller.finalizeDictation()
        withTimeout(TIMEOUT_MS) { client.started.await() }
    }

    private companion object {
        const val TEST_KEY = "sk-or-v1-testkey123456"
        const val CANCELLED_STATUS = "Ditado cancelado; nada será inserido."
        const val TIMEOUT_MS = 5_000L
        const val SETTLE_MS = 300L
    }
}

private class OneFrameAudioCaptureEngine : AudioCaptureEngine {
    var startCount = 0
        private set

    override val format: AudioFormat = AudioFormat.DEFAULT
    override val isRunning: Boolean = false

    override suspend fun start(onFrame: suspend (AudioFrame) -> Unit) {
        startCount++
        onFrame(AudioFrame(ByteArray(FRAME_BYTES), format))
    }

    override fun stop() = Unit

    private companion object {
        const val FRAME_BYTES = 3_200
    }
}

private class GatedTranscriptionClient : TranscriptionClient {
    val started = CompletableDeferred<Unit>()
    private val gate = CompletableDeferred<String>()

    fun release(text: String) {
        gate.complete(text)
    }

    override suspend fun transcribe(window: DictationWindow, apiKey: String, model: String?): TranscriptionResult {
        started.complete(Unit)
        val text = withContext(NonCancellable) { gate.await() }
        return TranscriptionResult(text = text, model = model ?: "fake")
    }

    override fun cancel() = Unit
}

private object NoOpTextInserter : TextInserter {
    override val isAvailable: Boolean = false

    override fun insert(text: String): TextInsertionResult = TextInsertionResult(false, "teste", "sem inserção")
}
