package dev.rafaelbrauner.flowvoice.service

import dev.rafaelbrauner.flowvoice.shared.insertion.InsertionGuard
import dev.rafaelbrauner.flowvoice.shared.insertion.TextInserter
import dev.rafaelbrauner.flowvoice.shared.insertion.TextInsertionResult

object AccessibilityTextInserter : TextInserter {
    @Volatile
    private var startPackage: String? = null

    override val isAvailable: Boolean
        get() = FlowVoiceAccessibilityService.isRunning

    override fun captureTarget() {
        val service = FlowVoiceAccessibilityService.service
        startPackage = service?.focusedPackage()?.takeUnless { it == service.packageName }
    }

    override fun captureTargetIfUnknown() {
        if (startPackage == null) captureTarget()
    }

    override fun insert(text: String): TextInsertionResult {
        val service = FlowVoiceAccessibilityService.service
            ?: return TextInsertionResult(false, "acessibilidade", "serviço inativo — nada inserido")
        if (InsertionGuard.destinationChanged(startPackage, service.focusedPackage())) {
            return TextInsertionResult(false, "acessibilidade", InsertionGuard.DESTINATION_CHANGED_MESSAGE)
        }
        val ownPackage = service.packageName
        val direct = service.insertDirect(text, excludedPackage = ownPackage)
        val result = if (direct.success || direct.blocked) {
            direct
        } else {
            service.insertFallback(text, excludedPackage = ownPackage)
        }
        return TextInsertionResult(result.success, result.route, result.message)
    }
}
