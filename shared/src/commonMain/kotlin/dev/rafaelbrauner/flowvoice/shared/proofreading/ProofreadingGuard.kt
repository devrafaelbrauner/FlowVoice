package dev.rafaelbrauner.flowvoice.shared.proofreading

import dev.rafaelbrauner.flowvoice.shared.text.EditDistance
import dev.rafaelbrauner.flowvoice.shared.text.OnsetContrast
import kotlin.math.abs
import kotlin.math.min

// A revisão só pode mexer em pontuação, maiúsculas, acentos e ortografia (P132). A comparação é
// por palavras, em minúsculas e sem acento: a quantidade de palavras não muda, números e palavras
// curtas ficam idênticos, e só palavras de 5 letras ou mais podem mudar, com até 2 edições. Assim
// uma dose trocada, um "não" removido, "direito" por "esquerdo" ou uma resposta descartam a revisão
// em qualquer tamanho de texto. Aspas, dois-pontos, quebras de linha e marcas <ditado> que o ditado
// não tinha também descartam.
// R2 (revisão de código): distância de edição sozinha aceitava "hipertensão"→"hipotensão",
// "normal"→"anormal", "prednisona"→"prednisolona", "sessenta"→"setenta", "Nega alergias?" e
// "Não, tomou". Agora: diferença nas primeiras 4 letras é outra palavra (P156), prefixo de negação
// posto ou tirado e hiper/hipo também, número por extenso fica idêntico como dígito, 2+ letras a
// mais ou a menos é outra palavra, "?" novo descarta e pontuação nova logo depois de uma negação
// (não, nem, nega, sem) descarta.
object ProofreadingGuard {
    private const val MIN_EDITABLE_WORD = 5
    private const val MAX_WORD_EDITS = 2
    private const val MAX_LENGTH_CHANGE = 1
    private val NEW_SIGNALS = setOf('"', '“', '”', '„', '«', '»', '‘', '’', '\'', ':', '\n', '?', '¿')
    private val NEGATIONS = setOf("nao", "nem", "nega", "sem")
    private val NEGATING_PREFIXES = listOf("a", "an", "in", "im", "ir", "des", "as")
    private val NUMBER_WORDS = setOf(
        "um", "uma", "dois", "duas", "tres", "quatro", "cinco", "seis", "sete", "oito", "nove", "dez",
        "onze", "doze", "treze", "catorze", "quatorze", "quinze", "dezesseis", "dezessete", "dezoito",
        "dezenove", "vinte", "trinta", "quarenta", "cinquenta", "sessenta", "setenta", "oitenta",
        "noventa", "cem", "cento", "duzentos", "duzentas", "trezentos", "trezentas", "quatrocentos",
        "quatrocentas", "quinhentos", "quinhentas", "seiscentos", "seiscentas", "setecentos",
        "setecentas", "oitocentos", "oitocentas", "novecentos", "novecentas", "mil", "meio", "meia",
        "primeiro", "primeira", "segundo", "segunda", "terceiro", "terceira", "dobro", "metade"
    )

    fun accepts(original: String, revised: String): Boolean {
        if (revised.contains("<ditado", ignoreCase = true) || revised.contains("</ditado", ignoreCase = true)) return false
        if (hasForbiddenNewSignal(revised, original)) return false
        val source = words(original)
        val target = words(revised)
        if (source.size != target.size) return false
        if (!source.indices.all { wordAccepted(source[it], target[it]) }) return false
        return !hasNewPunctuationAfterNegation(original, revised)
    }

    fun isNegation(word: String): Boolean = normalize(word) in NEGATIONS

    // Pontuação no vão que o vão do ditado não tinha: depois de uma negação, "Não tomou" →
    // "Não, tomou" inverte a frase.
    fun addsPunctuation(originalGap: String, revisedGap: String): Boolean =
        revisedGap.any { !it.isWhitespace() && it !in originalGap }

    private fun hasNewPunctuationAfterNegation(original: String, revised: String): Boolean {
        val source = wordsWithGaps(original)
        val target = wordsWithGaps(revised)
        // O vão final fica livre: um ponto depois de "não" no fim da frase não muda o sentido.
        return (0 until source.lastIndex).any { index ->
            source[index].first in NEGATIONS && addsPunctuation(source[index].second, target[index].second)
        }
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
        original in NUMBER_WORDS || revised in NUMBER_WORDS -> false
        min(original.length, revised.length) < MIN_EDITABLE_WORD -> false
        abs(original.length - revised.length) > MAX_LENGTH_CHANGE -> false
        OnsetContrast.has(original, revised, MIN_EDITABLE_WORD) -> false
        togglesNegatingPrefix(original, revised) -> false
        swapsHiperHipo(original, revised) -> false
        else -> EditDistance.within(original, revised, MAX_WORD_EDITS)
    }

    private fun togglesNegatingPrefix(a: String, b: String): Boolean {
        val (short, long) = if (a.length <= b.length) a to b else b to a
        return NEGATING_PREFIXES.any { long == it + short }
    }

    private fun swapsHiperHipo(a: String, b: String): Boolean =
        (a.startsWith("hiper") && b.startsWith("hipo")) || (a.startsWith("hipo") && b.startsWith("hiper"))

    // Palavra (normalizada) e o vão até a próxima, como estão no texto.
    private fun wordsWithGaps(text: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val word = StringBuilder()
        val gap = StringBuilder()
        text.forEach { char ->
            if (char.isLetterOrDigit()) {
                if (gap.isNotEmpty() || word.isEmpty()) {
                    if (word.isNotEmpty()) result += normalize(word.toString()) to gap.toString()
                    word.clear()
                    gap.clear()
                }
                word.append(char)
            } else if (word.isNotEmpty()) {
                gap.append(char)
            }
        }
        if (word.isNotEmpty()) result += normalize(word.toString()) to gap.toString()
        return result
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
