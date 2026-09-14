package dev.rafaelbrauner.flowvoice.ui.screens.settings

import android.Manifest
import dev.rafaelbrauner.flowvoice.shared.overlay.OverlayStartGuard

sealed interface OverlayToggleAction {
    data object Stop : OverlayToggleAction
    data object Unsupported : OverlayToggleAction
    data object RequestOverlayPermission : OverlayToggleAction
    data class RequestPermissions(val permissions: List<String>) : OverlayToggleAction
    data object MicrophoneDenied : OverlayToggleAction
    data object Start : OverlayToggleAction
}

object OverlayToggle {
    const val SDK_POST_NOTIFICATIONS = 33
    const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"

    fun decide(
        running: Boolean,
        sdkInt: Int,
        canDrawOverlays: Boolean,
        microphoneGranted: Boolean,
        notificationsGranted: Boolean,
        permissionsRequested: Boolean
    ): OverlayToggleAction {
        if (running) return OverlayToggleAction.Stop
        if (sdkInt < OverlayStartGuard.MIN_SDK_APPLICATION_OVERLAY) return OverlayToggleAction.Unsupported
        if (!OverlayStartGuard.canShow(sdkInt, canDrawOverlays)) return OverlayToggleAction.RequestOverlayPermission
        val missing = buildList {
            if (!microphoneGranted) add(Manifest.permission.RECORD_AUDIO)
            if (sdkInt >= SDK_POST_NOTIFICATIONS && !notificationsGranted) add(POST_NOTIFICATIONS)
        }
        if (missing.isNotEmpty() && !permissionsRequested) return OverlayToggleAction.RequestPermissions(missing)
        if (!microphoneGranted) return OverlayToggleAction.MicrophoneDenied
        return OverlayToggleAction.Start
    }
}
