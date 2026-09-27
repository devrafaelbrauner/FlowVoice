package dev.rafaelbrauner.flowvoice.ui.shell

enum class OverlayPermissionReturn { NotPending, Continue, StillMissing }

// Quem mandou o usuário autorizar "sobrepor a outros apps" guarda a intenção e, na volta, segue o
// mesmo fluxo em vez de pedir um segundo toque (R5b).
object PendingOverlayPermission {
    fun onReturn(pending: Boolean, canDrawOverlays: Boolean): OverlayPermissionReturn = when {
        !pending -> OverlayPermissionReturn.NotPending
        canDrawOverlays -> OverlayPermissionReturn.Continue
        else -> OverlayPermissionReturn.StillMissing
    }
}
