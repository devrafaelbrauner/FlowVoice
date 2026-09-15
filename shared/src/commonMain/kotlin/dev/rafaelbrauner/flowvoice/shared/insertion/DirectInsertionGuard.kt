package dev.rafaelbrauner.flowvoice.shared.insertion

// Trava da inserção sem toque do usuário (modo direto, P139), mais rígida que o InsertionGuard: sem
// destino conhecido nada é digitado, e depois da primeira inserção o input (campo) tem de ser o mesmo.
object DirectInsertionGuard {
    enum class Reason(val route: String, val message: String) {
        OwnApp("trava:flowvoice", "o FlowVoice está em primeiro plano; abra o app de destino e toque em Inserir aqui"),
        UnknownDestination("trava:destino", "destino não confirmado; abra o campo e toque em Inserir aqui"),
        AppChanged("trava:app", "o foco mudou de app; toque em Inserir aqui para escrever no app atual"),
        FieldChanged("trava:campo", "o campo mudou; toque em Inserir aqui para escrever no campo atual")
    }

    data class Refusal(val reason: Reason) {
        val message: String
            get() = reason.message
    }

    fun refusal(
        startPackage: String?,
        currentPackage: String?,
        ownPackage: String,
        pinnedInput: Int?,
        currentInput: Int?
    ): Refusal? {
        val reason = when {
            currentPackage == ownPackage -> Reason.OwnApp
            startPackage == null || currentPackage == null -> Reason.UnknownDestination
            startPackage != currentPackage -> Reason.AppChanged
            pinnedInput != null && currentInput != pinnedInput -> Reason.FieldChanged
            else -> return null
        }
        return Refusal(reason)
    }
}
