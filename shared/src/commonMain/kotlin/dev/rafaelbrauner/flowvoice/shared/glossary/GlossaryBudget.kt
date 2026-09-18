package dev.rafaelbrauner.flowvoice.shared.glossary

data class GlossaryReport(
    val promptTokens: Int,
    val droppedTerms: Int
)

class GlossaryBudget(
    val maxTokens: Int = 224,
    val charsPerTokenPtBr: Double = 2.51
) {
    fun budget(terms: List<String>): Pair<String?, GlossaryReport> {
        if (terms.isEmpty()) return null to GlossaryReport(0, 0)
        val ranked = terms.sortedByDescending { it.length }
        val kept = mutableListOf<String>()
        var used = 0
        var dropped = 0
        for (term in ranked) {
            val cost = estimateTokens(term)
            if (used + cost <= maxTokens) {
                kept.add(term)
                used += cost
            } else {
                dropped += 1
            }
        }
        if (kept.isEmpty()) return null to GlossaryReport(0, terms.size)
        return kept.joinToString(", ") to GlossaryReport(used, dropped)
    }

    fun estimateTokens(text: String): Int =
        maxOf(1, (text.length / charsPerTokenPtBr).toInt() + 1)
}
