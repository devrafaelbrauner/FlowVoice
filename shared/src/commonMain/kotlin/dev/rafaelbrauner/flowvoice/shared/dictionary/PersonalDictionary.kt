package dev.rafaelbrauner.flowvoice.shared.dictionary

interface PersonalDictionary {
    fun approved(): List<DictionaryTerm>
    fun pending(): List<DictionaryTerm>
    fun approve(surface: String)
    fun reject(surface: String)
    fun suggestFrom(text: String): List<String>
    fun apply(text: String): String
}
