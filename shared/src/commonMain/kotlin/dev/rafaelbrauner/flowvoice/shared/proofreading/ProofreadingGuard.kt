package dev.rafaelbrauner.flowvoice.shared.proofreading

import kotlin.math.abs
import kotlin.math.min

// A revisão só pode mexer em pontuação, maiúsculas, acentos e ortografia (P132). A comparação é
// por palavras, em minúsculas e sem acento: a quantidade de palavras não muda, números e palavras
// curtas ficam idênticos, e só palavras de 5 letras ou mais podem mudar, com até 2 edições. Assim
// uma dose trocada, um "não" removido, "direito" por "esquerdo" ou uma resposta descartam a revisão
// em qualquer tamanho de texto. Aspas, dois-pontos, quebras de linha e marcas <ditado> que o ditado
// não tinha também descartam.
object ProofreadingGuard {
    private const val MIN_EDITABLE_WORD = 5
    private const val MAX_WORD_EDITS = 2
    private val NEW_SIGNALS = setOf('"', '“', '”', '„', '«', '»', '‘', '’', '\'', ':', '\n')

    fun accepts(original: String, revised: String): Boolean {
        if (revised.contains("<ditado", ignoreCase = true) || revised.contains("</ditado", ignoreCase = true)) return false
        if (NEW_SIGNALS.any { it in revised && it !in original }) return false
        val source = words(original)
        val target = words(revised)
        if (source.size != target.size) return false
        return source.indices.all { wordAccepted(source[it], target[it]) }
    }

    private fun wordAccepted(original: String, revised: String): Boolean = when {
        original == revised -> true
        original.any { it.isDigit() } || revised.any { it.isDigit() } -> false
        min(original.length, revised.length) < MIN_EDITABLE_WORD -> false
        else -> editDistanceWithin(original, revised, MAX_WORD_EDITS)
    }

    private fun words(text: String): List<String> {
        val words = mutableListOf<String>()
        val current = StringBuilder()
        text.lowercase().forEach { char ->
            val plain = ACCENTS[char] ?: char
            if (plain.isLetterOrDigit()) {
                current.append(plain)
            } else if (current.isNotEmpty()) {
                words += current.toString()
                current.clear()
            }
        }
        if (current.isNotEmpty()) words += current.toString()
        return words
    }

    private fun editDistanceWithin(a: String, b: String, budget: Int): Boolean {
        if (abs(a.length - b.length) > budget) return false
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(substitution, min(previous[j] + 1, current[j - 1] + 1))
            }
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
