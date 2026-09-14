package dev.rafaelbrauner.flowvoice.shared.insertion

interface TextInserter {
    val isAvailable: Boolean

    fun captureTarget() = Unit

    fun insert(text: String): TextInsertionResult
}

data class TextInsertionResult(
    val success: Boolean,
    val route: String,
    val message: String
) {
    val summary: String
        get() = if (success) "[$route] $message" else "[$route] FALHOU — $message"
}
