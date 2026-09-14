package dev.rafaelbrauner.flowvoice.ui.overlay

import kotlin.test.Test
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
}
