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

    // P144: o modelo fecha cada trecho com ponto. Quando o trecho seguinte continua a frase, sobra um
    // ponto no meio dela — no S26 (2026-09-16) saiu "O exame de sangue. mostrou leucocitose.". O ponto
    // já está no campo, então o pedaço novo pede para apagá-lo antes de entrar.
    @Test
    fun aPieceThatContinuesTheSentenceErasesTheFullStopAlreadyTyped() {
        val step = DirectInsertionPlanner.advance(
            DirectInsertionPlan(),
            listOf(ok(0, "O exame de sangue."), ok(1, "mostrou leucocitose."))
        )

        assertEquals(
            DirectInsertionPiece(windowIndex = 1, separator = " ", text = "mostrou leucocitose.", deleteBefore = 1),
            step.pieces[1]
        )
        assertEquals("O exame de sangue mostrou leucocitose.", step.plan.transcript)
    }

    // Maiúscula é o sinal de que o próprio modelo considerou a frase encerrada: nada é apagado, e a
    // inicial não é rebaixada (seria corromper nome próprio).
    @Test
    fun aPieceThatStartsANewSentenceKeepsTheFullStop() {
        val step = DirectInsertionPlanner.advance(
            DirectInsertionPlan(),
            listOf(ok(0, "O exame de sangue."), ok(1, "Mostrou leucocitose."))
        )

        assertEquals(0, step.pieces[1].deleteBefore)
        assertEquals("O exame de sangue. Mostrou leucocitose.", step.plan.transcript)
    }

    // "Dr." não é fim de frase. A palavra antes do ponto precisa ser toda minúscula para o ponto ser
    // apagado; abreviação e nome próprio carregam maiúscula.
    @Test
    fun anAbbreviationBeforeTheFullStopIsNotUndone() {
        val step = DirectInsertionPlanner.advance(
            DirectInsertionPlan(),
            listOf(ok(0, "Avaliado pelo Dr."), ok(1, "grandmont, sem febre."))
        )

        assertEquals(0, step.pieces[1].deleteBefore)
        assertEquals("Avaliado pelo Dr. grandmont, sem febre.", step.plan.transcript)
    }

    @Test
    fun aPieceStartingWithPunctuationErasesTheFullStopAndGluesItself() {
        val step = DirectInsertionPlanner.advance(
            DirectInsertionPlan(),
            listOf(ok(0, "Paciente estável."), ok(1, ", sem febre."))
        )

        assertEquals(
            DirectInsertionPiece(windowIndex = 1, separator = "", text = ", sem febre.", deleteBefore = 1),
            step.pieces[1]
        )
        assertEquals("Paciente estável, sem febre.", step.plan.transcript)
    }

    @Test
    fun theFirstPieceOfTheSessionNeverErasesAnything() {
        val step = DirectInsertionPlanner.advance(DirectInsertionPlan(), listOf(ok(0, "mostrou leucocitose.")))

        assertEquals(0, step.pieces.single().deleteBefore)
    }

    // Depois de "Inserir aqui" noutro campo, ou de uma recusa, o que o FlowVoice digitou já não está
    // logo antes do cursor: nada pode ser apagado, porque o caractere de lá é do usuário.
    @Test
    fun nothingIsErasedWhenWhatWeTypedIsNoLongerRightBeforeTheCursor() {
        val step = DirectInsertionPlanner.advance(
            DirectInsertionPlan(nextWindow = 1, transcript = "O exame de sangue.", contiguous = false),
            listOf(ok(1, "mostrou leucocitose."))
        )

        assertEquals(0, step.pieces.single().deleteBefore)
        assertEquals("O exame de sangue. mostrou leucocitose.", step.plan.transcript)
    }

    // P150, medido no S26 em 2026-09-16 (`openai/gpt-transcribe`): a janela 1 foi à API com 1 s de
    // contexto e devolveu "Tá muito bonito" pelo mesmo áudio que a janela 0 transcreveu como "está
    // muito bonito". Sem reconhecer a repetição, o campo ficou com "muito bonito" duas vezes.
    @Test
    fun theContextTranscribedWithOtherWordsIsNotTypedTwice() {
        val step = DirectInsertionPlanner.advance(
            DirectInsertionPlan(),
            listOf(
                ok(0, "Hoje o dia está muito bonito."),
                ok(1, "Tá muito bonito, por isso iremos para a praia pela manhã.", contextDurationMs = 1000)
            )
        )

        assertEquals("por isso iremos para a praia pela manhã.", step.pieces[1].text)
        assertEquals("Hoje o dia está muito bonito por isso iremos para a praia pela manhã.", step.plan.transcript)
    }

    // A mesma janela sem contexto não repetiu áudio nenhum: o texto entra inteiro.
    @Test
    fun aWindowWithoutContextIsTypedWhole() {
        val step = DirectInsertionPlanner.advance(
            DirectInsertionPlan(),
            listOf(
                ok(0, "Hoje o dia está muito bonito."),
                ok(1, "Tá muito bonito, por isso iremos para a praia pela manhã.")
            )
        )

        assertEquals("Tá muito bonito, por isso iremos para a praia pela manhã.", step.pieces[1].text)
    }

    // Dentro do mesmo lote, o pedaço seguinte vem logo depois do que acabamos de digitar.
    @Test
    fun afterTheFirstPieceOfABatchTheNextOnesAreContiguousAgain() {
        val step = DirectInsertionPlanner.advance(
            DirectInsertionPlan(contiguous = false),
            listOf(ok(0, "O exame de sangue."), ok(1, "mostrou leucocitose."))
        )

        assertEquals(0, step.pieces[0].deleteBefore)
        assertEquals(1, step.pieces[1].deleteBefore)
    }

    private fun ok(index: Int, text: String, contextDurationMs: Long = 0L) =
        TranscriptionSegment(
            windowIndex = index,
            status = TranscriptionSegment.Status.Ok,
            text = text,
            contextDurationMs = contextDurationMs
        )

    private fun failed(index: Int) =
        TranscriptionSegment(index, TranscriptionSegment.Status.Failed, errorKind = "timeout")

    private fun transcribing(index: Int) = TranscriptionSegment(index, TranscriptionSegment.Status.Transcribing)
}
