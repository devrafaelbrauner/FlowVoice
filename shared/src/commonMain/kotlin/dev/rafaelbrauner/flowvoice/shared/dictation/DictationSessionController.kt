package dev.rafaelbrauner.flowvoice.shared.dictation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.sqrt

// Quem ouve o áudio da sessão na ordem exata da captura (motor no aparelho): cada frame antes das
// janelas que ele fecha, e cada janela no instante do corte. Chamado sob a trava da sessão, na
// thread da captura: não pode bloquear.
interface CaptureTap {
    fun onFrame(frame: AudioFrame)
    fun onWindow(window: DictationWindow)
}

class DictationSessionController(
    private val engine: AudioCaptureEngine,
    windowTargetDurationMs: Long = DictationWindowAggregator.DEFAULT_TARGET_DURATION_MS,
    private val autoFinalizeOnSilence: Boolean = false,
    private val silenceThreshold: Float = DEFAULT_SILENCE_THRESHOLD,
    private val silenceTimeoutMs: Long = DEFAULT_SILENCE_TIMEOUT_MS,
    windowPauseSearchBeforeMs: Long = 0L,
    windowPauseSearchAfterMs: Long = 0L,
    val windowEndpointing: SpeechEndpointing? = null,
    val windowContextDurationMs: Long = 0L
) {
    private val session = DictationSession()
    private val windowAggregator = DictationWindowAggregator(
        windowTargetDurationMs,
        engine.format,
        windowPauseSearchBeforeMs,
        windowPauseSearchAfterMs,
        windowEndpointing,
        windowContextDurationMs
    )
    private val sessionMutex = Mutex()
    private val observedState = MutableStateFlow(session.state)
    private val windowEvents = MutableSharedFlow<DictationWindow>(extraBufferCapacity = Channel.UNLIMITED)
    private var lastVoiceAtMs = 0L
    private var tap: CaptureTap? = null

    val state: StateFlow<DictationSessionState> = observedState.asStateFlow()
    val windows: Flow<DictationWindow> = windowEvents.asSharedFlow()

    var capturedDurationMs: Long = 0L
        private set

    var emittedWindowCount: Int = 0
        private set

    suspend fun start(tap: CaptureTap? = null) {
        sessionMutex.withLock {
            when (session.state) {
                is DictationSessionState.Idle -> session.start()
                is DictationSessionState.Capturing,
                is DictationSessionState.Finalizing -> throw InvalidDictationTransitionException(
                    session.state,
                    DictationSessionState.Capturing
                )
                is DictationSessionState.Finalized,
                is DictationSessionState.Cancelled,
                is DictationSessionState.Error -> {
                    session.reset()
                    session.start()
                }
            }
            windowAggregator.clear()
            capturedDurationMs = 0L
            lastVoiceAtMs = 0L
            emittedWindowCount = 0
            this.tap = tap
            observedState.value = session.state
        }
        try {
            engine.start(
                onFrame = { frame -> handleFrame(frame) },
                onError = { error -> failSession("audio capture failed: ${error.message}", keepAudio = true) }
            )
        } catch (error: Throwable) {
            failSession("failed to start audio capture: ${error.message}", keepAudio = false)
            throw error
        }
    }

    suspend fun finalize() {
        val enteredFinalization = sessionMutex.withLock {
            when (session.state) {
                is DictationSessionState.Capturing -> {
                    session.requestFinalization()
                    observedState.value = session.state
                    true
                }
                is DictationSessionState.Finalizing -> true
                else -> false
            }
        }
        if (!enteredFinalization) return
        engine.stop()
        completeFinalization()
    }

    suspend fun cancel() {
        val cancelled = sessionMutex.withLock {
            when (session.state) {
                is DictationSessionState.Capturing,
                is DictationSessionState.Finalizing -> {
                    session.cancel()
                    observedState.value = session.state
                    true
                }
                else -> false
            }
        }
        if (!cancelled) return
        engine.stop()
        sessionMutex.withLock {
            windowAggregator.clear()
            capturedDurationMs = 0L
            lastVoiceAtMs = 0L
        }
    }

    // O frame que o `stop()` destrava ainda é fala do usuário (Y3): enquanto a sessão está em
    // Finalizing — do toque de parar até o `engine.stop()` voltar —, ele entra na última janela.
    private suspend fun handleFrame(frame: AudioFrame) {
        var failureMessage: String? = null
        var silenceTimedOut = false
        sessionMutex.withLock {
            val capturing = session.state is DictationSessionState.Capturing
            if (capturing || session.state is DictationSessionState.Finalizing) {
                try {
                    val windows = windowAggregator.onFrame(frame)
                    tap?.onFrame(frame)
                    windows.forEach { emitWindow(it) }
                    capturedDurationMs += frame.durationMs
                    // A cauda não conta silêncio: a sessão já está encerrando.
                    if (capturing) silenceTimedOut = updateSilenceState(frame)
                } catch (error: IllegalArgumentException) {
                    failureMessage = "frame does not match capture format: ${error.message}"
                }
            }
        }
        val failure = failureMessage
        when {
            failure != null -> failSession(failure, keepAudio = true)
            silenceTimedOut -> finalize()
        }
    }

    // Falha no meio da captura (R3): o áudio que já estava no agregador é fala do usuário e sai
    // como última janela antes do erro, para quem ouve a sessão entregar o que foi dito até ali.
    // Na falha ao abrir o microfone não há o que guardar. A duração captada fica: é ela que diz até
    // onde o texto vai.
    private suspend fun failSession(message: String, keepAudio: Boolean) {
        sessionMutex.withLock {
            if (session.state.isTerminal) {
                return
            }
            if (keepAudio) windowAggregator.flush()?.let { emitWindow(it) }
            session.fail(message)
            windowAggregator.clear()
            lastVoiceAtMs = 0L
            observedState.value = session.state
        }
        engine.stop()
    }

    private suspend fun completeFinalization() {
        sessionMutex.withLock {
            if (session.state !is DictationSessionState.Finalizing) {
                return
            }
            session.completeFinalization()
            windowAggregator.flush()?.let { emitWindow(it) }
            observedState.value = session.state
        }
    }

    private suspend fun emitWindow(window: DictationWindow) {
        tap?.onWindow(window)
        emittedWindowCount++
        windowEvents.emit(window)
    }

    private fun updateSilenceState(frame: AudioFrame): Boolean {
        if (!autoFinalizeOnSilence) return false
        if (frame.rms() >= silenceThreshold) {
            lastVoiceAtMs = capturedDurationMs
        }
        return capturedDurationMs - lastVoiceAtMs >= silenceTimeoutMs
    }

    private fun AudioFrame.rms(): Float {
        if (format.bytesPerSample != 2) return 0f
        var sumSquares = 0.0
        var sampleCount = 0
        var index = 0
        while (index + 1 < pcm.size) {
            val low = pcm[index].toInt() and 0xFF
            val high = (pcm[index + 1].toInt() and 0xFF) shl 8
            val sample = (low or high).toShort().toInt()
            sumSquares += sample.toDouble() * sample.toDouble()
            sampleCount++
            index += 2
        }
        if (sampleCount == 0) return 0f
        return sqrt(sumSquares / sampleCount).toFloat()
    }

    companion object {
        const val DEFAULT_SILENCE_THRESHOLD = 300f
        const val DEFAULT_SILENCE_TIMEOUT_MS = 800L
    }
}