package dev.rafaelbrauner.flowvoice.ui.screens.dictionary

import dev.rafaelbrauner.flowvoice.shared.dictionary.InMemoryPersonalDictionary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DictionaryScreenStateTest {

    @Test
    fun pendingLabelFollowsCount() {
        assertEquals("Nada pendente", DictionaryScreenState.pendingLabel(0))
        assertEquals("1 termo aguardando", DictionaryScreenState.pendingLabel(1))
        assertEquals("3 termos aguardando", DictionaryScreenState.pendingLabel(3))
    }

    @Test
    fun approvingPendingTermMovesItToApproved() {
        val dictionary = InMemoryPersonalDictionary()
        dictionary.suggestFrom("mando pro Brauner hoje")
        val state = DictionaryScreenState(dictionary)
        assertTrue("Brauner" in state.pending)

        state.approve("Brauner")

        assertFalse("Brauner" in state.pending)
        assertEquals(listOf("Brauner"), state.approved)
    }

    @Test
    fun discardingRemovesPendingWithoutApproving() {
        val dictionary = InMemoryPersonalDictionary()
        dictionary.suggestFrom("commitText mesmo")
        val state = DictionaryScreenState(dictionary)

        state.discard("commitText")

        assertFalse("commitText" in state.pending)
        assertTrue(state.approved.isEmpty())
    }

    @Test
    fun refreshPicksUpSuggestionsMadeElsewhere() {
        val dictionary = InMemoryPersonalDictionary()
        val state = DictionaryScreenState(dictionary)
        assertTrue(state.pending.isEmpty())

        dictionary.suggestFrom("testei no Galaxy ontem")
        state.refresh()

        assertEquals(listOf("testei", "Galaxy", "ontem"), state.pending)
    }

    @Test
    fun manualTermIsTrimmedApprovedAndDraftCleared() {
        val state = DictionaryScreenState(InMemoryPersonalDictionary())

        state.updateDraft("  OpenRouter ")
        assertTrue(state.addDraft())

        assertEquals(listOf("OpenRouter"), state.approved)
        assertEquals("", state.draft)
    }

    @Test
    fun blankDraftIsRejected() {
        val state = DictionaryScreenState(InMemoryPersonalDictionary())
        state.updateDraft("   ")

        assertFalse(state.addDraft())
        assertTrue(state.approved.isEmpty())
    }
}
