package dev.rafaelbrauner.flowvoice.service

import dev.rafaelbrauner.flowvoice.shared.pipeline.InsertOutcome
import dev.rafaelbrauner.flowvoice.shared.pipeline.TextInserter

object AccessibilityTextInserter : TextInserter {
    override fun insert(text: String): InsertOutcome {
        val service = FlowVoiceAccessibilityService.service
            ?: return InsertOutcome(inserted = false, summary = "acessibilidade inativa — nada inserido")
        val direct = service.insertDirect(text)
        val result = if (direct.success) direct else service.insertFallback(text)
        return InsertOutcome(inserted = result.success, summary = result.summary)
    }
}
