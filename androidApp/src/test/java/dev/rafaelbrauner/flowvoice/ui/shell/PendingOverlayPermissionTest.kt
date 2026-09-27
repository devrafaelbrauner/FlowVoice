package dev.rafaelbrauner.flowvoice.ui.shell

import kotlin.test.Test
import kotlin.test.assertEquals

class PendingOverlayPermissionTest {

    @Test
    fun grantedOnReturnContinuesTheInterruptedAction() {
        assertEquals(OverlayPermissionReturn.Continue, PendingOverlayPermission.onReturn(pending = true, canDrawOverlays = true))
    }

    @Test
    fun stillDeniedOnReturnReportsIt() {
        assertEquals(OverlayPermissionReturn.StillMissing, PendingOverlayPermission.onReturn(pending = true, canDrawOverlays = false))
    }

    @Test
    fun ordinaryResumeWithoutARequestDoesNothingEvenIfThePermissionExists() {
        assertEquals(OverlayPermissionReturn.NotPending, PendingOverlayPermission.onReturn(pending = false, canDrawOverlays = true))
        assertEquals(OverlayPermissionReturn.NotPending, PendingOverlayPermission.onReturn(pending = false, canDrawOverlays = false))
    }
}
