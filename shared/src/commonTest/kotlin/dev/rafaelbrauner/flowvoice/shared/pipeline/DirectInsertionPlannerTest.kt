package dev.rafaelbrauner.flowvoice.shared.pipeline

import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DirectInsertionPlannerTest {
    @Test
    fun firstWindowIsTypedWithoutSeparatorAndTheNextOnesWithASpace() {
        val step = DirectInsertionPlanner.advance(
            DirectInsertionPlan(),
            listOf(ok(0, "Bom dia, Marina."), ok(1, "Consegui fechar o orçamento."))
        )

        assertEquals(
            listOf(
                DirectInsertionPiece(windowIndex = 0, separator = "", text = "Bom dia, Marina."),
                DirectInsertionPiece(windowIndex = 1, separator = " ", text = "Consegui fechar o orçamento.")
            ),
            step.pieces
        )
        assertEquals(2, step.plan.nextWindow)
        assertEquals("Bom dia, Marina. Consegui fechar o orçamento.", step.plan.transcript)
    }

    @Test
    fun windowReadyBeforeThePreviousOneWaitsSoTheOrderIsKept() {
        val waiting = DirectInsertionPlanner.advance(
            DirectInsertionPlan(),
            listOf(transcribing(0), ok(1, "segunda"))
        )

        assertTrue(waiting.pieces.isEmpty())
        assertEquals(0, waiting.plan.nextWindow)

        val released = DirectInsertionPlanner.advance(waiting.plan, listOf(ok(0, "primeira"), ok(1, "segunda")))

        assertEquals(listOf("primeira", "segunda"), released.pieces.map { it.text })
    }

    @Test
    fun windowNotYetSubmittedToTranscriptionStopsTheAdvance() {
        val step = DirectInsertionPlanner.advance(DirectInsertionPlan(), listOf(ok(0, "um"), ok(2, "três")))

        assertEquals(listOf("um"), step.pieces.map { it.text })
        assertEquals(1, step.plan.nextWindow)
    }

    @Test
    fun failedAndSilentWindowsArePassedOverWithoutTypingAnything() {
        val step = DirectInsertionPlanner.advance(
            DirectInsertionPlan(),
            listOf(ok(0, "um"), failed(1), ok(2, ""), ok(3, "quatro"))
        )

        assertEquals(listOf("um", "quatro"), step.pieces.map { it.text })
        assertEquals(4, step.plan.nextWindow)
    }

    @Test
    fun failureBeforeAnyTextKeepsTheFirstTypedPieceWithoutSeparator() {
        val step = DirectInsertionPlanner.advance(DirectInsertionPlan(), listOf(failed(0), ok(1, "dois")))

        assertEquals(listOf(DirectInsertionPiece(windowIndex = 1, separator = "", text = "dois")), step.pieces)
    }

    @Test
    fun wordRepeatedAtTheBoundaryIsDroppedFromTheNewPieceBecauseTypedTextCannotBeUndone() {
        val step = DirectInsertionPlanner.advance(
            DirectInsertionPlan(),
            listOf(ok(0, "é muito"), ok(1, "muito importante"))
        )

        assertEquals(listOf("é muito", "importante"), step.pieces.map { it.text })
        assertEquals("é muito importante", step.plan.transcript)
    }

    @Test
    fun pieceFullyRepeatingTheEndTypesNothingButStillAdvances() {
        val step = DirectInsertionPlanner.advance(
            DirectInsertionPlan(),
            listOf(ok(0, "pediu o exame"), ok(1, "o exame"), ok(2, "de sangue"))
        )

        assertEquals(listOf("pediu o exame", "de sangue"), step.pieces.map { it.text })
        assertEquals(3, step.plan.nextWindow)
    }

    @Test
    fun advancingAgainWithTheSameSegmentsTypesNothingTwice() {
        val segments = listOf(ok(0, "um"), ok(1, "dois"))
        val first = DirectInsertionPlanner.advance(DirectInsertionPlan(), segments)

        val second = DirectInsertionPlanner.advance(first.plan, segments)

        assertTrue(second.pieces.isEmpty())
        assertEquals(first.plan, second.plan)
    }

    // P143: o trecho que emenda depois de ponto final entra com um espaço. No S26 (0.5.0) saiu
    // "de Grandmont.Queda da pressão arterial." no campo.
    @Test
    fun aPieceAfterAFullStopIsSeparatedByASingleSpace() {
        val step = DirectInsertionPlanner.advance(
            DirectInsertionPlan(),
            listOf(ok(0, "de Grandmont."), ok(1, "Queda da pressão arterial."))
        )

        assertEquals(
            listOf(
                DirectInsertionPiece(windowIndex = 0, separator = "", text = "de Grandmont."),
                DirectInsertionPiece(windowIndex = 1, separator = " ", text = "Queda da pressão arterial.")
            ),
            step.pieces
        )
        assertEquals("de Grandmont. Queda da pressão arterial.", step.plan.transcript)
    }

    // Pontuação que pertence à frase anterior não pode entrar depois de um espaço.
    @Test
    fun aPieceStartingWithPunctuationIsGluedToTheTextAlreadyTyped() {
        val step = DirectInsertionPlanner.advance(
            DirectInsertionPlan(),
            listOf(ok(0, "Paciente refere dor"), ok(1, ", sem febre."))
        )

        assertEquals(listOf("", ""), step.pieces.map { it.separator })
        assertEquals("Paciente refere dor, sem febre.", step.plan.transcript)
    }

    @Test
    fun aWordBrokenAtTheCutIsCompletedWithoutASeparator() {
        val step = DirectInsertionPlanner.advance(
            DirectInsertionPlan(),
            listOf(ok(0, "com episódios de di"), ok(1, "de diarreia."))
        )

        assertEquals(
            DirectInsertionPiece(windowIndex = 1, separator = "", text = "arreia."),
            step.pieces[1]
        )
        assertEquals("com episódios de diarreia.", step.plan.transcript)
    }

    private fun ok(index: Int, text: String) = TranscriptionSegment(index, TranscriptionSegment.Status.Ok, text)

    private fun failed(index: Int) =
        TranscriptionSegment(index, TranscriptionSegment.Status.Failed, errorKind = "timeout")

    private fun transcribing(index: Int) = TranscriptionSegment(index, TranscriptionSegment.Status.Transcribing)
}
