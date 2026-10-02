package dev.rafaelbrauner.flowvoice.shared.pipeline

import dev.rafaelbrauner.flowvoice.shared.dictionary.CorrectionPair

// Diferença aprendível entre o que o app escreveu e o que o campo tem (Item 1 da revisão do motor,
// 2026-10-01): terminado o ditado, o usuário vê o resultado e conserta a palavra errada; o par
// errado→correto é o que o dicionário aprende. Puro e testável — a captura no campo
// (DictationPipeline.scheduleCorrectionCapture) só alimenta expected/actual e aprende o que daqui sai.
object CorrectionDiff {
    // Conservador v1: inserção/eliminação muda a quantidade de tokens e o alinhamento posição a
    // posição deixaria de ser 1:1 — aprender de um alinhamento errado gravaria regra falsa. Só troca
    // palavra a palavra ensina.
    private const val MAX_PAIRS = 3

    // Menos que 4 letras ("é", "um", "as") não caracteriza erro de transcrição que valha regra.
    private const val MIN_WORD_LETTERS = 4
    private val WHITESPACE = Regex("\\s+")
    private val WORD_ONLY = Regex("[\\p{L}]+")

    fun learnable(expected: String, actual: String): List<CorrectionPair> {
        val expectedTokens = tokenize(expected)
        val actualTokens = tokenize(actual)
        if (expectedTokens.size != actualTokens.size) return emptyList()
        val expectedStrips = expectedTokens.map(::strip)
        val actualStrips = actualTokens.map(::strip)
        val divergent = expectedStrips.indices.filter { position ->
            !expectedStrips[position].equals(actualStrips[position], ignoreCase = true)
        }
        // Nenhuma posição divergente: divergiu só espaço ou pontuação, e isso é trabalho do editor
        // (P148) — nunca regra de dicionário.
        if (divergent.isEmpty()) return emptyList()
        // Muitas posições mudando juntas é reformulação do usuário, não conserto de palavra: atribuir
        // isso a uma regra gravaria lixo no dicionário.
        if (divergent.size > MAX_PAIRS) return emptyList()
        return divergent.mapNotNull { position ->
            val wrong = expectedStrips[position]
            val right = actualStrips[position]
            if (isLearnableWord(wrong) && isLearnableWord(right) && !wrong.equals(right, ignoreCase = true)) {
                CorrectionPair(wrong, right)
            } else {
                null
            }
        }
    }

    // Espaço como separador depois do trim; tokens vazios de cadeia em branco saem fora, para que
    // "vazio contra algo" caia no caminho conservador de contagens diferentes.
    private fun tokenize(text: String): List<String> =
        text.trim().split(WHITESPACE).filter { it.isNotEmpty() }

    // Só as pontas entram na comparação: a pontuação presa à palavra (vírgula, ponto final) é do
    // editor; o meio do token fica como está, e o filtro de palavra-só-de-letras recusa o que sobrar
    // estranho (hífen, dígito no meio).
    private fun strip(token: String): String {
        var start = 0
        var end = token.length
        while (start < end && !token[start].isLetterOrDigit()) start++
        while (end > start && !token[end - 1].isLetterOrDigit()) end--
        return token.substring(start, end)
    }

    // Uma palavra só de letras cobrindo o token inteiro. O strip mantém dígito (isLetterOrDigit), e é
    // exatamente aqui que ele é recusado: "caza2" não ensina correção.
    private fun isLearnableWord(value: String): Boolean =
        value.matches(WORD_ONLY) && value.length >= MIN_WORD_LETTERS
}