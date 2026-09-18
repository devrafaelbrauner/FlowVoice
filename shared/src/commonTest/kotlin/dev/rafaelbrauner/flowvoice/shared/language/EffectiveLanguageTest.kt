package dev.rafaelbrauner.flowvoice.shared.language

import kotlin.test.Test
import kotlin.test.assertEquals

class EffectiveLanguageTest {

    @Test
    fun ptBrResolvesToPt() {
        assertEquals("pt", effectiveLanguage("pt-BR", listOf("pt", "en"), false))
    }

    @Test
    fun autoWithDetectionStaysAuto() {
        assertEquals("auto", effectiveLanguage("auto", listOf("pt", "en"), true))
    }

    @Test
    fun autoWithoutDetectionFallsBackToPt() {
        assertEquals("pt", effectiveLanguage("auto", listOf("pt", "es"), false))
    }

    @Test
    fun autoWithoutDetectionNorPtFallsBackToEnglish() {
        assertEquals("en", effectiveLanguage("auto", listOf("en", "es"), false))
    }

    @Test
    fun zhHansPassthrough() {
        assertEquals("zh-Hans", effectiveLanguage("zh-Hans", listOf("zh", "en"), true))
    }

    @Test
    fun norwegianAliasResolves() {
        assertEquals("no", effectiveLanguage("nb", listOf("no", "en"), false))
    }

    @Test
    fun emptySupportedKeepsIntent() {
        assertEquals("pt", effectiveLanguage("pt", emptyList(), false))
    }
}
