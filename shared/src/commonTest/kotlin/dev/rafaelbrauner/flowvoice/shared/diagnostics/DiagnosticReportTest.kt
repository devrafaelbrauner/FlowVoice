package dev.rafaelbrauner.flowvoice.shared.diagnostics

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticReportTest {
    @Test
    fun exportDoesNotIncludeSecrets() {
        val report = DiagnosticReport.build(
            generatedAtMs = 1L,
            accessibility = true,
            microphoneGranted = true,
            keyConfigured = true,
            signedIn = false,
            proofreading = true,
            model = "openai/gpt-4o-mini",
            notes = 2,
            terms = 1,
            overlay = false
        )
        val text = report.asText()
        assertTrue(text.contains("chave_openrouter=sim"))
        assertFalse(text.contains("sk-"))
        assertFalse(text.contains("Bearer"))
    }
}
