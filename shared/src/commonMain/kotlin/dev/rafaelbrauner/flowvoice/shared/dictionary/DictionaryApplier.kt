package dev.rafaelbrauner.flowvoice.shared.dictionary

object DictionaryApplier {
    fun apply(text: String, approved: Collection<String>): String {
        if (text.isBlank() || approved.isEmpty()) return text
        val byKey = approved
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .associateBy { it.lowercase() }
        if (byKey.isEmpty()) return text
        val pattern = Regex("\\p{L}[\\p{L}\\p{N}]*")
        return pattern.replace(text) { match ->
            byKey[match.value.lowercase()] ?: match.value
        }
    }
}
