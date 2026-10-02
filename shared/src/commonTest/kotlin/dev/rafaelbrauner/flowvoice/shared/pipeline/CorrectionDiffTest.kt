package dev.rafaelbrauner.flowvoice.shared.pipeline

import dev.rafaelbrauner.flowvoice.shared.dictionary.CorrectionPair
import kotlin.test.Test
import kotlin.test.assertEquals

// Item 1 da revisão do motor (2026-10-01): o diff conservador entre o que o app escreveu e o que o
// campo tem depois de o usuário consertar. Só troca 1:1 de palavra (4+ letras, só letras) ensina
// regra; espaço/pontuação é o editor (P148), contagem diferente ou mudança grande não se atribui.
class CorrectionDiffTest {
    @Test
    fun identicalTextsTeachNothing() {
        assertEquals(emptyList(), CorrectionDiff.learnable("o exame de sangue", "o exame de sangue"))
        assertEquals(emptyList(), CorrectionDiff.learnable("", ""))
    }

    @Test
    fun onlySpacingOrPunctuationDivergenceIsTheEditorNotACorrection() {
        assertEquals(emptyList(), CorrectionDiff.learnable("o exame, de sangue.", "o exame de sangue"))
        assertEquals(emptyList(), CorrectionDiff.learnable("casa  amarela", "casa amarela"))
        assertEquals(emptyList(), CorrectionDiff.learnable("bem? Consegue", "bem?Consegue"))
    }

    @Test
    fun oneWordSwapKeepsThePunctuationOutOfTheRule() {
        assertEquals(
            listOf(CorrectionPair("caza", "casa")),
            CorrectionDiff.learnable("O caza, hoje.", "O casa, hoje.")
        )
    }

    @Test
    fun twoSwapsAreLearnedInOrder() {
        assertEquals(
            listOf(CorrectionPair("caza", "casa"), CorrectionPair("amanha", "amanhã")),
            CorrectionDiff.learnable("O caza amanha cedo", "O casa amanhã cedo")
        )
    }

    @Test
    fun threeSwapsAreTheMaximum() {
        assertEquals(
            listOf(
                CorrectionPair("caza", "casa"),
                CorrectionPair("amanha", "amanhã"),
                CorrectionPair("tardee", "tarde")
            ),
            CorrectionDiff.learnable("caza amanha tardee", "casa amanhã tarde")
        )
    }

    @Test
    fun fourDivergentPositionsTeachNothing() {
        assertEquals(
            emptyList(),
            CorrectionDiff.learnable("caza amanha tardee keje", "casa amanhã tarde que")
        )
    }

    @Test
    fun tokensWithDigitsTeachNothing() {
        assertEquals(emptyList(), CorrectionDiff.learnable("leito caza2 hoje", "leito casa2 hoje"))
    }

    @Test
    fun wordsShorterThanFourLettersTeachNothing() {
        assertEquals(emptyList(), CorrectionDiff.learnable("fui no aso hoje", "fui no casa hoje"))
    }

    @Test
    fun differentTokenCountsTeachNothing() {
        assertEquals(emptyList(), CorrectionDiff.learnable("o exame de sangue", "o exame e o sangue"))
        assertEquals(emptyList(), CorrectionDiff.learnable("", "casa"))
        assertEquals(emptyList(), CorrectionDiff.learnable("casa", ""))
    }

    @Test
    fun caseDifferencesAreNotCorrections() {
        assertEquals(emptyList(), CorrectionDiff.learnable("Casa", "casa"))
        assertEquals(emptyList(), CorrectionDiff.learnable("A Casa azul", "a casa azul"))
    }
}