package dev.rafaelbrauner.flowvoice.ui.overlay

import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineStatus

data class OverlayOwnership(
    val owned: Boolean,
    val baseline: DictationPipelineStatus? = null
) {
    companion object {
        fun released(): OverlayOwnership = OverlayOwnership(owned = false)

        fun begin(current: DictationPipelineStatus): OverlayOwnership =
            OverlayOwnership(owned = true, baseline = current)

        fun adopt(current: DictationPipelineStatus): OverlayOwnership =
            OverlayOwnership(owned = current.isBusy)

        fun onStatus(ownership: OverlayOwnership, status: DictationPipelineStatus): OverlayOwnership {
            if (!ownership.owned) return ownership
            val baseline = ownership.baseline
            if (baseline != null) {
                if (status == baseline && !status.isBusy) return ownership
                return onStatus(ownership.copy(baseline = null), status)
            }
            return when (status) {
                DictationPipelineStatus.Idle,
                DictationPipelineStatus.Cancelled -> released()
                else -> ownership
            }
        }
    }
}
