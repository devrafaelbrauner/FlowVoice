package dev.rafaelbrauner.flowvoice.service

import dev.rafaelbrauner.flowvoice.service.FlowVoiceAccessibilityService.InsertResult
import dev.rafaelbrauner.flowvoice.shared.insertion.DirectInsertionGuard
import dev.rafaelbrauner.flowvoice.shared.insertion.InsertionGuard
import dev.rafaelbrauner.flowvoice.shared.insertion.TextInserter
import dev.rafaelbrauner.flowvoice.shared.insertion.TextInsertionResult

// O que o inseridor usa do serviço de acessibilidade: o suficiente para decidir o destino antes de
// tocar no campo, e para o teste trocar o serviço por um campo de mentira.
interface FocusedFieldAccess {
    val ownPackage: String
    fun focusedPackage(): String?
    fun currentInputGeneration(): Int?
    fun inputIsPassword(): Boolean
    fun textBeforeCursor(limit: Int): String?
    fun insertDirect(text: String, deleteBefore: Int = 0, excludedPackage: String? = null): InsertResult
    fun insertFallback(text: String, deleteBefore: Int = 0, excludedPackage: String? = null): InsertResult
    fun rewriteTail(text: String, deleteBefore: Int, excludedPackage: String): InsertResult?
}

object AccessibilityTextInserter : GuardedFieldInserter({ FlowVoiceAccessibilityService.service })

open class GuardedFieldInserter(private val field: () -> FocusedFieldAccess?) : TextInserter {
    @Volatile
    private var startPackage: String? = null

    // Geração de input em que a última inserção entrou; a próxima inserção sem toque exige a mesma (P139).
    @Volatile
    private var pinnedInput: Int? = null

    override val isAvailable: Boolean
        get() = field() != null

    override fun captureTarget() {
        val access = field()
        startPackage = access?.focusedPackage()?.takeUnless { it == access.ownPackage }
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
        val access = field() ?: return SERVICE_INACTIVE
        InsertionGuard.refusal(startPackage, access.focusedPackage(), access.ownPackage)?.let { reason ->
            return TextInsertionResult(false, "acessibilidade", reason)
        }
        return deliver(access, text)
    }

    override fun insertWithoutTap(text: String, deleteBefore: Int): TextInsertionResult {
        val access = field() ?: return SERVICE_INACTIVE
        directRefusal(access)?.let { refusal ->
            return TextInsertionResult(false, refusal.reason.route, refusal.message)
        }
        return deliver(access, text, deleteBefore)
    }

    // A leitura passa pelas mesmas travas da escrita sem toque, e antes delas nada é lido (Y1): outro
    // app, outro campo, o próprio FlowVoice (a chave OpenRouter) ou um campo de senha dão null — "não
    // sei" —, e quem ia apagar fica com a conta do app; a escrita logo depois é recusada do mesmo jeito.
    override fun readBeforeCursor(limit: Int): String? {
        val access = field() ?: return null
        if (directRefusal(access) != null || access.inputIsPassword()) return null
        return access.textBeforeCursor(limit)
    }

    // Rota atômica para refazer o fim do campo (P142): o ACTION_SET_TEXT troca o texto do campo num
    // passo só, sem instante nenhum com o texto apagado e sem depender da composição do teclado. As
    // travas da inserção sem toque (P139) continuam valendo, e o serviço só a usa quando o campo inteiro
    // está exposto e é texto simples (Y5); fora disso devolve null e o campo fica como está.
    override fun rewriteTail(deleteBefore: Int, text: String): TextInsertionResult? {
        val access = field() ?: return null
        directRefusal(access)?.let { refusal ->
            return TextInsertionResult(false, refusal.reason.route, refusal.message)
        }
        val result = access.rewriteTail(text, deleteBefore, excludedPackage = access.ownPackage) ?: return null
        if (result.success) pinnedInput = access.currentInputGeneration()
        return TextInsertionResult(result.success, result.route, result.message)
    }

    private fun directRefusal(access: FocusedFieldAccess): DirectInsertionGuard.Refusal? =
        DirectInsertionGuard.refusal(
            startPackage = startPackage,
            currentPackage = access.focusedPackage(),
            ownPackage = access.ownPackage,
            pinnedInput = pinnedInput,
            currentInput = access.currentInputGeneration()
        )

    private fun deliver(access: FocusedFieldAccess, text: String, deleteBefore: Int = 0): TextInsertionResult {
        val direct = access.insertDirect(text, deleteBefore, excludedPackage = access.ownPackage)
        val result = if (direct.success || direct.blocked) {
            direct
        } else {
            access.insertFallback(text, deleteBefore, excludedPackage = access.ownPackage)
        }
        if (result.success) pinnedInput = access.currentInputGeneration()
        return TextInsertionResult(result.success, result.route, result.message)
    }

    private companion object {
        val SERVICE_INACTIVE = TextInsertionResult(false, "acessibilidade", "serviço inativo — nada inserido")
    }
}
