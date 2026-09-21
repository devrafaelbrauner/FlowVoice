package dev.rafaelbrauner.flowvoice.ui.overlay

import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineSession
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineStatus
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationTarget
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OverlayOwnershipTest {

    private fun session(id: Int, status: DictationPipelineStatus) =
        DictationPipelineSession(id, DictationTarget.ActiveField, status)

    @Test
    fun staleIdleSeenRightAfterStartingAFreshOverlayKeepsTheBar() {
        var ownership = OverlayOwnership.begin(session(0, DictationPipelineStatus.Idle), startedHere = true)

        ownership = OverlayOwnership.onSession(ownership, session(0, DictationPipelineStatus.Idle))

        assertTrue(ownership.owned)
    }

    @Test
    fun staleCancelledFromThePreviousSessionKeepsTheBar() {
        var ownership = OverlayOwnership.begin(session(1, DictationPipelineStatus.Cancelled), startedHere = true)

        ownership = OverlayOwnership.onSession(ownership, session(1, DictationPipelineStatus.Cancelled))
        ownership = OverlayOwnership.onSession(ownership, session(2, DictationPipelineStatus.Starting))
        ownership = OverlayOwnership.onSession(ownership, session(2, DictationPipelineStatus.Recording))

        assertTrue(ownership.owned)
    }

    @Test
    fun cancellingTheOwnedSessionReleasesTheBar() {
        var ownership = OverlayOwnership.begin(session(1, DictationPipelineStatus.Cancelled), startedHere = true)

        ownership = OverlayOwnership.onSession(ownership, session(2, DictationPipelineStatus.Starting))
        ownership = OverlayOwnership.onSession(ownership, session(2, DictationPipelineStatus.Recording))
        ownership = OverlayOwnership.onSession(ownership, session(2, DictationPipelineStatus.Cancelled))

        assertFalse(ownership.owned)
    }

    @Test
    fun newerSessionEndingWithTheSameStatusAsTheBaselineReleasesTheBar() {
        var ownership = OverlayOwnership.begin(session(1, DictationPipelineStatus.Cancelled), startedHere = true)

        ownership = OverlayOwnership.onSession(ownership, session(2, DictationPipelineStatus.Cancelled))

        assertFalse(ownership.owned)
    }

    @Test
    fun busySessionWithoutOwnerCanBeAdoptedToShowStopControls() {
        val released = OverlayOwnership.released()
        assertFalse(released.owned)

        val adopted = OverlayOwnership.adopt(DictationPipelineStatus.Recording)

        assertTrue(adopted.owned)
        assertTrue(OverlayOwnership.onSession(adopted, session(3, DictationPipelineStatus.Recording)).owned)
    }
}
