package dev.rafaelbrauner.flowvoice.shared.proofreading

import kotlin.math.max
import kotlin.math.min

// A revisão só pode mexer em pontuação, maiúsculas, acentos e ortografia (P132). Comparando o
// esqueleto dos dois textos (só letras e dígitos, minúsculos e sem acento), uma correção
// ortográfica custa uma ou duas edições; uma palavra trocada, uma resposta ou uma reescrita custam
// mais que o limite e a revisão é descartada. Aspas que o ditado não tinha também descartam.
object ProofreadingGuard {
    private const val MIN_EDIT_BUDGET = 2
    private const val CHARS_PER_EXTRA_EDIT = 20
    private val QUOTES = setOf('"', '“', '”', '„', '«', '»')

    fun accepts(original: String, revised: String): Boolean {
        if (revised.any { it in QUOTES } && original.none { it in QUOTES }) return false
        val source = skeleton(original)
        val target = skeleton(revised)
        val budget = max(MIN_EDIT_BUDGET, source.length / CHARS_PER_EXTRA_EDIT)
        return editDistanceWithin(source, target, budget)
    }

    private fun skeleton(text: String): String = buildString {
        text.lowercase().forEach { char ->
            val plain = ACCENTS[char] ?: char
            if (plain.isLetterOrDigit()) append(plain)
        }
    }

    private fun editDistanceWithin(a: String, b: String, budget: Int): Boolean {
        if (kotlin.math.abs(a.length - b.length) > budget) return false
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(substitution, min(previous[j] + 1, current[j - 1] + 1))
                rowMin = min(rowMin, current[j])
            }
            if (rowMin > budget) return false
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length] <= budget
    }

    private val ACCENTS: Map<Char, Char> = buildMap {
        "áàâãä".forEach { put(it, 'a') }
        "éèêë".forEach { put(it, 'e') }
        "íìîï".forEach { put(it, 'i') }
        "óòôõö".forEach { put(it, 'o') }
        "úùûü".forEach { put(it, 'u') }
        put('ç', 'c')
        put('ñ', 'n')
    }
}
