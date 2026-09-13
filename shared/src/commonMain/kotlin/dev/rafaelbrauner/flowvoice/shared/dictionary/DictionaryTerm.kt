package dev.rafaelbrauner.flowvoice.shared.dictionary

data class DictionaryTerm(
    val surface: String,
    val approved: Boolean
) {
    init {
        require(surface.isNotBlank()) { "surface must not be blank" }
    }
}
