package dev.rafaelbrauner.flowvoice.shared.overlay

object OverlayStartGuard {
    const val MIN_SDK_APPLICATION_OVERLAY = 26

    fun canShow(sdkInt: Int, canDrawOverlays: Boolean): Boolean =
        sdkInt >= MIN_SDK_APPLICATION_OVERLAY && canDrawOverlays
}
