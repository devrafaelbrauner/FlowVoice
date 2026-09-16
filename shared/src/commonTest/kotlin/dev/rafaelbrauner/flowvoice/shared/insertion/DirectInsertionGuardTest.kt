package dev.rafaelbrauner.flowvoice.shared.insertion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DirectInsertionGuardTest {
    @Test
    fun sameAppAndSameInputIsAllowed() {
        assertNull(refusal(start = NOTES, current = NOTES, pinned = 7, input = 7))
    }

    @Test
    fun firstInsertionWithoutPinnedInputOnlyChecksTheApp() {
        assertNull(refusal(start = NOTES, current = NOTES, pinned = null, input = 3))
    }

    @Test
    fun deviceWithoutInputTrackingOnlyChecksTheApp() {
        assertNull(refusal(start = NOTES, current = NOTES, pinned = null, input = null))
    }

    @Test
    fun flowVoiceInFrontIsRefused() {
        assertEquals(DirectInsertionGuard.Reason.OwnApp, refusal(start = NOTES, current = OWN)?.reason)
    }

    @Test
    fun unknownDestinationIsRefusedBecauseNobodyTappedInsert() {
        assertEquals(DirectInsertionGuard.Reason.UnknownDestination, refusal(start = null, current = NOTES)?.reason)
        assertEquals(DirectInsertionGuard.Reason.UnknownDestination, refusal(start = NOTES, current = null)?.reason)
    }

    @Test
    fun anotherAppIsRefused() {
        val refusal = refusal(start = NOTES, current = WHATSAPP)

        assertEquals(DirectInsertionGuard.Reason.AppChanged, refusal?.reason)
        assertEquals("o foco mudou de app; toque em Inserir aqui para escrever no app atual", refusal?.message)
    }

    @Test
    fun anotherInputInTheSameAppIsRefusedSoAnotherChatNeverReceivesTheText() {
        assertEquals(
            DirectInsertionGuard.Reason.FieldChanged,
            refusal(start = WHATSAPP, current = WHATSAPP, pinned = 4, input = 6)?.reason
        )
        assertEquals(
            DirectInsertionGuard.Reason.FieldChanged,
            refusal(start = WHATSAPP, current = WHATSAPP, pinned = 4, input = null)?.reason
        )
    }

    @Test
    fun everyRefusalHasAMessagePointingToInsertHereAndARouteForTheLog() {
        DirectInsertionGuard.Reason.entries.forEach { reason ->
            assertEquals(true, reason.message.contains("Inserir aqui"), reason.message)
            assertEquals(true, reason.route.startsWith("trava:"), reason.route)
        }
    }

    private fun refusal(start: String?, current: String?, pinned: Int? = null, input: Int? = null) =
        DirectInsertionGuard.refusal(
            startPackage = start,
            currentPackage = current,
            ownPackage = OWN,
            pinnedInput = pinned,
            currentInput = input
        )

    private companion object {
        const val OWN = "dev.rafaelbrauner.flowvoice"
        const val NOTES = "com.samsung.android.app.notes"
        const val WHATSAPP = "com.whatsapp"
    }
}
