package dev.rafaelbrauner.flowvoice.ui.overlay

import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineStatus
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationTarget

object OverlaySessionPolicy {
    fun ownedAfter(owned: Boolean, status: DictationPipelineStatus): Boolean = owned && status.isBusy

    fun cancelOnDestroy(owned: Boolean, target: DictationTarget, status: DictationPipelineStatus): Boolean =
        owned && target == DictationTarget.ActiveField && status.isBusy
}
