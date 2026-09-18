package dev.rafaelbrauner.flowvoice.desktop

import dev.rafaelbrauner.flowvoice.shared.dictation.DictationSessionController
import dev.rafaelbrauner.flowvoice.shared.dictation.DictationSessionState
import dev.rafaelbrauner.flowvoice.shared.insertion.TextInserter
import dev.rafaelbrauner.flowvoice.shared.preview.LivePreviewAssembler
import dev.rafaelbrauner.flowvoice.shared.transcription.InMemorySecretStore
import dev.rafaelbrauner.flowvoice.shared.prefs.InMemoryPreferencesStore
import dev.rafaelbrauner.flowvoice.shared.transcription.IncrementalTranscriptionController
import dev.rafaelbrauner.flowvoice.shared.transcription.KeyValidationResult
import dev.rafaelbrauner.flowvoice.shared.transcription.OpenRouterConfig
import dev.rafaelbrauner.flowvoice.shared.transcription.OpenRouterKeyValidator
import dev.rafaelbrauner.flowvoice.shared.transcription.SecretStore
import dev.rafaelbrauner.flowvoice.shared.transcription.SecretStoreUnavailableException
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionClient
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionSegment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DesktopDictationController : KoinComponent {
    private val session by inject<DictationSessionController>()
    private val secretStore by inject<SecretStore>()
    private val keyValidator by inject<OpenRouterKeyValidator>()
    private val transcriptionClient by inject<TranscriptionClient>()
    private val config by inject<OpenRouterConfig>()
    private val textInserter by inject<TextInserter>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessionOnlyKey = InMemorySecretStore()
    private val sessionOnlyPrefs = InMemoryPreferencesStore()
    private val transcription = IncrementalTranscriptionController(
        client = transcriptionClient,
        config = config,
        scope = scope,
        apiKeyProvider = ::currentKey,
        modelProvider = { dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionModels.selected(sessionOnlyPrefs, config) }
    )

    private val statusFlow = MutableStateFlow("Pronto.")
    private val keyConfiguredFlow = MutableStateFlow(false)
    private val finalTextFlow = MutableStateFlow("")
    private val finalizingFlow = MutableStateFlow(false)
    private val submittedWindows = MutableStateFlow(0)

    @Volatile
    private var sessionToken = 0

    val status: StateFlow<String> = statusFlow.asStateFlow()
    val keyConfigured: StateFlow<Boolean> = keyConfiguredFlow.asStateFlow()
    val finalText: StateFlow<String> = finalTextFlow.asStateFlow()
    val finalizing: StateFlow<Boolean> = finalizingFlow.asStateFlow()
    val sessionState: StateFlow<DictationSessionState>
        get() = session.state
    val segments: StateFlow<List<TranscriptionSegment>>
        get() = transcription.segments
    val insertionAvailable: Boolean
        get() = textInserter.isAvailable

    init {
        keyConfiguredFlow.value = currentKey() != null
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            session.windows.collect {
                transcription.submit(it)
                submittedWindows.value += 1
            }
        }
    }

    fun saveKey(rawKey: String) {
        scope.launch {
            statusFlow.value = "Validando chave…"
            statusFlow.value = when (keyValidator.validate(rawKey)) {
                KeyValidationResult.Valid -> storeKey(rawKey)
                KeyValidationResult.InvalidFormat -> "Formato de chave inválido."
                KeyValidationResult.Rejected -> "Chave recusada pela OpenRouter."
                KeyValidationResult.Unavailable -> "Não foi possível validar agora (rede ou serviço)."
            }
            keyConfiguredFlow.value = currentKey() != null
        }
    }

    fun startDictation() {
        if (finalizingFlow.value) return
        sessionToken++
        transcription.reset()
        submittedWindows.value = 0
        finalTextFlow.value = ""
        scope.launch {
            try {
                session.start()
                statusFlow.value = "Gravando (modelo ${config.model})…"
            } catch (error: Exception) {
                statusFlow.value = "Falha ao iniciar a captura: ${error.message ?: error::class.simpleName}"
            }
        }
    }

    fun finalizeDictation() {
        if (!finalizingFlow.compareAndSet(expect = false, update = true)) return
        val token = sessionToken
        scope.launch {
            try {
                session.finalize()
                publishIfCurrent(token) { statusFlow.value = "Transcrevendo o trecho final…" }
                submittedWindows.first { it >= session.emittedWindowCount }
                transcription.awaitIdle()
                val preview = LivePreviewAssembler.assemble(transcription.segments.value, sessionComplete = true)
                publishIfCurrent(token) {
                    finalTextFlow.value = preview.finalized
                    statusFlow.value = if (preview.finalized.isBlank()) {
                        "Nada transcrito."
                    } else {
                        "Texto final pronto (${preview.finalized.length} caracteres)."
                    }
                }
            } catch (error: Exception) {
                publishIfCurrent(token) {
                    statusFlow.value = "Falha ao finalizar: ${error.message ?: error::class.simpleName}"
                }
            } finally {
                publishIfCurrent(token) { finalizingFlow.value = false }
            }
        }
    }

    fun cancelDictation() {
        sessionToken++
        transcription.cancel()
        finalTextFlow.value = ""
        finalizingFlow.value = false
        scope.launch {
            try {
                session.cancel()
                statusFlow.value = "Ditado cancelado; nada será inserido."
            } catch (error: Exception) {
                statusFlow.value = "Falha ao cancelar: ${error.message ?: error::class.simpleName}"
            }
        }
    }

    fun insertIntoActiveApp(hideWindow: () -> Unit) {
        if (finalizingFlow.value) return
        val text = finalTextFlow.value.trim()
        if (text.isEmpty()) {
            statusFlow.value = "Sem texto final para inserir."
            return
        }
        if (!textInserter.isAvailable) {
            statusFlow.value = textInserter.insert(text).summary
            return
        }
        scope.launch {
            withContext(Dispatchers.Main) { hideWindow() }
            for (remaining in INSERT_COUNTDOWN_SECONDS downTo 1) {
                statusFlow.value = "Foque o campo de destino: inserindo em $remaining s…"
                delay(1_000)
            }
            statusFlow.value = textInserter.insert(text).summary
        }
    }

    fun close() {
        transcription.cancel()
        runBlocking {
            runCatching { session.cancel() }
                .onFailure { System.err.println("FlowVoiceDesktop: falha ao encerrar a captura (${it::class.simpleName})") }
        }
        scope.cancel()
    }

    private inline fun publishIfCurrent(token: Int, publish: () -> Unit) {
        if (token == sessionToken) publish()
    }

    private fun storeKey(rawKey: String): String = try {
        secretStore.writeOpenRouterKey(rawKey)
        "Chave validada e salva cifrada."
    } catch (_: SecretStoreUnavailableException) {
        sessionOnlyKey.writeOpenRouterKey(rawKey)
        "Chave validada. Sem cofre neste sistema: usada só nesta sessão, não foi salva."
    } catch (error: Exception) {
        "Falha ao salvar a chave (${error::class.simpleName})."
    }

    private fun currentKey(): String? =
        secretStore.readOpenRouterKey() ?: sessionOnlyKey.readOpenRouterKey()

    private companion object {
        private const val INSERT_COUNTDOWN_SECONDS = 3
    }
}
