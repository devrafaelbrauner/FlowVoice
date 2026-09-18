package dev.rafaelbrauner.flowvoice.shared.glossary

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GlossaryBudgetTest {

    @Test
    fun emptyTermsProduceNullPrompt() {
        val (prompt, report) = GlossaryBudget().budget(emptyList())
        assertNull(prompt)
        assertTrue(report.promptTokens == 0)
    }

    @Test
    fun smallListFitsEntirely() {
        val (prompt, report) = GlossaryBudget().budget(listOf("dipiridamol", "abdome"))
        assertNotNull(prompt)
        assertTrue(report.droppedTerms == 0)
        assertTrue(report.promptTokens <= 224)
    }

    @Test
    fun largeListIsCutAtBudget() {
        val terms = (1..602).map { "termo_medico_$it" }
        val (prompt, report) = GlossaryBudget().budget(terms)
        assertNotNull(prompt)
        assertTrue(report.promptTokens <= 224)
        assertTrue(report.droppedTerms > 0)
    }

    @Test
    fun longestTermsWinBudget() {
        val (prompt, _) = GlossaryBudget(maxTokens = 10).budget(
            listOf("ab", "dipiridamol")
        )
        assertNotNull(prompt)
        assertTrue(prompt.contains("dipiridamol"))
    }
}
