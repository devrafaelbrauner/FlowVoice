package dev.rafaelbrauner.flowvoice.ui.screens.home

import dev.rafaelbrauner.flowvoice.shared.localasr.TranscriptionEngine
import dev.rafaelbrauner.flowvoice.shared.localasr.TranscriptionEngineSelection
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineSession
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipelineStatus
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeLogicTest {

    private fun action(
        accessibilityRunning: Boolean = true,
        microphoneGranted: Boolean = true,
        keyConfigured: Boolean = true,
        engine: TranscriptionEngine = TranscriptionEngine.Cloud,
        sdkInt: Int = 36,
        canDrawOverlays: Boolean = true
    ) = micAction(
        accessibilityRunning,
        microphoneGranted,
        TranscriptionEngineSelection.canDictate(engine, keyConfigured),
        sdkInt,
        canDrawOverlays
    )

    @Test
    fun missingAccessibilityOrMicrophoneOpensOnboarding() {
        assertEquals(MicAction.OpenOnboarding, action(accessibilityRunning = false))
        assertEquals(MicAction.OpenOnboarding, action(microphoneGranted = false))
        assertEquals(MicAction.OpenOnboarding, action(microphoneGranted = false, keyConfigured = false))
    }

    @Test
    fun missingKeyIsReportedBeforeStartingOrAskingForTheOverlay() {
        assertEquals(MicAction.ConfigureKey, action(keyConfigured = false))
        assertEquals(MicAction.ConfigureKey, action(keyConfigured = false, canDrawOverlays = false))
    }

    @Test
    fun onDeviceModelInUseStartsDictationWithoutKey() {
        assertEquals(MicAction.StartDictation, action(keyConfigured = false, engine = TranscriptionEngine.Local))
        assertEquals(
            MicAction.RequestOverlayPermission,
            action(keyConfigured = false, engine = TranscriptionEngine.Local, canDrawOverlays = false)
        )
    }

    @Test
    fun androidSevenCannotShowTheOverlay() {
        assertEquals(MicAction.OverlayUnsupported, action(sdkInt = 25))
    }

    @Test
    fun missingOverlayPermissionAsksForIt() {
        assertEquals(MicAction.RequestOverlayPermission, action(canDrawOverlays = false))
    }

    @Test
    fun readyDeviceStartsDictation() {
        assertEquals(MicAction.StartDictation, action(sdkInt = 26))
    }

    @Test
    fun micWhileANoteIsBeingDictatedOpensThatNote() {
        assertEquals("n1", noteToResumeOnMic(pipelineBusy = true, activeNoteId = "n1"))
        assertNull(noteToResumeOnMic(pipelineBusy = true, activeNoteId = null))
        assertNull(noteToResumeOnMic(pipelineBusy = false, activeNoteId = "n1"))
    }

    @Test
    fun micTapOutcomeIsTheFirstRecordingOrFailureOfANewerSessionEvenIfEqualToThePrevious() {
        val failed = DictationPipelineStatus.Failed("chave OpenRouter ausente ou inválida")
        val previous = DictationPipelineSession(1, DictationTarget.ActiveField, failed)

        assertNull(startOutcome(previous, previous))
        assertNull(startOutcome(previous, DictationPipelineSession(2, DictationTarget.ActiveField, DictationPipelineStatus.Starting)))
        assertEquals(failed, startOutcome(previous, DictationPipelineSession(2, DictationTarget.ActiveField, failed)))
        assertEquals(
            DictationPipelineStatus.Recording,
            startOutcome(previous, DictationPipelineSession(2, DictationTarget.ActiveField, DictationPipelineStatus.Recording))
        )
    }

    @Test
    fun snippetSkipsLineRepeatedAsTitle() {
        assertEquals(
            "Fechamos o escopo da fase 2.",
            noteSnippet("Reunião de orçamento", "Reunião de orçamento\n\nFechamos o escopo da fase 2.")
        )
    }

    @Test
    fun snippetUsesBodyWhenTitleWasWrittenSeparately() {
        assertEquals("Rodar o corpus pt-BR", noteSnippet("Ideias para o F05", "Rodar o corpus pt-BR\nmedir WER"))
    }

    @Test
    fun singleLineNoteHasNoSnippet() {
        assertEquals("", noteSnippet("Lista do mercado", "Lista do mercado"))
        assertEquals("", noteSnippet("Sem título", "  "))
    }
}
