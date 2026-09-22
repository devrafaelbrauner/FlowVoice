package dev.rafaelbrauner.flowvoice.ui.overlay

import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineSession
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineStatus
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationTarget
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OverlaySessionPolicyTest {

    private fun session(id: Int, status: DictationPipelineStatus) =
        DictationPipelineSession(id, DictationTarget.ActiveField, status)

    @Test
    fun hidingTheBubbleDoesNotCancelADictationTheAppAskedFor() {
        // Início → ACTION_START_DICTATION → DictationOverlay.beginSession(startedHere = false): o app
        // pediu o ditado, e ocultar a bolha não descarta o texto dele (P40).
        val appRequested = OverlayOwnership.begin(session(1, DictationPipelineStatus.Recording), startedHere = false)
        val ownsSession = OverlaySessionPolicy.ownsSession(appRequested)

        assertFalse(
            OverlaySessionPolicy.cancelOnDestroy(
                ownsSession,
                DictationTarget.ActiveField,
                DictationPipelineStatus.Recording
            )
        )
    }

    @Test
    fun hidingTheBubbleStillCancelsTheDictationTheBubbleStarted() {
        val bubbleStarted = OverlayOwnership.begin(session(1, DictationPipelineStatus.Recording), startedHere = true)
        val ownsSession = OverlaySessionPolicy.ownsSession(bubbleStarted)

        assertTrue(
            OverlaySessionPolicy.cancelOnDestroy(
                ownsSession,
                DictationTarget.ActiveField,
                DictationPipelineStatus.Recording
            )
        )
    }

    @Test
    fun adoptingABusySessionShowsControlsWithoutGrantingCancelOnHide() {
        val adopted = OverlayOwnership.adopt(DictationPipelineStatus.Recording)

        assertTrue(adopted.owned)
        assertFalse(OverlaySessionPolicy.ownsSession(adopted))
    }

    @Test
    fun hidingTheBubbleKeepsTheOverlayWhileTheDictationHasAControlOnScreen() {
        assertFalse(OverlaySessionPolicy.shouldStopWithHiddenBubble(true, OverlayMode.Bar))
        assertFalse(OverlaySessionPolicy.shouldStopWithHiddenBubble(true, OverlayMode.Preview))
    }

    @Test
    fun hidingTheBubbleStopsTheOverlayWhenOnlyTheBubbleWasOnScreen() {
        assertTrue(OverlaySessionPolicy.shouldStopWithHiddenBubble(true, OverlayMode.Bubble))
        assertFalse(OverlaySessionPolicy.shouldStopWithHiddenBubble(false, OverlayMode.Bubble))
    }

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
