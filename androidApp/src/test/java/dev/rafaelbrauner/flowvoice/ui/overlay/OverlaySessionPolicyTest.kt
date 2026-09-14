package dev.rafaelbrauner.flowvoice.ui.overlay

import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineStatus
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationTarget
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OverlaySessionPolicyTest {

    @Test
    fun destroyCancelsOnlyAnActiveFieldSessionOwnedByTheOverlay() {
        assertTrue(OverlaySessionPolicy.cancelOnDestroy(true, DictationTarget.ActiveField, DictationPipelineStatus.Recording))
        assertTrue(OverlaySessionPolicy.cancelOnDestroy(true, DictationTarget.ActiveField, DictationPipelineStatus.Ready("texto")))

        assertFalse(OverlaySessionPolicy.cancelOnDestroy(false, DictationTarget.ActiveField, DictationPipelineStatus.Recording))
        assertFalse(OverlaySessionPolicy.cancelOnDestroy(true, DictationTarget.Note, DictationPipelineStatus.Recording))
        assertFalse(OverlaySessionPolicy.cancelOnDestroy(true, DictationTarget.ActiveField, DictationPipelineStatus.Idle))
    }

    @Test
    fun ownershipEndsWithTheSessionSoALaterSessionStartedElsewhereSurvivesTheOverlay() {
        var owned = true
        owned = OverlaySessionPolicy.ownedAfter(owned, DictationPipelineStatus.Starting)
        owned = OverlaySessionPolicy.ownedAfter(owned, DictationPipelineStatus.Recording)
        assertTrue(owned)

        owned = OverlaySessionPolicy.ownedAfter(owned, DictationPipelineStatus.Cancelled)
        assertFalse(owned)

        owned = OverlaySessionPolicy.ownedAfter(owned, DictationPipelineStatus.Starting)
        owned = OverlaySessionPolicy.ownedAfter(owned, DictationPipelineStatus.Recording)
        assertFalse(owned)
        assertFalse(OverlaySessionPolicy.cancelOnDestroy(owned, DictationTarget.ActiveField, DictationPipelineStatus.Recording))
    }
}
