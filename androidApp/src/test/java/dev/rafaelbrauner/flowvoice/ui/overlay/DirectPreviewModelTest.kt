package dev.rafaelbrauner.flowvoice.ui.overlay

import dev.rafaelbrauner.flowvoice.shared.insertion.TextInsertionResult
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineStatus
import dev.rafaelbrauner.flowvoice.shared.pipeline.DirectInsertionProgress
import kotlin.test.Test
import kotlin.test.assertEquals
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

    // S26, 2026-09-28: o cartão cobria a linha em que se ditava e repetia o que já estava no campo.
    @Test
    fun normalDictationShowsNoCardFromStartToFinish() {
        assertEquals(DirectPreviewState.Hidden, state(DictationPipelineStatus.Starting, progress = DirectInsertionProgress()))
        assertEquals(DirectPreviewState.Hidden, state(DictationPipelineStatus.Recording, transcribing = true, elapsedMs = 12_300L))
        assertEquals(DirectPreviewState.Hidden, state(DictationPipelineStatus.Transcribing, transcribing = true))
    }

    @Test
    fun withoutTheBubbleTheCardSaysWhereToFinish() {
        val hidden = assertIs<DirectPreviewState.Live>(state(DictationPipelineStatus.Recording, bubbleHidden = true))

        assertEquals(DirectPreviewModel.STATUS_LISTENING_WITHOUT_BUBBLE, hidden.status)
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

    // Decisão de 2026-09-28: aviso de trecho (o pipeline só o manda com a revisão final desligada) é uma
    // linha curta, sem cabeçalho nem botões.
    @Test
    fun aFailedWindowWhileRecordingIsOnlyAShortNotice() {
        val warning = "Trecho 2 de 3 falhou (timeout): texto incompleto"

        assertEquals(
            DirectPreviewState.Notice(warning),
            state(DictationPipelineStatus.Recording, progress = typing.copy(warning = warning))
        )
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
        // B (S26, 2026-09-28 13:56): o "Cancelar" deste cartão descartou a passada final.
        assertNull(live.cancelLabel, "depois do toque de parar não há o que cancelar")
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

    // Decisão de 2026-09-28: sem "Digitado no campo · N palavras" nem "Nada transcrito." — o texto está no
    // campo, e ditado sem texto não perdeu nada. Só o aviso de que algo faltou, numa linha.
    @Test
    fun aCompletedDictationShowsOnlyTheNoticeOfWhatWasLost() {
        val warning = "Trecho 2 de 3 falhou (timeout): texto incompleto"
        val typed = TextInsertionResult(true, "direto", "digitado no campo")

        assertEquals(DirectPreviewState.Notice(warning), state(DictationPipelineStatus.Completed("Bom dia, Marina.", typed, warning)))
        assertEquals(DirectPreviewState.Hidden, state(DictationPipelineStatus.Completed("Bom dia, Marina.", typed)))
        assertEquals(
            DirectPreviewState.Hidden,
            state(DictationPipelineStatus.Completed("", TextInsertionResult(false, "direto", "sem texto para inserir")))
        )
        assertEquals(DirectPreviewState.Hidden, state(DictationPipelineStatus.Completed("Bom dia, Marina.", typed, warning), dismissed = true))
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
        dismissed: Boolean = false,
        bubbleHidden: Boolean = false
    ): DirectPreviewState = DirectPreviewModel.from(
        status = status,
        progress = progress,
        transcribing = transcribing,
        elapsedMs = elapsedMs,
        owned = owned,
        dismissed = dismissed,
        bubbleHidden = bubbleHidden
    )
}
