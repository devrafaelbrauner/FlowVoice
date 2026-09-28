package dev.rafaelbrauner.flowvoice.shared.proofreading

import dev.rafaelbrauner.flowvoice.shared.text.EditDistance
import dev.rafaelbrauner.flowvoice.shared.text.OnsetContrast
import kotlin.math.abs
import kotlin.math.min

// A revisão só pode mexer em pontuação, maiúsculas, acentos, ortografia e na terminação das palavras
// (P132, concordância). A comparação é por palavras, em minúsculas e sem acento: a quantidade de
// palavras não muda, números ficam idênticos e uma palavra só muda (a) com até 2 edições, se tem 5
// letras ou mais (ortografia), ou (b) com o mesmo radical de 4 letras ou mais e só o fim diferente,
// até 3 letras de cada lado ou "-ou/-aram", "-eu/-eram" (concordância: "nomeados"→"nomeado",
// "está"→"estão", "melhorou"→"melhoraram"), ou (c) num par da
// lista fechada de verbos ("foram"→"foi", "é"→"são"). Assim uma dose trocada, um "não" removido,
// "direito" por "esquerdo" ou uma resposta descartam a revisão em qualquer tamanho de texto. Aspas,
// dois-pontos, quebras de linha e marcas <ditado> que o ditado não tinha também descartam.
// R2 (revisão de código): distância de edição sozinha aceitava "hipertensão"→"hipotensão",
// "normal"→"anormal", "prednisona"→"prednisolona", "sessenta"→"setenta", "Nega alergias?" e
// "Não, tomou". Agora: diferença nas primeiras 4 letras é outra palavra (P156), prefixo de negação
// posto ou tirado e hiper/hipo também, número por extenso fica idêntico como dígito, 2+ letras a
// mais ou a menos é outra palavra (fora da terminação), "?" novo descarta e pontuação nova logo
// depois de uma negação (não, nem, nega, sem) descarta. A terminação não abre nenhuma dessas portas:
// as regras de número, negação e hiper/hipo vêm antes, e "prednisona"→"prednisolona" muda 5 letras
// depois do radical.
object ProofreadingGuard {
    private const val MIN_EDITABLE_WORD = 5
    private const val MAX_WORD_EDITS = 2
    private const val MAX_LENGTH_CHANGE = 1
    // Concordância (P157): palavra de 4 letras ou mais, radical comum de 4 ou mais e só o fim diferente,
    // até 3 letras de cada lado. Radical de 3 aceitaria "caso"→"casa" e "mesa"→"mesma".
    private const val MIN_AGREEMENT_WORD = 4
    private const val MIN_AGREEMENT_STEM = 4
    private const val MAX_AGREEMENT_ENDING = 3
    // O pretérito da 3ª pessoa muda 4 letras no plural ("melhorou"→"melhoraram", "recebeu"→"receberam").
    // Só esses pares passam do limite: "negativo"→"negação" também muda 4 e é outra palavra.
    private val PRETERITE_ENDINGS = setOf(setOf("ou", "aram"), setOf("eu", "eram"))
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
    // Verbos cuja concordância muda o radical (ou é curta demais para a regra da terminação), singular e
    // plural. Comparados com acento: "é" (verbo) e "e" (conjunção) não são a mesma palavra.
    private val VERB_AGREEMENT: Set<Set<String>> = listOf(
        "é" to "são", "foi" to "foram", "está" to "estão", "era" to "eram", "estava" to "estavam",
        "tem" to "têm", "vem" to "vêm", "teve" to "tiveram", "fez" to "fizeram", "pode" to "podem",
        "vai" to "vão", "ficou" to "ficaram", "deu" to "deram", "veio" to "vieram", "esteve" to "estiveram",
        "disse" to "disseram", "pôs" to "puseram", "quis" to "quiseram", "dá" to "dão", "vê" to "veem",
        "sai" to "saem"
    ).map { (singular, plural) -> setOf(singular, plural) }.toSet()

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
    fun acceptsWord(original: String, revised: String): Boolean = wordEdit(original, revised) != WordEdit.Refused

    fun wordEdit(original: String, revised: String): WordEdit = classify(original.lowercase(), revised.lowercase())

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

    private fun wordAccepted(dictated: String, proposed: String): Boolean = classify(dictated, proposed) != WordEdit.Refused

    // Recebe as palavras em minúsculas, com acento (a lista de verbos precisa dele).
    private fun classify(dictated: String, proposed: String): WordEdit {
        val original = normalize(dictated)
        val revised = normalize(proposed)
        return when {
            original == revised -> WordEdit.Same
            original.any { it.isDigit() } || revised.any { it.isDigit() } -> WordEdit.Refused
            original in NUMBER_WORDS || revised in NUMBER_WORDS -> WordEdit.Refused
            togglesNegatingPrefix(original, revised) -> WordEdit.Refused
            swapsHiperHipo(original, revised) -> WordEdit.Refused
            // Antes da ortografia: "nomeados"→"nomeado" cabe nas duas, e é concordância (P157).
            changesOnlyTheEnding(original, revised) || setOf(dictated, proposed) in VERB_AGREEMENT -> WordEdit.Agreement
            fixesSpelling(original, revised) -> WordEdit.Spelling
            else -> WordEdit.Refused
        }
    }

    private fun fixesSpelling(original: String, revised: String): Boolean =
        min(original.length, revised.length) >= MIN_EDITABLE_WORD &&
            abs(original.length - revised.length) <= MAX_LENGTH_CHANGE &&
            !OnsetContrast.has(original, revised, MIN_EDITABLE_WORD) &&
            EditDistance.within(original, revised, MAX_WORD_EDITS)

    private fun changesOnlyTheEnding(original: String, revised: String): Boolean {
        if (min(original.length, revised.length) < MIN_AGREEMENT_WORD) return false
        val stem = original.commonPrefixWith(revised).length
        if (stem < MIN_AGREEMENT_STEM) return false
        val endings = setOf(original.substring(stem), revised.substring(stem))
        return endings.all { it.length <= MAX_AGREEMENT_ENDING } || endings in PRETERITE_ENDINGS
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

    // Palavras em minúsculas, como estão no texto (com acento); a separação ignora o acento.
    private fun words(text: String): List<String> {
        val words = mutableListOf<String>()
        val current = StringBuilder()
        text.lowercase().forEach { char ->
            val plain = ACCENTS[char] ?: char
            if (plain.isLetterOrDigit()) {
                current.append(char)
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

// O que a revisão fez com uma palavra: nada (só acento/maiúscula), ortografia, concordância (só a
// terminação, ou um par da lista de verbos) ou troca recusada.
enum class WordEdit { Same, Spelling, Agreement, Refused }
