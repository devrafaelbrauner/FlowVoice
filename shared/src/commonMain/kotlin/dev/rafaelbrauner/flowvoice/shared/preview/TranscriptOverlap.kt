package dev.rafaelbrauner.flowvoice.shared.preview

// Deduplicação da emenda entre janelas (P127, endurecida pela P143).
//
// Com o contexto sobreposto em áudio (P143), o começo de cada janela repete o fim da anterior, e o
// modelo não devolve essa repetição igual: muda o caixa, a pontuação e às vezes o acento. A
// comparação é feita sem nada disso.
//
// Três degraus, do mais seguro ao mais arriscado:
//  1. maior sufixo de `left` igual ao prefixo de `right`;
//  2. o mesmo ignorando a última palavra de `left`, quando ela é um pedaço de palavra partida no
//     corte e a palavra inteira abre `right`: o que falta é colado sem espaço (`glued`);
//  3. o mesmo sem colar, quando o pedaço não casa com a palavra inteira — o pedaço sobra repetido,
//     que é feio mas nunca troca a palavra.
//
// Os degraus 2 e 3 exigem ao menos uma palavra de âncora antes do pedaço. É isso que torna a regra
// segura: sem contexto sobreposto quase não há âncora, e nada é removido. Um pedaço de uma letra só
// nunca cola, porque seria artigo ou preposição virando começo de palavra ("a" + "amostra").
object TranscriptOverlap {
    data class Match(
        // O que falta digitar depois de `left`.
        val text: String,
        // Verdadeiro quando `text` fecha uma palavra partida e tem de entrar sem separador.
        val glued: Boolean
    )

    fun match(left: String, right: String): Match {
        if (right.isBlank()) return Match("", glued = false)
        if (left.isBlank()) return Match(right.trim(), glued = false)

        val leftTokens = tokenize(left)
        val rightTokens = tokenize(right)

        val exact = overlapSize(leftTokens, rightTokens)
        if (exact > 0) return Match(rightTokens.drop(exact).joinToString(" "), glued = false)

        val anchor = overlapSize(leftTokens.dropLast(1), rightTokens)
        if (anchor == 0) return Match(rightTokens.joinToString(" "), glued = false)

        val fragment = leftTokens.last()
        val whole = rightTokens[anchor]
        if (completes(fragment, whole)) {
            val rest = rightTokens.drop(anchor + 1)
            val tail = whole.substring(fragment.length)
            return Match((listOf(tail) + rest).joinToString(" "), glued = true)
        }
        return Match(rightTokens.drop(anchor).joinToString(" "), glued = false)
    }

    private fun completes(fragment: String, whole: String): Boolean =
        fragment.length >= MIN_FRAGMENT_CHARS &&
            whole.length > fragment.length &&
            whole.startsWith(fragment, ignoreCase = true)

    // Maior sufixo de `left` igual ao prefixo de `right`. Um trecho só de pontuação não conta como
    // repetição: ele casaria com qualquer outro e apagaria palavra de verdade.
    private fun overlapSize(left: List<String>, right: List<String>): Int {
        val max = minOf(left.size, right.size, MAX_OVERLAP_TOKENS)
        for (size in max downTo 1) {
            val tail = left.takeLast(size).map(::normalize)
            val head = right.take(size).map(::normalize)
            if (tail == head && tail.any { it.isNotEmpty() }) return size
        }
        return 0
    }

    private fun normalize(token: String): String = buildString {
        token.forEach { char ->
            val lower = char.lowercaseChar()
            val plain = ACCENTS[lower] ?: lower
            if (plain.isLetterOrDigit()) append(plain)
        }
    }

    private fun tokenize(text: String): List<String> =
        text.split(Regex("\\s+")).filter { it.isNotBlank() }

    // Uma repetição longa de verdade é contexto, não coincidência; o teto só limita o estrago de um
    // casamento improvável, já que o contexto de 1 s cabe em poucas palavras.
    private const val MAX_OVERLAP_TOKENS = 12
    private const val MIN_FRAGMENT_CHARS = 2

    private val ACCENTS: Map<Char, Char> = mapOf(
        'á' to 'a', 'à' to 'a', 'â' to 'a', 'ã' to 'a', 'ä' to 'a',
        'é' to 'e', 'è' to 'e', 'ê' to 'e', 'ë' to 'e',
        'í' to 'i', 'ì' to 'i', 'î' to 'i', 'ï' to 'i',
        'ó' to 'o', 'ò' to 'o', 'ô' to 'o', 'õ' to 'o', 'ö' to 'o',
        'ú' to 'u', 'ù' to 'u', 'û' to 'u', 'ü' to 'u',
        'ç' to 'c', 'ñ' to 'n'
    )
}
