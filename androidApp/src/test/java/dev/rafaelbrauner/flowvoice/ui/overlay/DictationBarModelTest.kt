package dev.rafaelbrauner.flowvoice.ui.overlay

import dev.rafaelbrauner.flowvoice.shared.insertion.TextInsertionResult
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineStatus
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationTarget
import dev.rafaelbrauner.flowvoice.shared.preview.LivePreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DictationBarModelTest {
    private val live = LivePreview(finalized = "Bom dia, Marina.", provisional = "consegui fechar")

    @Test
    fun sessionNotStartedByTheOverlayStaysHidden() {
        val state = state(DictationPipelineStatus.Recording, owned = false)

        assertEquals(DictationBarState.Hidden, state)
    }

    @Test
    fun startingDisablesInsert() {
        val bar = assertIs<DictationBarState.Live>(state(DictationPipelineStatus.Starting))

        assertEquals(BarPhase.Starting, bar.phase)
        assertEquals(DictationBarModel.ROUTE_STARTING, bar.route)
        assertTrue(!bar.canInsert)
    }

    @Test
    fun recordingShowsLiveTextListeningRouteAndClock() {
        val bar = assertIs<DictationBarState.Live>(state(DictationPipelineStatus.Recording, elapsedMs = 6_400L))

        assertEquals("Bom dia, Marina.", bar.finalized)
        assertEquals("consegui fechar", bar.provisional)
        assertEquals(DictationBarModel.ROUTE_LISTENING, bar.route)
        assertEquals("0:06", bar.clock)
        assertTrue(bar.canInsert)
    }

    @Test
    fun transcribingRouteFollowsProofreadingPreference() {
        val withReview = assertIs<DictationBarState.Live>(
            state(DictationPipelineStatus.Transcribing, proofreading = true)
        )
        val withoutReview = assertIs<DictationBarState.Live>(
            state(DictationPipelineStatus.Transcribing, proofreading = false)
        )

        assertEquals(DictationBarModel.ROUTE_PROOFREADING, withReview.route)
        assertEquals(DictationBarModel.ROUTE_TRANSCRIBING, withoutReview.route)
        assertTrue(!withReview.canInsert)
    }

    @Test
    fun readyShowsRevisedTextAsFinalizedAndKeepsWarning() {
        val bar = assertIs<DictationBarState.Live>(
            state(DictationPipelineStatus.Ready("Bom dia, Marina.", warning = "Trecho 2 de 3 falhou (timeout): texto incompleto"))
        )

        assertEquals("Bom dia, Marina.", bar.finalized)
        assertEquals("", bar.provisional)
        assertEquals(DictationBarModel.ROUTE_READY, bar.route)
        assertEquals("Trecho 2 de 3 falhou (timeout): texto incompleto", bar.warning)
        assertTrue(bar.canInsert)
    }

    @Test
    fun refusedInsertionKeepsTextVisibleWithReasonAndRetry() {
        val bar = assertIs<DictationBarState.Live>(
            state(DictationPipelineStatus.Ready("Bom dia, Marina.", refusal = "o foco mudou de app; toque em Inserir de novo para inserir no app atual"))
        )

        assertEquals("Bom dia, Marina.", bar.finalized)
        assertEquals(DictationBarModel.ROUTE_NOT_INSERTED, bar.route)
        assertEquals("o foco mudou de app; toque em Inserir de novo para inserir no app atual", bar.warning)
        assertTrue(bar.canInsert)
    }

    @Test
    fun readyWithoutTextDisablesInsertAndExplains() {
        val bar = assertIs<DictationBarState.Live>(state(DictationPipelineStatus.Ready("")))

        assertTrue(!bar.canInsert)
        assertEquals(DictationBarModel.NOTHING_TRANSCRIBED, bar.warning)
    }

    @Test
    fun completedInsertionShowsConfirmationWithLatency() {
        val result = assertIs<DictationBarState.Result>(
            state(
                DictationPipelineStatus.Completed(
                    text = "Bom dia",
                    insertion = TextInsertionResult(true, "commitText", "ok"),
                    latencyMs = 1_100L
                )
            )
        )

        assertTrue(result.success)
        assertEquals("Inserido no campo ativo · 1,1 s", result.message)
    }

    @Test
    fun completedWithoutInsertionExplainsWhy() {
        val result = assertIs<DictationBarState.Result>(
            state(
                DictationPipelineStatus.Completed(
                    text = "Bom dia",
                    insertion = TextInsertionResult(false, "acessibilidade", "serviço inativo — nada inserido")
                )
            )
        )

        assertTrue(!result.success)
        assertEquals("Texto não inserido", result.message)
        assertEquals("serviço inativo — nada inserido", result.detail)
    }

    @Test
    fun noteSessionAdoptedByTheBubbleOffersStopInsteadOfInsert() {
        val recording = assertIs<DictationBarState.Live>(
            state(DictationPipelineStatus.Recording, target = DictationTarget.Note)
        )
        val transcribing = assertIs<DictationBarState.Live>(
            state(DictationPipelineStatus.Transcribing, target = DictationTarget.Note)
        )

        assertEquals(DictationTarget.Note, recording.destination)
        assertEquals(DictationBarModel.ROUTE_NOTE, recording.route)
        assertFalse(recording.canInsert)
        assertTrue(recording.canStop)
        assertFalse(transcribing.canInsert)
        assertFalse(transcribing.canStop)
    }

    @Test
    fun activeFieldSessionOffersInsertAndNeverStop() {
        val bar = assertIs<DictationBarState.Live>(state(DictationPipelineStatus.Recording))

        assertEquals(DictationTarget.ActiveField, bar.destination)
        assertTrue(bar.canInsert)
        assertFalse(bar.canStop)
    }

    @Test
    fun completedNoteSessionReportsTextSavedToTheNote() {
        val result = assertIs<DictationBarState.Result>(
            state(
                DictationPipelineStatus.Completed(
                    text = "Bom dia",
                    insertion = TextInsertionResult(false, "nota", "texto entregue à nota"),
                    warning = "Trecho 2 de 3 falhou (timeout): texto incompleto"
                ),
                target = DictationTarget.Note
            )
        )

        assertTrue(result.success)
        assertEquals(DictationBarModel.SAVED_TO_NOTE, result.message)
        assertEquals("Trecho 2 de 3 falhou (timeout): texto incompleto", result.detail)
    }

    @Test
    fun failureIsShownUntilDismissed() {
        val failed = DictationPipelineStatus.Failed("Nenhum trecho transcrito (sem rede)")

        val shown = assertIs<DictationBarState.Result>(state(failed))
        val dismissed = state(failed, dismissed = true)

        assertEquals("Falha no ditado", shown.message)
        assertEquals("Nenhum trecho transcrito (sem rede)", shown.detail)
        assertEquals(DictationBarState.Hidden, dismissed)
    }

    @Test
    fun cancelledAndIdleAreHidden() {
        assertEquals(DictationBarState.Hidden, state(DictationPipelineStatus.Cancelled))
        assertEquals(DictationBarState.Hidden, state(DictationPipelineStatus.Idle))
    }

    @Test
    fun clockAndLatencyFormatting() {
        assertEquals("0:00", DictationBarModel.clock(0L))
        assertEquals("0:09", DictationBarModel.clock(9_999L))
        assertEquals("1:05", DictationBarModel.clock(65_000L))
        assertEquals("1,1 s", DictationBarModel.latency(1_100L))
        assertEquals("1,0 s", DictationBarModel.latency(950L))
        assertEquals("0,0 s", DictationBarModel.latency(0L))
    }

    private fun state(
        status: DictationPipelineStatus,
        elapsedMs: Long = 0L,
        proofreading: Boolean = false,
        owned: Boolean = true,
        dismissed: Boolean = false,
        target: DictationTarget = DictationTarget.ActiveField
    ): DictationBarState = DictationBarModel.from(
        status = status,
        target = target,
        live = live,
        elapsedMs = elapsedMs,
        proofreadingEnabled = proofreading,
        owned = owned,
        dismissed = dismissed
    )
}
