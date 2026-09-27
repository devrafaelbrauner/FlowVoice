package dev.rafaelbrauner.flowvoice.ui.overlay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OverlayStartRequestsTest {
    @Test
    fun eachRequestIsTakenExactlyOnce() {
        while (OverlayStartRequests.takePending()) Unit

        assertFalse(OverlayStartRequests.takePending())
        OverlayStartRequests.request()
        assertTrue(OverlayStartRequests.takePending())
        assertFalse(OverlayStartRequests.takePending())
    }

    @Test
    fun requestsMadeBeforeTheOverlayComposesAreStillTaken() {
        while (OverlayStartRequests.takePending()) Unit

        OverlayStartRequests.request()
        OverlayStartRequests.request()

        assertTrue(OverlayStartRequests.takePending())
        assertFalse(OverlayStartRequests.takePending())
    }

    @Test
    fun homeMicWithTheBubbleTurnedOffDictatesWithoutTurningItOn() {
        val plan = OverlayStartRequests.plan(OverlayStartRequests.ACTION_START_DICTATION, bubbleEnabled = false, bubbleOnScreen = false)

        assertEquals(OverlayStartPlan(showBubble = false, persistEnabled = false, requestDictation = true), plan)
    }

    @Test
    fun homeMicKeepsABubbleTheUserHasOn() {
        listOf(true to false, false to true, true to true).forEach { (enabled, onScreen) ->
            val plan = OverlayStartRequests.plan(OverlayStartRequests.ACTION_START_DICTATION, enabled, onScreen)
            assertEquals(OverlayStartPlan(showBubble = true, persistEnabled = false, requestDictation = true), plan)
        }
    }

    @Test
    fun settingsToggleAndAutoRestartTurnTheBubbleOnAndRememberIt() {
        listOf(false, true).forEach { enabled ->
            val plan = OverlayStartRequests.plan(action = null, bubbleEnabled = enabled, bubbleOnScreen = false)
            assertEquals(OverlayStartPlan(showBubble = true, persistEnabled = true, requestDictation = false), plan)
        }
    }
}
