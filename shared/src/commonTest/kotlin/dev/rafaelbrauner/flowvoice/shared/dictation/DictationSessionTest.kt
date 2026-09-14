package dev.rafaelbrauner.flowvoice.shared.dictation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DictationSessionTest {
    @Test
    fun startsIdle() {
        val session = DictationSession()

        assertEquals(DictationSessionState.Idle, session.state)
        assertFalse(session.state.isActive)
        assertFalse(session.state.isTerminal)
    }

    @Test
    fun followsSuccessfulFinalization() {
        val session = DictationSession()

        session.start()
        assertEquals(DictationSessionState.Capturing, session.state)
        assertTrue(session.state.isActive)
        assertFalse(session.state.isTerminal)

        session.requestFinalization()
        assertEquals(DictationSessionState.Finalizing, session.state)
        assertTrue(session.state.isActive)

        session.completeFinalization()
        assertEquals(DictationSessionState.Finalized, session.state)
        assertFalse(session.state.isActive)
        assertTrue(session.state.isTerminal)

        session.reset()
        assertEquals(DictationSessionState.Idle, session.state)
    }

    @Test
    fun cancelFromCapturingAndFinalizing() {
        val fromCapturing = DictationSession()
        fromCapturing.start()
        fromCapturing.cancel()
        assertEquals(DictationSessionState.Cancelled, fromCapturing.state)
        assertTrue(fromCapturing.state.isTerminal)

        val fromFinalizing = DictationSession()
        fromFinalizing.start()
        fromFinalizing.requestFinalization()
        fromFinalizing.cancel()
        assertEquals(DictationSessionState.Cancelled, fromFinalizing.state)
    }

    @Test
    fun failFromAllowedStates() {
        val fromIdle = DictationSession()
        fromIdle.fail("microphone unavailable")
        assertEquals(DictationSessionState.Error("microphone unavailable"), fromIdle.state)

        val fromCapturing = DictationSession()
        fromCapturing.start()
        fromCapturing.fail("read error")
        assertEquals(DictationSessionState.Error("read error"), fromCapturing.state)
    }

    @Test
    fun invalidTransitionsFromIdleThrow() {
        val session = DictationSession()

        assertFailsWith<InvalidDictationTransitionException> { session.requestFinalization() }
        assertFailsWith<InvalidDictationTransitionException> { session.completeFinalization() }
        assertFailsWith<InvalidDictationTransitionException> { session.cancel() }
    }

    @Test
    fun invalidTransitionsFromCapturingThrow() {
        val session = DictationSession()
        session.start()

        assertFailsWith<InvalidDictationTransitionException> { session.start() }
        assertFailsWith<InvalidDictationTransitionException> { session.completeFinalization() }
        assertFailsWith<InvalidDictationTransitionException> { session.reset() }
    }

    @Test
    fun invalidTransitionsFromTerminalStatesThrow() {
        val finalized = DictationSession()
        finalized.start()
        finalized.requestFinalization()
        finalized.completeFinalization()
        assertFailsWith<InvalidDictationTransitionException> { finalized.start() }

        val cancelled = DictationSession()
        cancelled.start()
        cancelled.cancel()
        assertFailsWith<InvalidDictationTransitionException> { cancelled.requestFinalization() }

        val failed = DictationSession()
        failed.fail("failure")
        assertFailsWith<InvalidDictationTransitionException> { failed.start() }
    }
}