package dev.rafaelbrauner.flowvoice.shared.transcription

import dev.rafaelbrauner.flowvoice.shared.dictation.DictationWindow
import dev.rafaelbrauner.flowvoice.shared.dictation.EmptyAudioMemory
import dev.rafaelbrauner.flowvoice.shared.dictation.SilentWindow
import dev.rafaelbrauner.flowvoice.shared.dictation.WindowSpeechGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class IncrementalTranscriptionController(
    private val client: TranscriptionClient,
    private val config: OpenRouterConfig,
    private val scope: CoroutineScope,
    private val apiKeyProvider: () -> String?,
    private val modelProvider: () -> String = { config.model },
    private val eventLog: TranscriptionEventLog = TranscriptionEventLog.NoOp,
    private val textLog: TranscriptionEventLog = TranscriptionEventLog.NoOp
) : SessionTranscription {
    private val processMutex = Mutex()
    private val provisional = MutableStateFlow("")
    private val segmentState = MutableStateFlow<List<TranscriptionSegment>>(emptyList())
    private val budgetState = MutableStateFlow(false)
    private val fatalState = MutableStateFlow<TranscriptionError?>(null)

    // Estado da sessão, lido e escrito só sob `lock` (P53): no desktop `submit` vem de
    // `Dispatchers.Default` e `reset`/`cancel` da thread da interface. `generation` muda a cada
    // reset, e a janela de uma sessão anterior que termina depois dele não escreve mais nada.
    private val lock = Any()
    private var generation = 0
    private val jobs = mutableListOf<Job>()
    private var cancelled = false
    private var requestCount = 0
    // O que já se provou vazio nesta sessão, para não pagar duas vezes pelo mesmo nível (P153).
    private val emptyAudio = EmptyAudioMemory()
    private var sessionApiKey: String? = null

    val provisionalText: StateFlow<String> = provisional.asStateFlow()
    override val segments: StateFlow<List<TranscriptionSegment>> = segmentState.asStateFlow()
    val budgetExhausted: StateFlow<Boolean> = budgetState.asStateFlow()
    val fatalError: StateFlow<TranscriptionError?> = fatalState.asStateFlow()

    override fun submit(window: DictationWindow) {
        val job = locked(lock) {
            if (cancelled) return
            val withinBudget = requestCount < config.maxRequestsPerSession
            if (withinBudget) requestCount++
            val session = generation
            // Só começa depois de entrar em `jobs`: um reset no meio do caminho já o encontra lá.
            val job = scope.launch(start = CoroutineStart.LAZY) {
                if (!withinBudget) {
                    rejectOverBudget(window, session)
                } else {
                    processMutex.withLock {
                        if (isLive(session)) {
                            process(window, session)
                        }
                    }
                }
            }
            jobs += job
            if (!withinBudget) budgetState.value = true
            job
        }
        job.start()
    }

    override fun cancel() {
        val active = locked(lock) {
            cancelled = true
            jobs.toList().also { jobs.clear() }
        }
        active.forEach { it.cancel() }
    }

    // A chave vale para a sessão inteira: uma falha passageira do cofre no meio do ditado não pode
    // virar "chave ausente" e encerrar a sessão (P134).
    fun reset(apiKey: String? = null) {
        val previous = locked(lock) {
            generation++
            val previous = jobs.toList()
            jobs.clear()
            cancelled = false
            requestCount = 0
            sessionApiKey = apiKey?.takeIf { it.isNotBlank() }
            emptyAudio.clear()
            budgetState.value = false
            fatalState.value = null
            provisional.value = ""
            segmentState.value = emptyList()
            previous
        }
        previous.forEach { it.cancel() }
    }

    override suspend fun awaitIdle() {
        locked(lock) { jobs.toList() }.forEach { it.join() }
    }

    private fun isLive(session: Int): Boolean = locked(lock) { session == generation && !cancelled }

    private fun rejectOverBudget(window: DictationWindow, session: Int) {
        upsert(
            TranscriptionSegment(
                windowIndex = window.index,
                status = TranscriptionSegment.Status.Failed,
                errorKind = TranscriptionError.SessionBudgetExceeded().kind
            ),
            session
        )
        eventLog.log(
            "transcription_budget",
            mapOf(
                "window" to window.index.toString(),
                "durationMs" to window.durationMs.toString(),
                "model" to modelProvider()
            )
        )
    }

    private suspend fun process(window: DictationWindow, session: Int) {
        if (SilentWindow.detect(window)) {
            skipSilent(window, "digital", session)
            return
        }
        // Sem fala medida bastante, a janela não vale uma requisição: com o piso de ruído alto o
        // silêncio do fim do ditado chegava aqui como janela de teto e voltava vazia (P151).
        WindowSpeechGate.skipReason(window)?.let { reason ->
            skipSilent(window, reason, session)
            return
        }
        // Áudio que não é mais alto do que um que já voltou vazio nesta sessão não se paga de novo
        // (P153). Quem fala baixo escapa da trava: o nível que já rendeu texto nunca é silêncio.
        val alreadyEmpty = locked(lock) {
            if (session != generation) return
            emptyAudio.skips(window.peakLevel)
        }
        if (alreadyEmpty) {
            skipSilent(window, WindowSpeechGate.REASON_LEVEL_ALREADY_EMPTY, session)
            return
        }
        upsert(
            TranscriptionSegment(
                windowIndex = window.index,
                status = TranscriptionSegment.Status.Transcribing
            ),
            session
        )
        fatalState.value?.let { fatal ->
            fail(window, fatal, session)
            return
        }
        val apiKey = sessionKey(session)
        if (apiKey == null) {
            failFatally(window, TranscriptionError.InvalidKey(), session)
            return
        }
        try {
            val result = client.transcribe(window, apiKey, modelProvider())
            if (result.text.isNotBlank()) {
                locked(lock) { if (session == generation) emptyAudio.rememberSpoken(window.peakLevel, window.noiseFloor) }
                acceptText(window, result.text, session)
            } else {
                val silence = locked(lock) {
                    if (session != generation) return
                    emptyAudio.rememberEmpty(window.peakLevel, window.noiseFloor)
                }
                if (silence) acceptText(window, result.text, session) else failEmptyVoice(window, session)
            }
            textLog.log("transcription_window_text", mapOf("window" to window.index.toString(), "text" to result.text))
        } catch (error: CancellationException) {
            throw error
        } catch (error: TranscriptionError.InvalidKey) {
            failFatally(window, error, session)
        } catch (error: TranscriptionError) {
            fail(window, error, session)
        } catch (error: Throwable) {
            fail(window, TranscriptionErrorClassifier.fromThrowable(error), session)
        }
    }

    // O cofre é lido fora da trava: no desktop a leitura decifra a chave (DPAPI).
    private fun sessionKey(session: Int): String? {
        locked(lock) { sessionApiKey }?.let { return it }
        val key = apiKeyProvider()?.takeIf { it.isNotBlank() } ?: return null
        locked(lock) { if (session == generation) sessionApiKey = key }
        return key
    }

    private fun acceptText(window: DictationWindow, text: String, session: Int) {
        upsert(
            TranscriptionSegment(
                windowIndex = window.index,
                status = TranscriptionSegment.Status.Ok,
                text = text,
                contextDurationMs = window.contextDurationMs
            ),
            session,
            rebuildProvisional = true
        )
    }

    // Voz alta demais para ser silêncio voltou sem texto (R1): pode ser uma tosse, pode ser fala
    // perdida pelo modelo. O trecho fica como falho, para o aviso de texto incompleto aparecer em vez
    // de um "ok" mudo, e a memória da P153 não aprende nada com ele.
    private fun failEmptyVoice(window: DictationWindow, session: Int) {
        upsert(
            TranscriptionSegment(
                windowIndex = window.index,
                status = TranscriptionSegment.Status.Failed,
                errorKind = EMPTY_VOICE_KIND,
                contextDurationMs = window.contextDurationMs
            ),
            session
        )
        eventLog.log(
            "transcription_empty_voice",
            buildMap {
                put("window", window.index.toString())
                put("durationMs", window.durationMs.toString())
                put("model", modelProvider())
                window.peakLevel?.let { put("peak", it.toString()) }
                window.noiseFloor?.let { put("noiseFloor", it.toString()) }
                window.voicedMs?.let { put("voicedMs", it.toString()) }
            }
        )
    }

    // A janela silenciosa já ocupou uma vaga do teto na submissão: sem isso, uma captura muda
    // nunca atingiria o teto e o microfone ficaria aberto (P107).
    private fun skipSilent(window: DictationWindow, reason: String, session: Int) {
        upsert(TranscriptionSegment(windowIndex = window.index, status = TranscriptionSegment.Status.Ok), session)
        eventLog.log(
            "transcription_silent_window",
            buildMap {
                put("window", window.index.toString())
                put("durationMs", window.durationMs.toString())
                put("reason", reason)
                put("cut", window.cut.name.lowercase())
                // Quanto de fala foi medido na janela barrada: é o número que diz, no aparelho, se o
                // corte da P151 pegou silêncio mesmo ou se encostou em fala baixa.
                window.voicedMs?.let { put("voicedMs", it.toString()) }
            }
        )
    }

    private fun failFatally(window: DictationWindow, error: TranscriptionError, session: Int) {
        fail(window, error, session)
        locked(lock) { if (session == generation) fatalState.value = error }
    }

    private fun fail(window: DictationWindow, error: TranscriptionError, session: Int) {
        upsert(
            TranscriptionSegment(
                windowIndex = window.index,
                status = TranscriptionSegment.Status.Failed,
                errorKind = when (error) {
                    is TranscriptionError.InvalidResponse -> error.message?.take(48) ?: error.kind
                    is TranscriptionError.Server -> "server_${error.statusCode}"
                    else -> error.kind
                }
            ),
            session
        )
        eventLog.log(
            "transcription_window_failed",
            mapOf(
                "window" to window.index.toString(),
                "durationMs" to window.durationMs.toString(),
                "model" to modelProvider(),
                "kind" to error.kind
            )
        )
    }

    private fun upsert(segment: TranscriptionSegment, session: Int, rebuildProvisional: Boolean = false) {
        locked(lock) {
            if (session != generation) return
            val current = segmentState.value.toMutableList()
            val index = current.indexOfFirst { it.windowIndex == segment.windowIndex }
            if (index >= 0) {
                current[index] = segment
            } else {
                current += segment
                current.sortBy { it.windowIndex }
            }
            segmentState.value = current
            if (rebuildProvisional) {
                provisional.value = current
                    .filter { it.status == TranscriptionSegment.Status.Ok && it.text.isNotBlank() }
                    .joinToString(" ") { it.text.trim() }
            }
        }
    }

    companion object {
        // Trecho com voz alta que voltou sem texto (R1).
        const val EMPTY_VOICE_KIND = "empty_voice"
    }
}
