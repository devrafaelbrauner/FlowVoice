package dev.rafaelbrauner.flowvoice.shared.proofreading

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProofreadingMergeTest {
    // O caso do S26 (2026-09-16 10:42): a revisão trocou "Tá" por "Está" e o guard descartou o texto
    // inteiro, junto com a vírgula e as maiúsculas que estavam certas. A palavra do ditado fica; a
    // pontuação da revisão entra.
    @Test
    fun aWordTheRevisionSwappedIsKeptFromTheDictationAndThePunctuationIsTaken() {
        val dictated = "Hoje o dia está muito bonito. Tá muito bonito, por isso iremos para a praia."
        val revised = "Hoje o dia está muito bonito. Está muito bonito, por isso iremos para a praia."

        assertEquals(dictated, ProofreadingMerge.merge(dictated, revised))
    }

    @Test
    fun thePunctuationOfTheRevisionEntersEvenNextToTheKeptWord() {
        val dictated = "Estava muito cansado. Tá com dor."
        val revised = "Estava muito cansado, está com dor."

        assertEquals("Estava muito cansado, tá com dor.", ProofreadingMerge.merge(dictated, revised))
    }

    // Acento e ortografia continuam sendo da revisão: é para isso que ela existe (P132).
    @Test
    fun theAccentAndTheSpellingOfTheRevisionAreKept() {
        val dictated = "amanha vamos a pratica de manha"
        val revised = "Amanhã vamos à prática de manhã."

        assertEquals(revised, ProofreadingMerge.merge(dictated, revised))
    }

    // Número trocado nunca entra, nem com a pontuação junto (dose, pressão, data).
    @Test
    fun aChangedNumberIsKeptFromTheDictation() {
        val dictated = "Tomar 500 mg de dipirona"
        val revised = "Tomar 800 mg de dipirona."

        assertEquals("Tomar 500 mg de dipirona.", ProofreadingMerge.merge(dictated, revised))
    }

    // Texto que não é mais o mesmo ditado (palavra a mais, resposta do modelo): nada a misturar.
    @Test
    fun aRevisionWithAnotherNumberOfWordsMergesNothing() {
        assertNull(ProofreadingMerge.merge("vamos à praia", "vamos à praia amanhã"))
    }

    // Aspas e dois-pontos que o ditado não tinha continuam barrados pelo guard depois da mistura.
    @Test
    fun theMergedTextStillHasToPassTheGuard() {
        val dictated = "o paciente disse que estava bem"
        val revised = "O paciente disse que: \"estava bem\"."

        assertEquals(false, ProofreadingGuard.accepts(dictated, ProofreadingMerge.merge(dictated, revised)!!))
    }
}
