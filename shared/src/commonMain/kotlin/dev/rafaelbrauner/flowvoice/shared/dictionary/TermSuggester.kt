package dev.rafaelbrauner.flowvoice.shared.dictionary

object TermSuggester {
    private val stop = setOf(
        "a", "o", "as", "os", "um", "uma", "de", "da", "do", "das", "dos",
        "e", "em", "no", "na", "nos", "nas", "para", "por", "com", "que",
        "se", "é", "está", "estão", "ao", "à", "às", "ou"
    )

    fun candidates(text: String): List<String> {
        val seen = linkedSetOf<String>()
        text.split(Regex("[^\\p{L}\\p{N}]+"))
            .map { it.trim() }
            .filter { it.length >= 4 }
            .filter { it.lowercase() !in stop }
            .forEach { token -> seen += token }
        return seen.toList()
    }
}
