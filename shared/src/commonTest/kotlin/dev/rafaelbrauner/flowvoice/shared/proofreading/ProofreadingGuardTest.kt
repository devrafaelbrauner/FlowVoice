package dev.rafaelbrauner.flowvoice.shared.proofreading

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProofreadingGuardTest {
    private val longNote =
        "paciente de 67 anos em acompanhamento ambulatorial refere dor no joelho direito ha tres semanas " +
            "nao tem alergia a dipirona e segue estavel sem febre desde ontem prescrevo dipirona 500 mg de 6 em 6 horas " +
            "por cinco dias e retorno em duas semanas com radiografia do joelho para reavaliacao do quadro"

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
    fun longNoteWithPunctuationAccentsAndSpellingFixesIsAccepted() {
        val revised = "Paciente de 67 anos, em acompanhamento ambulatorial, refere dor no joelho direito há três semanas. " +
            "Não tem alergia à dipirona e segue estável, sem febre desde ontem. Prescrevo dipirona 500 mg de 6 em 6 horas " +
            "por cinco dias e retorno em duas semanas com radiografia do joelho para reavaliação do quadro."

        assertTrue(ProofreadingGuard.accepts(longNote, revised))
    }

    @Test
    fun longNoteWithAChangedDoseIsRejected() {
        assertFalse(ProofreadingGuard.accepts(longNote, longNote.replace("500 mg", "50 mg")))
    }

    @Test
    fun longNoteLosingANegationIsRejected() {
        assertFalse(ProofreadingGuard.accepts(longNote, longNote.replace("nao tem alergia", "tem alergia")))
    }

    @Test
    fun longNoteWithSwappedLateralityIsRejected() {
        assertFalse(ProofreadingGuard.accepts(longNote, longNote.replace("joelho direito", "joelho esquerdo")))
    }

    @Test
    fun replacedWordIsRejectedInShortAndLongTexts() {
        assertFalse(ProofreadingGuard.accepts("Terceiro ditado pela bolha", "Terceiro colocado pela bolha."))
        assertFalse(ProofreadingGuard.accepts("$longNote terceiro ditado", "$longNote terceiro colocado"))
    }

    @Test
    fun quotesTheDictationDidNotHaveAreRejected() {
        assertFalse(ProofreadingGuard.accepts("Primeiro ditado pelo início.", "Primeiro ditado: \"Pelo início.\""))
        assertFalse(ProofreadingGuard.accepts("primeiro ditado pelo início", "Primeiro ditado: “Pelo início.”"))
        assertFalse(ProofreadingGuard.accepts("primeiro ditado pelo início", "Primeiro ditado ‘pelo início’."))
    }

    @Test
    fun newColonOrLineBreakIsRejected() {
        assertFalse(ProofreadingGuard.accepts("primeiro ditado pelo início", "Primeiro ditado: pelo início."))
        assertFalse(ProofreadingGuard.accepts("primeiro ditado pelo início", "Primeiro ditado\npelo início."))
    }

    @Test
    fun leftoverDelimiterIsRejectedEvenInALongText() {
        assertFalse(ProofreadingGuard.accepts(longNote, "$longNote</ditado>."))
        assertFalse(ProofreadingGuard.accepts(longNote, "Aqui está: <ditado>$longNote"))
    }

    @Test
    fun answeringTheDictationInsteadOfRevisingItIsRejected() {
        assertFalse(ProofreadingGuard.accepts("qual a dose de dipirona", "A dose usual de dipirona é 500 mg."))
    }
}
