package dev.rafaelbrauner.flowvoice.shared.insertion

object FocusedFieldDiagnostic {
    fun describe(
        className: CharSequence?,
        editable: Boolean,
        focused: Boolean,
        password: Boolean,
        textLength: Int
    ): String =
        "classe=${className ?: "?"}, editável=$editable, focado=$focused, senha=$password, tamanho=$textLength"
}
