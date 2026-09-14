package dev.rafaelbrauner.flowvoice.ui.screens.settings

import android.Manifest
import kotlin.test.Test
import kotlin.test.assertEquals

class OverlayToggleTest {
    private fun decide(
        running: Boolean = false,
        sdkInt: Int = 34,
        canDrawOverlays: Boolean = true,
        microphoneGranted: Boolean = true,
        notificationsGranted: Boolean = true,
        permissionsRequested: Boolean = false
    ) = OverlayToggle.decide(running, sdkInt, canDrawOverlays, microphoneGranted, notificationsGranted, permissionsRequested)

    @Test
    fun runningOverlayIsStopped() {
        assertEquals(OverlayToggleAction.Stop, decide(running = true))
    }

    @Test
    fun androidSevenIsUnsupported() {
        assertEquals(OverlayToggleAction.Unsupported, decide(sdkInt = 25))
    }

    @Test
    fun missingOverlayPermissionOpensSettings() {
        assertEquals(OverlayToggleAction.RequestOverlayPermission, decide(canDrawOverlays = false))
    }

    @Test
    fun missingMicrophoneAndNotificationsAreRequestedTogether() {
        assertEquals(
            OverlayToggleAction.RequestPermissions(listOf(Manifest.permission.RECORD_AUDIO, OverlayToggle.POST_NOTIFICATIONS)),
            decide(microphoneGranted = false, notificationsGranted = false)
        )
    }

    @Test
    fun notificationsAreRequestedEvenWhenMicrophoneIsAlreadyGranted() {
        assertEquals(
            OverlayToggleAction.RequestPermissions(listOf(OverlayToggle.POST_NOTIFICATIONS)),
            decide(notificationsGranted = false)
        )
    }

    @Test
    fun deniedNotificationsDoNotBlockStartAfterRequest() {
        assertEquals(OverlayToggleAction.Start, decide(notificationsGranted = false, permissionsRequested = true))
    }

    @Test
    fun deniedMicrophoneAfterRequestIsReported() {
        assertEquals(
            OverlayToggleAction.MicrophoneDenied,
            decide(microphoneGranted = false, permissionsRequested = true)
        )
    }

    @Test
    fun beforeAndroidThirteenNotificationsAreNotRequested() {
        assertEquals(OverlayToggleAction.Start, decide(sdkInt = 30, notificationsGranted = false))
    }
}
