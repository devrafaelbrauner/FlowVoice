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

    // Texto que está de fato logo antes do cursor, até `limit` caracteres (P148). Quem não souber ler
    // o campo devolve null, e quem for apagar fica com a conta do próprio app.
    fun readBeforeCursor(limit: Int): String? = null

    // Refaz o fim do campo num passo só (P142): apaga `deleteBefore` caracteres antes do cursor e
    // escreve `text` sem nenhum instante com o texto apagado no meio do caminho — no Android é o
    // ACTION_SET_TEXT, que troca o texto do campo inteiro de uma vez e não depende da composição do
    // teclado. Quem não tiver essa rota devolve null, e nada é refeito.
    fun rewriteTail(deleteBefore: Int, text: String): TextInsertionResult? = null
}

data class TextInsertionResult(
    val success: Boolean,
    val route: String,
    val message: String
) {
    val summary: String
        get() = if (success) "[$route] $message" else "[$route] FALHOU — $message"
}
