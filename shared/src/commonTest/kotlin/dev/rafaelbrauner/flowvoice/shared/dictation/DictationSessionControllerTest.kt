package dev.rafaelbrauner.flowvoice.shared.dictation

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DictationSessionControllerTest {
    @Test
    fun transitionsFromIdleThroughCapturingToFinalized() = runTest {
        val engine = FakeAudioCaptureEngine(listOf(pcmFrame(100L)))
        val controller = DictationSessionController(engine)

        assertEquals(DictationSessionState.Idle, controller.state.value)
        controller.start()
        assertEquals(DictationSessionState.Capturing, controller.state.value)
        controller.finalize()

        assertEquals(DictationSessionState.Finalized, controller.state.value)
        assertEquals(100L, controller.capturedDurationMs)
        assertEquals(1, engine.startCount)
        assertEquals(1, engine.stopCount)
    }

    @Test
    fun emitsWindowsWithContinuousTimelineIncludingFinalFlush() = runTest {
        val frames = listOf(pcmFrame(100L), pcmFrame(100L), pcmFrame(100L), pcmFrame(50L))
        val controller = DictationSessionController(FakeAudioCaptureEngine(frames), windowTargetDurationMs = 100L)
        val windows = mutableListOf<DictationWindow>()
        val collector = backgroundScope.launch { controller.windows.collect { windows += it } }
        runCurrent()

        controller.start()
        controller.finalize()
        runCurrent()

        assertEquals(4, windows.size)
        assertEquals(listOf(0, 1, 2, 3), windows.map { it.index })
        assertEquals(listOf(0L, 100L, 200L, 300L), windows.map { it.startedAtMs })
        assertEquals(listOf(100L, 200L, 300L, 350L), windows.map { it.finishedAtMs })
        assertContentEquals(pcmFrame(100L).pcm, windows[0].pcm)
        assertContentEquals(pcmFrame(50L).pcm, windows[3].pcm)
        assertEquals(350L, controller.capturedDurationMs)
        collector.cancel()
        runCurrent()
    }

    @Test
    fun cancelStopsEngineAndClearsBuffer() = runTest {
        val engine = FakeAudioCaptureEngine(listOf(pcmFrame(50L)))
        val controller = DictationSessionController(engine, windowTargetDurationMs = 100L)
        val windows = mutableListOf<DictationWindow>()
        val collector = backgroundScope.launch { controller.windows.collect { windows += it } }
        runCurrent()

        controller.start()
        controller.cancel()
        runCurrent()

        assertEquals(DictationSessionState.Cancelled, controller.state.value)
        assertEquals(1, engine.stopCount)
        assertEquals(0L, controller.capturedDurationMs)
        assertTrue(windows.isEmpty())
        collector.cancel()
        runCurrent()
    }

    @Test
    fun autoFinalizesAfterSilenceTimeoutAndDropsLaterFrames() = runTest {
        val frames = listOf(
            pcmFrame(50L),
            pcmFrame(50L),
            pcmFrame(50L, amplitude = 0),
            pcmFrame(50L, amplitude = 0),
            pcmFrame(50L)
        )
        val engine = FakeAudioCaptureEngine(frames)
        val controller = DictationSessionController(
            engine,
            windowTargetDurationMs = 1_000L,
            autoFinalizeOnSilence = true,
            silenceTimeoutMs = 100L
        )
        val windows = mutableListOf<DictationWindow>()
        val collector = backgroundScope.launch { controller.windows.collect { windows += it } }
        runCurrent()

        controller.start()
        runCurrent()

        assertEquals(DictationSessionState.Finalized, controller.state.value)
        assertEquals(200L, controller.capturedDurationMs)
        assertEquals(1, engine.stopCount)
        val window = windows.single()
        assertEquals(0, window.index)
        assertEquals(0L, window.startedAtMs)
        assertEquals(200L, window.finishedAtMs)
        collector.cancel()
        runCurrent()
    }

    @Test
    fun endpointingEmitsTheWindowAtThePauseBeforeFinalize() = runTest {
        val frames = listOf(0, 4_000, 4_000, 4_000, 4_000, 4_000, 4_000, 4_000, 4_000, 4_000, 4_000, 0, 0, 0, 0)
            .map { pcmFrame(100L, amplitude = it) }
        val controller = DictationSessionController(
            FakeAudioCaptureEngine(frames),
            windowPauseSearchBeforeMs = DictationWindowAggregator.SPEECH_PAUSE_SEARCH_BEFORE_MS,
            windowPauseSearchAfterMs = DictationWindowAggregator.SPEECH_PAUSE_SEARCH_AFTER_MS,
            windowEndpointing = SpeechEndpointing()
        )
        val windows = mutableListOf<DictationWindow>()
        val collector = backgroundScope.launch { controller.windows.collect { windows += it } }
        runCurrent()

        controller.start()
        runCurrent()

        val window = windows.single()
        assertEquals(WindowCut.Pause, window.cut)
        assertTrue(window.finishedAtMs in 1_100L..1_500L, "corte em ${window.finishedAtMs} ms")

        controller.finalize()
        runCurrent()
        assertEquals(listOf(WindowCut.Pause, WindowCut.Flush), windows.map { it.cut })
        assertEquals(1_500L, windows.last().finishedAtMs)
        collector.cancel()
        runCurrent()
    }

    @Test
    fun startFailureMovesSessionToError() = runTest {
        val error = AudioCaptureException("microphone unavailable")
        val engine = FakeAudioCaptureEngine(emptyList(), startError = error)
        val controller = DictationSessionController(engine)

        assertSuspendFailsWith<AudioCaptureException> { controller.start() }

        val state = controller.state.value
        assertTrue(state is DictationSessionState.Error, "expected Error but was $state")
        assertEquals("failed to start audio capture: microphone unavailable", (state as DictationSessionState.Error).message)
        assertEquals(1, engine.stopCount)
    }

    @Test
    fun startWhileCapturingThrows() = runTest {
        val engine = FakeAudioCaptureEngine(listOf(pcmFrame(100L)))
        val controller = DictationSessionController(engine)

        controller.start()
        assertSuspendFailsWith<InvalidDictationTransitionException> { controller.start() }

        assertEquals(DictationSessionState.Capturing, controller.state.value)
        assertEquals(1, engine.startCount)

        controller.finalize()
        assertEquals(DictationSessionState.Finalized, controller.state.value)
    }

    @Test
    fun sessionCanBeRestartedAfterFinalized() = runTest {
        val engine = FakeAudioCaptureEngine(listOf(pcmFrame(100L), pcmFrame(100L)))
        val controller = DictationSessionController(engine, windowTargetDurationMs = 100L)
        val windows = mutableListOf<DictationWindow>()
        val collector = backgroundScope.launch { controller.windows.collect { windows += it } }
        runCurrent()

        controller.start()
        controller.finalize()
        controller.start()
        controller.finalize()
        runCurrent()

        assertEquals(2, engine.startCount)
        assertEquals(2, engine.stopCount)
        assertEquals(DictationSessionState.Finalized, controller.state.value)
        assertEquals(listOf(0, 1, 0, 1), windows.map { it.index })
        collector.cancel()
        runCurrent()
    }

    @Test
    fun captureErrorAfterStartMovesSessionToError() = runTest {
        val engine = ErrorReportingAudioCaptureEngine()
        val controller = DictationSessionController(engine)

        controller.start()
        assertEquals(DictationSessionState.Capturing, controller.state.value)
        engine.reportError(AudioCaptureException("microphone removed"))

        val state = controller.state.value
        assertTrue(state is DictationSessionState.Error, "expected Error but was $state")
        assertEquals("audio capture failed: microphone removed", (state as DictationSessionState.Error).message)
        assertEquals(1, engine.stopCount)
    }

    private suspend inline fun <reified T : Throwable> assertSuspendFailsWith(block: suspend () -> Unit): T {
        try {
            block()
        } catch (error: Throwable) {
            assertTrue(error is T, "expected ${T::class.simpleName} but got $error")
            return error as T
        }
        fail("expected ${T::class.simpleName} to be thrown")
    }

    private fun pcmFrame(durationMs: Long, amplitude: Int = 4_000): AudioFrame =
        AudioFrame(pcmOf(durationMs, amplitude), AudioFormat.DEFAULT)

    private fun pcmOf(durationMs: Long, amplitude: Int): ByteArray {
        val sampleCount = (durationMs * 16).toInt()
        val pcm = ByteArray(sampleCount * 2)
        for (i in 0 until sampleCount) {
            pcm[i * 2] = (amplitude and 0xFF).toByte()
            pcm[i * 2 + 1] = ((amplitude ushr 8) and 0xFF).toByte()
        }
        return pcm
    }
}

private class FakeAudioCaptureEngine(
    private val frames: List<AudioFrame>,
    override val format: AudioFormat = AudioFormat.DEFAULT,
    private val startError: Throwable? = null
) : AudioCaptureEngine {
    var startCount = 0
        private set
    var stopCount = 0
        private set

    private var running = false

    override val isRunning: Boolean
        get() = running

    override suspend fun start(onFrame: suspend (AudioFrame) -> Unit) {
        startCount++
        startError?.let { throw it }
        running = true
        for (frame in frames) {
            if (!running) break
            onFrame(frame)
        }
        running = false
    }

    override fun stop() {
        stopCount++
        running = false
    }
}

private class ErrorReportingAudioCaptureEngine : AudioCaptureEngine {
    var stopCount = 0
        private set

    private var onError: (suspend (AudioCaptureException) -> Unit)? = null

    override val format: AudioFormat = AudioFormat.DEFAULT
    override val isRunning: Boolean
        get() = onError != null

    override suspend fun start(onFrame: suspend (AudioFrame) -> Unit) = start(onFrame, onError = {})

    override suspend fun start(
        onFrame: suspend (AudioFrame) -> Unit,
        onError: suspend (AudioCaptureException) -> Unit
    ) {
        this.onError = onError
    }

    suspend fun reportError(error: AudioCaptureException) {
        checkNotNull(onError) { "engine was not started" }.invoke(error)
    }

    override fun stop() {
        stopCount++
        onError = null
    }
}