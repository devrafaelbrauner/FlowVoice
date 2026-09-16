package dev.rafaelbrauner.flowvoice.service

import dev.rafaelbrauner.flowvoice.shared.insertion.DirectInsertionGuard
import dev.rafaelbrauner.flowvoice.shared.insertion.InsertionGuard
import dev.rafaelbrauner.flowvoice.shared.insertion.TextInserter
import dev.rafaelbrauner.flowvoice.shared.insertion.TextInsertionResult

object AccessibilityTextInserter : TextInserter {
    @Volatile
    private var startPackage: String? = null

    // Geração de input em que a última inserção entrou; a próxima inserção sem toque exige a mesma (P139).
    @Volatile
    private var pinnedInput: Int? = null

    override val isAvailable: Boolean
        get() = FlowVoiceAccessibilityService.isRunning

    override fun captureTarget() {
        val service = FlowVoiceAccessibilityService.service
        startPackage = service?.focusedPackage()?.takeUnless { it == service.packageName }
        pinnedInput = null
    }

    override fun captureTargetIfUnknown() {
        if (startPackage == null) captureTarget()
    }

    // O microfone do Início começa com o FlowVoice na frente; o destino é o app que ele reabre (P130).
    fun pinTarget(packageName: String) {
        startPackage = packageName
        pinnedInput = null
    }

    override fun insert(text: String): TextInsertionResult {
        val service = FlowVoiceAccessibilityService.service ?: return SERVICE_INACTIVE
        val ownPackage = service.packageName
        InsertionGuard.refusal(startPackage, service.focusedPackage(), ownPackage)?.let { reason ->
            return TextInsertionResult(false, "acessibilidade", reason)
        }
        return deliver(service, text)
    }

    override fun insertWithoutTap(text: String, deleteBefore: Int): TextInsertionResult {
        val service = FlowVoiceAccessibilityService.service ?: return SERVICE_INACTIVE
        DirectInsertionGuard.refusal(
            startPackage = startPackage,
            currentPackage = service.focusedPackage(),
            ownPackage = service.packageName,
            pinnedInput = pinnedInput,
            currentInput = service.currentInputGeneration()
        )?.let { refusal ->
            return TextInsertionResult(false, refusal.reason.route, refusal.message)
        }
        return deliver(service, text, deleteBefore)
    }

    override fun readBeforeCursor(limit: Int): String? =
        FlowVoiceAccessibilityService.service?.textBeforeCursor(limit)

    // Rota atômica para refazer o fim do campo (P142): o ACTION_SET_TEXT do `insertFallback` troca o
    // texto do campo num passo só, sem instante nenhum com o texto apagado e sem depender da
    // composição do teclado. As travas da inserção sem toque (P139) continuam valendo.
    override fun rewriteTail(deleteBefore: Int, text: String): TextInsertionResult? {
        val service = FlowVoiceAccessibilityService.service ?: return null
        DirectInsertionGuard.refusal(
            startPackage = startPackage,
            currentPackage = service.focusedPackage(),
            ownPackage = service.packageName,
            pinnedInput = pinnedInput,
            currentInput = service.currentInputGeneration()
        )?.let { refusal ->
            return TextInsertionResult(false, refusal.reason.route, refusal.message)
        }
        val result = service.insertFallback(text, deleteBefore, excludedPackage = service.packageName)
        if (result.success) pinnedInput = service.currentInputGeneration()
        return TextInsertionResult(result.success, result.route, result.message)
    }

    private fun deliver(
        service: FlowVoiceAccessibilityService,
        text: String,
        deleteBefore: Int = 0
    ): TextInsertionResult {
        val ownPackage = service.packageName
        val direct = service.insertDirect(text, deleteBefore, excludedPackage = ownPackage)
        val result = if (direct.success || direct.blocked) {
            direct
        } else {
            service.insertFallback(text, deleteBefore, excludedPackage = ownPackage)
        }
        if (result.success) pinnedInput = service.currentInputGeneration()
        return TextInsertionResult(result.success, result.route, result.message)
    }

    private val SERVICE_INACTIVE = TextInsertionResult(false, "acessibilidade", "serviço inativo — nada inserido")
}
