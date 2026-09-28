package dev.rafaelbrauner.flowvoice.shared.localasr

// Texto dos tokens do fluxo contínuo e onde um pedaço pode fechar.
object StreamingText {
    private val SPACES = Regex("\\s+")

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
        for (index in tokens.size - 1 downTo start + 1) {
            if (startsWord(tokens[index])) return index
        }
        return start
    }

    private fun startsWord(token: String): Boolean = token.firstOrNull()?.isWhitespace() == true
}
