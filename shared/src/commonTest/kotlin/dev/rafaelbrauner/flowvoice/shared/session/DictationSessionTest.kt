package dev.rafaelbrauner.flowvoice.shared.session

import dev.rafaelbrauner.flowvoice.shared.audio.EnergyVad
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DictationSessionTest {

    @Test
    fun startStopDeliversAccumulatedAudio() = runTest {
        val router = ChannelStreamRouter()
        val session = DictationSession(
            router = router,
            vad = EnergyVad(frameSamples = 4),
            scope = TestScope(testScheduler)
        )
        session.start()
        assertEquals(SessionState.LISTENING, session.state.value)
        router.feed(floatArrayOf(0.9f, 0.9f, 0.9f, 0.9f))
        router.feed(floatArrayOf(0.9f, 0.9f, 0.9f, 0.9f))
        val finalized = session.stop()
        assertNotNull(finalized)
        assertEquals(8, finalized.audio.size)
        assertEquals(16000, finalized.sampleRateHz)
        assertEquals(SessionState.IDLE, session.state.value)
    }

    @Test
    fun stopWithoutStartReturnsNull() = runTest {
        val router = ChannelStreamRouter()
        val session = DictationSession(
            router = router,
            vad = EnergyVad(frameSamples = 4),
            scope = TestScope(testScheduler)
        )
        assertNull(session.stop())
    }

    @Test
    fun cancelResetsToIdleWithoutText() = runTest {
        val router = ChannelStreamRouter()
        val session = DictationSession(
            router = router,
            vad = EnergyVad(frameSamples = 4),
            scope = TestScope(testScheduler)
        )
        session.start()
        router.feed(floatArrayOf(0.9f, 0.9f, 0.9f, 0.9f))
        session.cancel()
        assertEquals(SessionState.IDLE, session.state.value)
        assertTrue(session.liveText.value.committed.isEmpty())
        assertTrue(session.liveText.value.tentative.isEmpty())
    }

    @Test
    fun emptySessionFinalizesToNull() = runTest {
        val router = ChannelStreamRouter()
        val session = DictationSession(
            router = router,
            vad = EnergyVad(frameSamples = 4),
            scope = TestScope(testScheduler)
        )
        session.start()
        assertNull(session.stop())
    }
}
