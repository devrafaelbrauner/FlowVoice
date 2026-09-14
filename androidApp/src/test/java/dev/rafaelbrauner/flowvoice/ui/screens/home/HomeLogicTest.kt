package dev.rafaelbrauner.flowvoice.ui.screens.home

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeLogicTest {

    @Test
    fun missingAccessibilityOrMicrophoneOpensOnboarding() {
        assertEquals(MicAction.OpenOnboarding, micAction(false, true, 36, true))
        assertEquals(MicAction.OpenOnboarding, micAction(true, false, 36, true))
    }

    @Test
    fun androidSevenCannotShowTheOverlay() {
        assertEquals(MicAction.OverlayUnsupported, micAction(true, true, 25, true))
    }

    @Test
    fun missingOverlayPermissionAsksForIt() {
        assertEquals(MicAction.RequestOverlayPermission, micAction(true, true, 36, false))
    }

    @Test
    fun readyDeviceStartsDictation() {
        assertEquals(MicAction.StartDictation, micAction(true, true, 26, true))
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
