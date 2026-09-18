package dev.rafaelbrauner.flowvoice.shared.text

import dev.rafaelbrauner.flowvoice.shared.transcription.OutputLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostProcessTest {

    @Test
    fun normalizeCollapsesWhitespace() {
        assertEquals("oi tudo bem", normalizeTranscriptionOutput("  oi   tudo\nbem "))
    }

    @Test
    fun fillerWordsRemovedWithoutEatingRealWords() {
        val result = removeFillerWords("eu né fui tipo ao médico hum ontem")
        assertTrue("médico" in result)
        assertTrue("ontem" in result)
        assertTrue(" né " !in " $result ")
    }

    @Test
    fun fillerRemovalCanBeDisabled() {
        val text = "eu né fui"
        assertEquals(text, removeFillerWords(text, enabled = false))
    }

    @Test
    fun customWordFixesOneEditError() {
        val result = applyCustomWords("tomar dipiridamo hoje", listOf("dipiridamol"))
        assertTrue("dipiridamol" in result)
    }

    @Test
    fun highThresholdDoesNotCorrect() {
        val result = applyCustomWords("tomar xyz hoje", listOf("dipiridamol"), threshold = 0.01)
        assertEquals("tomar xyz hoje", result)
    }

    @Test
    fun detectsPtAndUnknown() {
        assertEquals(OutputLanguage.PT, detectOutputLanguage("bom dia", "pt"))
        assertEquals(OutputLanguage.UNKNOWN, detectOutputLanguage("", "pt"))
    }
}
