package dev.rafaelbrauner.flowvoice.shared.proofreading

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProofreadingGuardTest {
    @Test
    fun punctuationCapitalizationAndAccentsAreAccepted() {
        assertTrue(ProofreadingGuard.accepts("tomar dipirona", "Tomar dipirona."))
        assertTrue(ProofreadingGuard.accepts("ola mundo", "Olá, mundo."))
    }

    @Test
    fun smallSpellingFixIsAccepted() {
        assertTrue(ProofreadingGuard.accepts("o paciente tem excessao de peso", "O paciente tem exceção de peso."))
    }

    @Test
    fun quotesTheDictationDidNotHaveAreRejected() {
        assertFalse(ProofreadingGuard.accepts("Primeiro ditado pelo início.", "Primeiro ditado: \"Pelo início.\""))
        assertFalse(ProofreadingGuard.accepts("primeiro ditado pelo início", "Primeiro ditado: “Pelo início.”"))
    }

    @Test
    fun replacedWordIsRejected() {
        assertFalse(ProofreadingGuard.accepts("Terceiro ditado pela bolha", "Terceiro colocado pela bolha."))
    }

    @Test
    fun answeringTheDictationInsteadOfRevisingItIsRejected() {
        assertFalse(ProofreadingGuard.accepts("qual a dose de dipirona", "A dose usual de dipirona é 500 mg."))
    }
}
