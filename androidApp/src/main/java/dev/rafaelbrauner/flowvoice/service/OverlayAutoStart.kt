package dev.rafaelbrauner.flowvoice.service

import dev.rafaelbrauner.flowvoice.shared.overlay.OverlayStartGuard

// Reinstalar ou atualizar o app mata o processo e leva a bolha junto, e ela só voltava pelo
// interruptor dos Ajustes ou pelo microfone do Início (P141). Com "bolha ligada" guardado, o app
// religa sozinho quando volta ao primeiro plano, desde que as permissões continuem valendo.
object OverlayAutoStart {
    fun shouldRestart(
        enabled: Boolean,
        running: Boolean,
        sdkInt: Int,
        canDrawOverlays: Boolean,
        microphoneGranted: Boolean
    ): Boolean =
        enabled && !running && microphoneGranted && OverlayStartGuard.canShow(sdkInt, canDrawOverlays)
}
