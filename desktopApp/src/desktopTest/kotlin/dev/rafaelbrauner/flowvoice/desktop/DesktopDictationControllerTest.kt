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
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionError
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopDictationControllerTest {
    private val engine = PushAudioCaptureEngine()
    private val client = GatedTranscriptionClient()
    private var config = OpenRouterConfig()
    private val controller by lazy { DesktopDictationController() }

    @BeforeTest
    fun setUp() {
        startKoin {
            modules(
                module {
                    single<AudioCaptureEngine> { engine }
                    factory { DictationSessionController(get()) }
                    single<SecretStore> { InMemorySecretStore().apply { writeOpenRouterKey(TEST_KEY) } }
                    single<TranscriptionClient> { client }
                    single { config }
                    single<TextInserter> { NoOpTextInserter }
                }
            )
        }
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

    // Y6: no desktop, o trecho que falhou sumia do texto final e o status dizia "Texto final pronto".
    // O aviso tem de dizer qual trecho falhou antes de o usuário inserir.
    @Test
    fun aFailedWindowIsReportedInsteadOfAFinishedText() = runBlocking {
        client.open(failures = mapOf(0 to TranscriptionError.Server(500)), text = "Nega febre.")
        startRecording()
        engine.push(durationMs = 4_000L)
        engine.push(durationMs = 500L)
        controller.finalizeDictation()

        withTimeout(TIMEOUT_MS) { controller.finalizing.first { !it } }
        assertEquals("Nega febre.", controller.finalText.value)
        assertEquals("Trecho 1 de 2 falhou (erro do servidor): texto incompleto", controller.warning.value)
        assertFalse(controller.status.value.startsWith("Texto final pronto"), controller.status.value)
    }

    // Y6: ao estourar o teto de requisições, o Android encerra a gravação (P92); o desktop seguia
    // gravando e marcando cada trecho novo como falho.
    @Test
    fun theRequestBudgetStopsTheCapture() = runBlocking {
        config = OpenRouterConfig(maxRequestsPerSession = 1)
        client.open(text = "Paciente estável.")
        startRecording()
        engine.push(durationMs = 4_000L)
        engine.push(durationMs = 4_000L)

        withTimeout(TIMEOUT_MS) { controller.sessionState.first { it == DictationSessionState.Finalized } }
        withTimeout(TIMEOUT_MS) { controller.finalizing.first { !it } }
        assertEquals("Paciente estável.", controller.finalText.value)
        assertTrue(controller.warning.value.orEmpty().contains("teto de requisições"), controller.warning.value)
    }

    // Y6: chave recusada falha todo trecho; gravar mais só acumula falhas (P111 no Android).
    @Test
    fun anInvalidKeyStopsTheCapture() = runBlocking {
        client.open(failures = mapOf(0 to TranscriptionError.InvalidKey()))
        startRecording()
        engine.push(durationMs = 4_000L)

        withTimeout(TIMEOUT_MS) { controller.sessionState.first { it == DictationSessionState.Finalized } }
        withTimeout(TIMEOUT_MS) { controller.finalizing.first { !it } }
        assertEquals("", controller.finalText.value)
        assertTrue(controller.status.value.contains("chave OpenRouter ausente ou inválida"), controller.status.value)
    }

    private suspend fun startRecording() {
        controller.startDictation()
        withTimeout(TIMEOUT_MS) { controller.sessionState.first { it == DictationSessionState.Capturing } }
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

// Entrega um quadro de 100 ms ao começar e depois o que o teste empurrar, como a captura real.
private class PushAudioCaptureEngine : AudioCaptureEngine {
    var startCount = 0
        private set
    private var onFrame: (suspend (AudioFrame) -> Unit)? = null

    override val format: AudioFormat = AudioFormat.DEFAULT
    override val isRunning: Boolean = false

    override suspend fun start(onFrame: suspend (AudioFrame) -> Unit) {
        startCount++
        this.onFrame = onFrame
        onFrame(voicedFrame(FRAME_MS))
    }

    suspend fun push(durationMs: Long) {
        onFrame?.invoke(voicedFrame(durationMs))
    }

    override fun stop() = Unit

    private fun voicedFrame(durationMs: Long): AudioFrame {
        val bytes = (durationMs * format.sampleRate / 1_000L).toInt() * format.bytesPerFrame
        return AudioFrame(ByteArray(bytes) { VOICED_PATTERN[it % VOICED_PATTERN.size] }, format)
    }

    private companion object {
        const val FRAME_MS = 100L

        // Amostras de ±1000: silêncio digital (ByteArray zerado) não vai à transcrição.
        val VOICED_PATTERN = byteArrayOf(0xE8.toByte(), 0x03, 0x18, 0xFC.toByte())
    }
}

private class GatedTranscriptionClient : TranscriptionClient {
    val started = CompletableDeferred<Unit>()
    private val gate = CompletableDeferred<String>()
    @Volatile
    private var failures: Map<Int, TranscriptionError> = emptyMap()

    fun release(text: String) {
        gate.complete(text)
    }

    // Sem portão: cada janela responde na hora, com `text` ou com a falha marcada para ela.
    fun open(failures: Map<Int, TranscriptionError> = emptyMap(), text: String = "") {
        this.failures = failures
        gate.complete(text)
    }

    override suspend fun transcribe(window: DictationWindow, apiKey: String, model: String?): TranscriptionResult {
        started.complete(Unit)
        val text = withContext(NonCancellable) { gate.await() }
        failures[window.index]?.let { throw it }
        return TranscriptionResult(text = text, model = model ?: "fake")
    }
}

private object NoOpTextInserter : TextInserter {
    override val isAvailable: Boolean = false

    override fun insert(text: String): TextInsertionResult = TextInsertionResult(false, "teste", "sem inserção")
}
