package dev.rafaelbrauner.flowvoice.ui.screens.dictionary

import dev.rafaelbrauner.flowvoice.shared.dictionary.CorrectionPair
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

        // "ontem" entrou na stop-list do TermSuggester (onda 1) e não volta como sugestão.
        assertEquals(listOf("testei", "Galaxy"), state.pending)
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

    @Test
    fun removingAnApprovedTermStopsItFromRewritingDictations() {
        val dictionary = InMemoryPersonalDictionary()
        val state = DictionaryScreenState(dictionary)
        state.updateDraft("Espinéia")
        state.addDraft()
        assertEquals("Paciente com Espinéia", dictionary.apply("Paciente com espinéia"))

        state.remove("Espinéia")

        assertTrue(state.approved.isEmpty())
        assertEquals("Paciente com espinéia", dictionary.apply("Paciente com espinéia"))
    }

    @Test
    fun removeDoesNotTouchAPendingSuggestion() {
        val dictionary = InMemoryPersonalDictionary()
        dictionary.suggestFrom("mando pro Brauner hoje")
        val state = DictionaryScreenState(dictionary)

        state.remove("Brauner")

        assertTrue("Brauner" in state.pending)
    }

    @Test
    fun stateExposesLearnedCorrections() {
        val dictionary = InMemoryPersonalDictionary()
        val state = DictionaryScreenState(dictionary)
        assertTrue(state.corrections.isEmpty())

        dictionary.learnCorrection("caza", "casa")
        state.refresh()

        assertEquals(listOf(CorrectionPair("caza", "casa")), state.corrections)
    }

    @Test
    fun forgettingCorrectionRemovesOnlyThatRule() {
        val dictionary = InMemoryPersonalDictionary()
        dictionary.learnCorrection("caza", "casa")
        dictionary.learnCorrection("medico", "médico")
        val state = DictionaryScreenState(dictionary)
        assertEquals(listOf(CorrectionPair("caza", "casa"), CorrectionPair("medico", "médico")), state.corrections)

        state.forgetCorrection("caza")

        assertEquals(listOf(CorrectionPair("medico", "médico")), state.corrections)
        // Esquecer a regra não rebaixa o termo certo: ele segue aprovado no vocabulário.
        assertTrue("casa" in state.approved)
    }
}
