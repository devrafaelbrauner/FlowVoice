package dev.rafaelbrauner.flowvoice.shared.insertion

interface TextInserter {
    val isAvailable: Boolean

    fun captureTarget() = Unit

    fun captureTargetIfUnknown() = Unit

    fun insert(text: String): TextInsertionResult

    // Inserção sem toque do usuário (modo direto, P139). No Android passa pelo DirectInsertionGuard.
    // `deleteBefore` apaga caracteres logo antes do cursor antes de escrever (P144): só o ponto que o
    // próprio FlowVoice inseriu. Quem não souber apagar ignora, e o ponto sobra.
    fun insertWithoutTap(text: String, deleteBefore: Int = 0): TextInsertionResult = insert(text)
}

data class TextInsertionResult(
    val success: Boolean,
    val route: String,
    val message: String
) {
    val summary: String
        get() = if (success) "[$route] $message" else "[$route] FALHOU — $message"
}
