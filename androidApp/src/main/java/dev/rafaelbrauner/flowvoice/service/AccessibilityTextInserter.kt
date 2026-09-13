package dev.rafaelbrauner.flowvoice.service

import dev.rafaelbrauner.flowvoice.shared.insertion.TextInserter
import dev.rafaelbrauner.flowvoice.shared.insertion.TextInsertionResult

object AccessibilityTextInserter : TextInserter {
    override val isAvailable: Boolean
        get() = FlowVoiceAccessibilityService.isRunning

    override fun insert(text: String): TextInsertionResult {
        val service = FlowVoiceAccessibilityService.service
            ?: return TextInsertionResult(false, "acessibilidade", "serviço inativo — nada inserido")
        val direct = service.insertDirect(text)
        val result = if (direct.success) direct else service.insertFallback(text)
        return TextInsertionResult(result.success, result.route, result.message)
    }
}
