package dev.rafaelbrauner.flowvoice.shared.benchmark

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WordErrorRateTest {
    @Test
    fun identicalTextIsZero() {
        val text = "O médico pediu o exame de sangue"
        assertEquals(0.0, WordErrorRate.score(text, text))
    }

    @Test
    fun ignoresPunctuationAndCase() {
        assertEquals(
            0.0,
            WordErrorRate.score(
                "Preciso marcar consulta no posto.",
                "preciso marcar consulta no posto"
            )
        )
    }

    @Test
    fun substitutionCountsAsError() {
        val wer = WordErrorRate.score("o paciente está estável", "o paciente está instável")
        assertTrue(wer > 0.0)
        assertTrue(wer <= 1.0)
    }

    @Test
    fun emptyHypothesisAgainstReferenceIsOne() {
        assertEquals(1.0, WordErrorRate.score("uma frase", ""))
    }
}
