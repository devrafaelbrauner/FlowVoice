package dev.rafaelbrauner.flowvoice.shared.localasr

import dev.rafaelbrauner.flowvoice.shared.text.SpokenPunctuation

// Texto dos tokens do fluxo contínuo e onde um pedaço pode fechar.
object StreamingText {
    private val SPACES = Regex("\\s+")
    private const val CLOSING_PUNCTUATION = ",.;:!?…%)]}»"

    // O Nemotron escreve o espaço como token próprio e às vezes o repete ("tudo bem?  Consegue"):
    // espaço repetido vira um só, e as pontas saem.
    fun text(tokens: List<String>, from: Int, to: Int = tokens.size): String {
        val start = from.coerceIn(0, tokens.size)
        val end = to.coerceIn(start, tokens.size)
        if (start == end) return ""
        return tokens.subList(start, end).joinToString("").replace(SPACES, " ").trim()
    }

    // Até onde um corte sem pausa (teto do ditado, fala ainda em curso) pode fechar o pedaço, a partir
    // de `from`: logo antes do último token que abre palavra. A palavra só está inteira quando o
    // modelo já emitiu o começo da seguinte; a última palavra emitida pode ainda ganhar pedaços
    // ("B" + "om") e fica para o pedaço seguinte. Sem palavra seguinte, nada fecha (`from`).
    fun wholeWordsEnd(tokens: List<String>, from: Int): Int {
        val start = from.coerceIn(0, tokens.size)
        var end = (tokens.size - 1 downTo start + 1).firstOrNull { startsWord(tokens[it]) } ?: return start
        // Começo de comando falado ("nova", "ponto de") fica para o pedaço seguinte, junto do resto dele.
        repeat(SpokenPunctuation.trailingCommandPrefixWords(text(tokens, start, end))) {
            end = (end - 1 downTo start + 1).firstOrNull { startsWord(tokens[it]) } ?: start
        }
        return end
    }

    // O pedaço que começa em `from` continua a última palavra já fechada: o primeiro token não traz o
    // espaço de palavra nova ("minuto" + "s"), ou o pedaço abre com a pontuação dela (o ponto que saiu
    // depois da pausa, às vezes depois de um token só de espaço). O primeiro pedaço nunca cola.
    fun continuesPrevious(tokens: List<String>, from: Int, to: Int = tokens.size): Boolean {
        if (from !in 1 until to.coerceAtMost(tokens.size)) return false
        if (!startsWord(tokens[from])) return true
        return text(tokens, from, to).firstOrNull()?.let { it in CLOSING_PUNCTUATION } == true
    }

    private fun startsWord(token: String): Boolean = token.firstOrNull()?.isWhitespace() == true
}
