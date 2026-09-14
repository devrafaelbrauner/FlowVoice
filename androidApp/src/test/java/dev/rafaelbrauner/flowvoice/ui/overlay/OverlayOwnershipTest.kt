package dev.rafaelbrauner.flowvoice.ui.overlay

import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OverlayOwnershipTest {

    @Test
    fun staleIdleSeenRightAfterStartingAFreshOverlayKeepsTheBar() {
        var ownership = OverlayOwnership.begin(DictationPipelineStatus.Idle)

        ownership = OverlayOwnership.onStatus(ownership, DictationPipelineStatus.Idle)

        assertTrue(ownership.owned)
    }

    @Test
    fun staleCancelledFromThePreviousSessionKeepsTheBar() {
        var ownership = OverlayOwnership.begin(DictationPipelineStatus.Cancelled)

        ownership = OverlayOwnership.onStatus(ownership, DictationPipelineStatus.Cancelled)
        ownership = OverlayOwnership.onStatus(ownership, DictationPipelineStatus.Starting)
        ownership = OverlayOwnership.onStatus(ownership, DictationPipelineStatus.Recording)

        assertTrue(ownership.owned)
    }

    @Test
    fun cancellingTheOwnedSessionReleasesTheBar() {
        var ownership = OverlayOwnership.begin(DictationPipelineStatus.Cancelled)

        ownership = OverlayOwnership.onStatus(ownership, DictationPipelineStatus.Starting)
        ownership = OverlayOwnership.onStatus(ownership, DictationPipelineStatus.Recording)
        ownership = OverlayOwnership.onStatus(ownership, DictationPipelineStatus.Cancelled)

        assertFalse(ownership.owned)
    }

    @Test
    fun busySessionWithoutOwnerCanBeAdoptedToShowStopControls() {
        val released = OverlayOwnership.released()
        assertFalse(released.owned)

        val adopted = OverlayOwnership.adopt(DictationPipelineStatus.Recording)

        assertTrue(adopted.owned)
        assertTrue(OverlayOwnership.onStatus(adopted, DictationPipelineStatus.Recording).owned)
    }
}
