package dev.rafaelbrauner.flowvoice.ui.overlay

import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineSession
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineStatus

data class OverlayOwnership(
    val owned: Boolean,
    val baselineSession: Int? = null
) {
    companion object {
        fun released(): OverlayOwnership = OverlayOwnership(owned = false)

        fun begin(current: DictationPipelineSession): OverlayOwnership =
            OverlayOwnership(owned = true, baselineSession = current.id)

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
