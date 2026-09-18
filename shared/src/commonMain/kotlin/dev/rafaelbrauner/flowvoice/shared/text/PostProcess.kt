package dev.rafaelbrauner.flowvoice.shared.text

import dev.rafaelbrauner.flowvoice.shared.transcription.OutputLanguage
import kotlin.math.min

private val defaultFillerWords = setOf(
    "né", "tipo", "hum", "ah", "uh", "hã", "éé", "ahn", "ham", "bom", "sabe", "então"
)

fun normalizeTranscriptionOutput(text: String): String =
    text.replace(Regex("\\s+"), " ").trim()

fun removeFillerWords(
    text: String,
    custom: List<String>? = null,
    enabled: Boolean = true
): String {
    if (!enabled) return text
    val fillers = defaultFillerWords + (custom?.map { it.lowercase() }.orEmpty())
    return text.split(" ").filter { token ->
        val stripped = token.trim().trim(',', '.', '?', '!', ':', ';').lowercase()
        !(stripped in fillers && token.length <= stripped.length + 2)
    }.joinToString(" ")
}

fun applyCustomWords(
    text: String,
    words: List<String>,
    threshold: Double = 0.18
): String {
    if (words.isEmpty()) return text
    return text.split(" ").joinToString(" ") { token ->
        val stripped = token.trim().trim(',', '.', '?', '!', ':', ';')
        if (stripped.isEmpty()) return@joinToString token
        val prefix = token.substring(0, token.indexOf(stripped))
        val suffix = token.substring(token.indexOf(stripped) + stripped.length)
        val best = words.minByOrNull { levenshteinNormalized(stripped.lowercase(), it.lowercase()) }
        if (best != null && levenshteinNormalized(stripped.lowercase(), best.lowercase()) <= threshold) {
            prefix + matchCase(stripped, best) + suffix
        } else {
            token
        }
    }
}

fun detectOutputLanguage(text: String, requested: String): OutputLanguage {
    if (text.isBlank()) return OutputLanguage.UNKNOWN
    return when (baseOf(requested)) {
        "pt" -> OutputLanguage.PT
        "en" -> OutputLanguage.EN
        else -> OutputLanguage.UNKNOWN
    }
}

private fun baseOf(tag: String): String =
    tag.split('-', '_').firstOrNull().orEmpty().lowercase()

private fun matchCase(source: String, target: String): String = when {
    source.all { it.isUpperCase() } -> target.uppercase()
    source.firstOrNull()?.isUpperCase() == true -> target.replaceFirstChar { it.uppercase() }
    else -> target.lowercase()
}

internal fun levenshteinNormalized(a: String, b: String): Double {
    if (a == b) return 0.0
    if (a.isEmpty() || b.isEmpty()) return 1.0
    val maxLen = maxOf(a.length, b.length)
    val prev = IntArray(b.length + 1) { it }
    val curr = IntArray(b.length + 1)
    for (i in 1..a.length) {
        curr[0] = i
        for (j in 1..b.length) {
            curr[j] = min(
                min(prev[j] + 1, curr[j - 1] + 1),
                prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
            )
        }
        prev.indices.forEach { prev[it] = curr[it] }
    }
    return prev[b.length].toDouble() / maxLen
}
