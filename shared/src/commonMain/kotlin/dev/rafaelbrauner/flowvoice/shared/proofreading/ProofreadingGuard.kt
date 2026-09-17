package dev.rafaelbrauner.flowvoice.shared.proofreading

import dev.rafaelbrauner.flowvoice.shared.text.EditDistance
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
        if (hasForbiddenNewSignal(revised, original)) return false
        val source = words(original)
        val target = words(revised)
        if (source.size != target.size) return false
        return source.indices.all { wordAccepted(source[it], target[it]) }
    }

    // A mesma regra para uma palavra só (P149), para misturar a revisão palavra a palavra em vez de
    // descartá-la inteira por causa de uma. Recebe as palavras como estão no texto.
    fun acceptsWord(original: String, revised: String): Boolean =
        wordAccepted(normalize(original), normalize(revised))

    // S26 (2026-09-17 12:46): a revisão pôs "manhã:" e o guard derrubou o texto inteiro, inclusive a
    // vírgula que estava certa. A mistura tira só o sinal novo; o guard continua recusando o texto cru.
    fun stripForbiddenNewSignals(text: String, original: String): String = buildString {
        text.forEach { char ->
            if (char in NEW_SIGNALS && char !in original) return@forEach
            append(char)
        }
    }

    fun hasForbiddenNewSignal(text: String, original: String): Boolean =
        NEW_SIGNALS.any { it in text && it !in original }

    private fun normalize(word: String): String = buildString {
        word.lowercase().forEach { char -> append(ACCENTS[char] ?: char) }
    }

    private fun wordAccepted(original: String, revised: String): Boolean = when {
        original == revised -> true
        original.any { it.isDigit() } || revised.any { it.isDigit() } -> false
        min(original.length, revised.length) < MIN_EDITABLE_WORD -> false
        else -> EditDistance.within(original, revised, MAX_WORD_EDITS)
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
