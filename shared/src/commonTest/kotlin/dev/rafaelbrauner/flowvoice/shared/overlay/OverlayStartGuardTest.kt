package dev.rafaelbrauner.flowvoice.shared.overlay

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OverlayStartGuardTest {

    @Test
    fun showsOverlayOnAndroid8WithPermission() {
        assertTrue(OverlayStartGuard.canShow(sdkInt = 26, canDrawOverlays = true))
        assertTrue(OverlayStartGuard.canShow(sdkInt = 36, canDrawOverlays = true))
    }

    @Test
    fun refusesWithoutOverlayPermission() {
        assertFalse(OverlayStartGuard.canShow(sdkInt = 36, canDrawOverlays = false))
    }

    @Test
    fun refusesBeforeAndroid8BecauseApplicationOverlayTypeDoesNotExist() {
        assertFalse(OverlayStartGuard.canShow(sdkInt = 24, canDrawOverlays = true))
        assertFalse(OverlayStartGuard.canShow(sdkInt = 25, canDrawOverlays = true))
    }
}
