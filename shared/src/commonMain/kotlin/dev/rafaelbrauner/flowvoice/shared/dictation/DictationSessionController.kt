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

class DictationSessionController(
    private val engine: AudioCaptureEngine,
    windowTargetDurationMs: Long = DictationWindowAggregator.DEFAULT_TARGET_DURATION_MS,
    private val autoFinalizeOnSilence: Boolean = false,
    private val silenceThreshold: Float = DEFAULT_SILENCE_THRESHOLD,
    private val silenceTimeoutMs: Long = DEFAULT_SILENCE_TIMEOUT_MS,
    windowPauseSearchMs: Long = 0L
) {
    private val session = DictationSession()
    private val windowAggregator = DictationWindowAggregator(windowTargetDurationMs, engine.format, windowPauseSearchMs)
    private val sessionMutex = Mutex()
    private val observedState = MutableStateFlow(session.state)
    private val windowEvents = MutableSharedFlow<DictationWindow>(extraBufferCapacity = Channel.UNLIMITED)
    private var lastVoiceAtMs = 0L

    val state: StateFlow<DictationSessionState> = observedState.asStateFlow()
    val windows: Flow<DictationWindow> = windowEvents.asSharedFlow()

    var capturedDurationMs: Long = 0L
        private set

    var emittedWindowCount: Int = 0
        private set

    suspend fun start() {
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
            observedState.value = session.state
        }
        try {
            engine.start(
                onFrame = { frame -> handleFrame(frame) },
                onError = { error -> failSession("audio capture failed: ${error.message}") }
            )
        } catch (error: Throwable) {
            failSession("failed to start audio capture: ${error.message}")
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

    private suspend fun handleFrame(frame: AudioFrame) {
        var failureMessage: String? = null
        var silenceTimedOut = false
        sessionMutex.withLock {
            if (session.state is DictationSessionState.Capturing) {
                try {
                    windowAggregator.onFrame(frame).forEach { emitWindow(it) }
                    capturedDurationMs += frame.durationMs
                    silenceTimedOut = updateSilenceState(frame)
                } catch (error: IllegalArgumentException) {
                    failureMessage = "frame does not match capture format: ${error.message}"
                }
            }
        }
        val failure = failureMessage
        when {
            failure != null -> failSession(failure)
            silenceTimedOut -> finalize()
        }
    }

    private suspend fun failSession(message: String) {
        sessionMutex.withLock {
            if (session.state.isTerminal) {
                return
            }
            session.fail(message)
            windowAggregator.clear()
            capturedDurationMs = 0L
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