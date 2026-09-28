package dev.rafaelbrauner.flowvoice.shared.proofreading

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProofreadingGuardTest {
    private val longNote =
        "paciente de 67 anos em acompanhamento ambulatorial refere dor no joelho direito ha tres semanas " +
            "nao tem alergia a dipirona e segue estavel sem febre desde ontem prescrevo dipirona 500 mg de 6 em 6 horas " +
            "por cinco dias e retorno em duas semanas com radiografia do joelho para reavaliacao do quadro"

    // R2 (revisão de código): trocas que mudam o sentido clínico e passavam por distância de edição.
    private val meaningChangingEdits = listOf(
        "hipertensão" to "hipotensão",
        "Hiperglicemia" to "Hipoglicemia",
        "normal" to "anormal",
        "sintomático" to "assintomático",
        "regular" to "irregular",
        "prednisona" to "prednisolona",
        "amoxicilina" to "ampicilina",
        "sessenta gotas" to "setenta gotas",
        "Não tomou a medicação." to "Não, tomou a medicação.",
        "Nega alergias." to "Nega alergias?"
    )

    @Test
    fun meaningChangingEditsAreRejected() {
        meaningChangingEdits.forEach { (dictated, revised) ->
            assertFalse(ProofreadingGuard.accepts(dictated, revised), "$dictated → $revised")
            assertFalse(ProofreadingGuard.accepts("Paciente com $dictated", "Paciente com $revised"), "$dictated → $revised")
        }
    }

    @Test
    fun accentsCasePunctuationAndSpellingAroundClinicalWordsAreAccepted() {
        assertTrue(ProofreadingGuard.accepts("paciente com hipertensao nega alergias", "Paciente com hipertensão. Nega alergias."))
        assertTrue(ProofreadingGuard.accepts("nao tomou a medicacao ha sessenta dias", "Não tomou a medicação há sessenta dias."))
        assertTrue(ProofreadingGuard.accepts("quadro assintomatico, prednisona suspensa", "Quadro assintomático; prednisona suspensa."))
        assertTrue(ProofreadingGuard.accepts("o paciente tem excessao de peso", "O paciente tem exceção de peso."))
    }

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

    // P157 e o pedido de 2026-09-28: a formatação pode corrigir a concordância pela terminação.
    @Test
    fun agreementThatOnlyChangesTheEndingIsAccepted() {
        assertTrue(ProofreadingGuard.accepts("Um deles foram nomeados.", "Um deles foi nomeado."))
        assertTrue(ProofreadingGuard.accepts("os exame foi pedido ontem", "Os exames foram pedidos ontem."))
        assertTrue(ProofreadingGuard.accepts("os resultados está normal", "Os resultados estão normais."))
        assertTrue(ProofreadingGuard.accepts("a dor melhorou e os sintomas regrediu", "A dor melhorou e os sintomas regrediram."))
        assertTrue(ProofreadingGuard.accepts("mama direito sem nódulos", "Mama direita sem nódulos."))
        assertTrue(ProofreadingGuard.accepts("os pacientes é idosos", "Os pacientes são idosos."))
        assertTrue(ProofreadingGuard.accepts("elas tem febre", "Elas têm febre."))
    }

    @Test
    fun agreementDoesNotOpenTheDoorToAnotherWord() {
        val refused = listOf(
            "prednisona" to "prednisolona",
            "amoxicilina" to "ampicilina",
            "direito" to "esquerdo",
            "normal" to "anormal",
            "dois comprimidos" to "três comprimidos",
            "duas doses" to "dois doses",
            "500 mg" to "50 mg",
            "caso novo" to "casa novo",
            "mesa limpa" to "mesma limpa",
            "hipotenso" to "hipertenso",
            "ele e sua mãe" to "ele são sua mãe"
        )
        refused.forEach { (dictated, revised) ->
            assertFalse(ProofreadingGuard.accepts(dictated, revised), "$dictated → $revised")
            assertFalse(ProofreadingGuard.accepts("Paciente com $dictated hoje", "Paciente com $revised hoje"), "$dictated → $revised")
        }
        assertFalse(ProofreadingGuard.accepts("não foram nomeados", "foram nomeados"), "negação tirada")
        assertFalse(ProofreadingGuard.accepts("não foram nomeados", "nós foram nomeados"), "negação trocada por outra palavra")
    }
}
