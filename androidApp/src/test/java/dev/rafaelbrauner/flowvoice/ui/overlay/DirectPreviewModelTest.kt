package dev.rafaelbrauner.flowvoice.ui.overlay

import dev.rafaelbrauner.flowvoice.shared.insertion.TextInsertionResult
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineStatus
import dev.rafaelbrauner.flowvoice.shared.pipeline.DirectInsertionProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DirectPreviewModelTest {
    private val typing = DirectInsertionProgress(sessionId = 3, active = true, typed = "Bom dia, Marina.")

    @Test
    fun sessionNotOwnedByTheBubbleAndFinishedSessionsAreHidden() {
        assertEquals(DirectPreviewState.Hidden, state(DictationPipelineStatus.Recording, owned = false))
        assertEquals(DirectPreviewState.Hidden, state(DictationPipelineStatus.Idle))
        assertEquals(DirectPreviewState.Hidden, state(DictationPipelineStatus.Cancelled))
    }

    @Test
    fun startingShowsOnlyTheStateAndCancel() {
        val live = assertIs<DirectPreviewState.Live>(state(DictationPipelineStatus.Starting, progress = DirectInsertionProgress()))

        assertEquals(DirectPreviewModel.STATUS_STARTING, live.status)
        assertEquals("", live.typedTail)
        assertFalse(live.canInsertHere)
    }

    @Test
    fun recordingShowsClockListeningHintTextAlreadyInTheFieldAndTranscribing() {
        val live = assertIs<DirectPreviewState.Live>(
            state(DictationPipelineStatus.Recording, transcribing = true, elapsedMs = 12_300L)
        )

        assertEquals(DirectPhase.Recording, live.phase)
        assertEquals("0:12", live.clock)
        assertEquals(DirectPreviewModel.STATUS_LISTENING, live.status)
        assertEquals("Bom dia, Marina.", live.typedTail)
        assertTrue(live.transcribing)
        assertEquals("", live.pending)
        assertNull(live.notice)
        assertFalse(live.canInsertHere)
        assertEquals(DirectPreviewModel.CANCEL, live.cancelLabel)
    }

    @Test
    fun pausedRecordingExplainsWhyShowsWhatIsPendingAndOffersInsertHere() {
        val paused = typing.copy(pending = " consegui fechar", pausedReason = "o foco mudou de app; toque em Inserir aqui para escrever no app atual")

        val live = assertIs<DirectPreviewState.Live>(state(DictationPipelineStatus.Recording, progress = paused))

        assertEquals(DirectPreviewModel.STATUS_PAUSED, live.status)
        assertEquals("consegui fechar", live.pending)
        assertEquals("o foco mudou de app; toque em Inserir aqui para escrever no app atual", live.notice)
        assertTrue(live.canInsertHere)
    }

    @Test
    fun failedWindowWarningIsShownWhileRecording() {
        val live = assertIs<DirectPreviewState.Live>(
            state(DictationPipelineStatus.Recording, progress = typing.copy(warning = "Trecho 2 de 3 falhou (timeout): texto incompleto"))
        )

        assertEquals("Trecho 2 de 3 falhou (timeout): texto incompleto", live.warning)
    }

    @Test
    fun finishingTypesTheLastPieceWithoutInsertHereUnlessSomethingIsPending() {
        val live = assertIs<DirectPreviewState.Live>(state(DictationPipelineStatus.Transcribing, transcribing = true))

        assertEquals(DirectPhase.Finishing, live.phase)
        assertEquals(DirectPreviewModel.STATUS_FINISHING, live.status)
        assertFalse(live.canInsertHere)
    }

    // P147: a revisão final leva ~1 s no fim do ditado, e a prévia diz o que está acontecendo.
    @Test
    fun theFinalProofreadingIsAnnouncedWhileItRuns() {
        val live = assertIs<DirectPreviewState.Live>(
            state(DictationPipelineStatus.Transcribing, progress = typing.copy(proofreading = true))
        )

        assertEquals(DirectPhase.Finishing, live.phase)
        assertEquals(DirectPreviewModel.STATUS_PROOFREADING, live.status)
        assertEquals("Bom dia, Marina.", live.typedTail)
    }

    @Test
    fun readyWithLeftoverTextOffersInsertHereAndDiscard() {
        val ready = DictationPipelineStatus.Ready(
            text = " consegui fechar o orçamento",
            warning = null,
            latencyMs = 900L,
            refusal = "o campo mudou; toque em Inserir aqui para escrever no campo atual"
        )

        val live = assertIs<DirectPreviewState.Live>(state(ready, progress = typing.copy(pending = ready.text, pausedReason = ready.refusal)))

        assertEquals(DirectPhase.Ready, live.phase)
        assertEquals(DirectPreviewModel.STATUS_NOT_TYPED, live.status)
        assertEquals("consegui fechar o orçamento", live.pending)
        assertEquals("o campo mudou; toque em Inserir aqui para escrever no campo atual", live.notice)
        assertTrue(live.canInsertHere)
        assertEquals(DirectPreviewModel.DISCARD, live.cancelLabel)
    }

    @Test
    fun completedSessionReportsHowManyWordsWereTypedWithTheWarning() {
        val result = assertIs<DirectPreviewState.Result>(
            state(
                DictationPipelineStatus.Completed(
                    text = "Bom dia, Marina. Consegui fechar",
                    insertion = TextInsertionResult(true, "direto", "digitado no campo"),
                    warning = "Trecho 2 de 3 falhou (timeout): texto incompleto"
                )
            )
        )

        assertTrue(result.success)
        assertEquals("Digitado no campo · 5 palavras", result.message)
        assertEquals("Trecho 2 de 3 falhou (timeout): texto incompleto", result.detail)
        assertEquals(
            "Digitado no campo · 1 palavra",
            assertIs<DirectPreviewState.Result>(
                state(DictationPipelineStatus.Completed("Ok.", TextInsertionResult(true, "direto", "ok")))
            ).message
        )
    }

    @Test
    fun completedWithoutTextSaysNothingWasTranscribed() {
        val result = assertIs<DirectPreviewState.Result>(
            state(DictationPipelineStatus.Completed("", TextInsertionResult(false, "direto", "sem texto para inserir")))
        )

        assertFalse(result.success)
        assertEquals(DirectPreviewModel.NOTHING_TRANSCRIBED, result.message)
    }

    @Test
    fun failureIsShownUntilDismissed() {
        val failed = DictationPipelineStatus.Failed("Nenhum trecho transcrito (sem rede)")

        val shown = assertIs<DirectPreviewState.Result>(state(failed))

        assertEquals("Falha no ditado", shown.message)
        assertEquals("Nenhum trecho transcrito (sem rede)", shown.detail)
        assertEquals(DirectPreviewState.Hidden, state(failed, dismissed = true))
        assertEquals(
            DirectPreviewState.Hidden,
            state(DictationPipelineStatus.Completed("Ok.", TextInsertionResult(true, "direto", "ok")), dismissed = true)
        )
    }

    @Test
    fun tailKeepsTheEndOfTheTypedTextStartingAtAWord() {
        assertEquals("curto", DirectPreviewModel.tail("  curto ", maxChars = 10))
        assertEquals("…quatro", DirectPreviewModel.tail("um dois três quatro", maxChars = 10))
        assertEquals("…abcdefghij", DirectPreviewModel.tail("xxabcdefghij", maxChars = 10))
    }

    private fun state(
        status: DictationPipelineStatus,
        progress: DirectInsertionProgress = typing,
        transcribing: Boolean = false,
        elapsedMs: Long = 0L,
        owned: Boolean = true,
        dismissed: Boolean = false
    ): DirectPreviewState = DirectPreviewModel.from(
        status = status,
        progress = progress,
        transcribing = transcribing,
        elapsedMs = elapsedMs,
        owned = owned,
        dismissed = dismissed
    )
}
