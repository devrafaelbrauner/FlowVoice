package dev.rafaelbrauner.flowvoice.shared.dictionary

interface PersonalDictionary {
    fun approved(): List<DictionaryTerm>
    fun pending(): List<DictionaryTerm>
    fun corrections(): List<CorrectionPair>
    fun approve(surface: String)
    fun reject(surface: String)
    fun learnCorrection(wrong: String, right: String)
    fun forgetCorrection(wrong: String)
    fun suggestFrom(text: String): List<String>
    fun revision(): Long
    fun apply(text: String): String
}

// Uma correção ensinada pelo usuário: trocar a palavra inteira `wrong` por `right`. A regra vale
// antes de qualquer casamento por som (Item 1 da revisão do motor) — o usuário apontou qual é a
// palavra certa, então isso decide sobre a inferência fonética.
data class CorrectionPair(
    val wrong: String,
    val right: String
)