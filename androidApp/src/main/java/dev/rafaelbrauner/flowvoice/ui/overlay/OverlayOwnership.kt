package dev.rafaelbrauner.flowvoice.ui.overlay

import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineSession
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineStatus

data class OverlayOwnership(
    val owned: Boolean,
    val baselineSession: Int? = null,
    /** Verdadeiro só quando a bolha iniciou o ditado; ocultar o botão cancela essa sessão (P103, P40). */
    val startedHere: Boolean = false
) {
    companion object {
        fun released(): OverlayOwnership = OverlayOwnership(owned = false)

        // O pedido de início do app (Início → ACTION_START_DICTATION) chega pelo mesmo caminho, mas quem
        // começou o ditado foi o app: ocultar a bolha não cancela o texto dele (P40).
        fun begin(current: DictationPipelineSession, startedHere: Boolean): OverlayOwnership =
            OverlayOwnership(owned = true, baselineSession = current.id, startedHere = startedHere)

        fun adopt(current: DictationPipelineStatus): OverlayOwnership =
            OverlayOwnership(owned = current.isBusy)

        fun onSession(ownership: OverlayOwnership, session: DictationPipelineSession): OverlayOwnership {
            if (!ownership.owned) return ownership
            val baseline = ownership.baselineSession
            if (baseline != null && session.id <= baseline) return ownership
            return when (session.status) {
                DictationPipelineStatus.Idle,
                DictationPipelineStatus.Cancelled -> released()
                else -> ownership
            }
        }
    }
}
