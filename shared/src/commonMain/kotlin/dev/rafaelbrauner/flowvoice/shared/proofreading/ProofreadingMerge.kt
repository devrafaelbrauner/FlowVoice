package dev.rafaelbrauner.flowvoice.shared.proofreading

// Mistura a revisão com o ditado palavra a palavra (P149). O guard da P132 é tudo ou nada: no S26
// (2026-09-16 10:42) a revisão trocou "Tá" por "Está", e o texto inteiro foi descartado junto com a
// vírgula e as maiúsculas que estavam certas. Aqui a palavra do ditado fica onde a revisão trocou o
// que não podia, e a pontuação da revisão entra do mesmo jeito. Aspas e dois-pontos que o ditado não
// tinha saem do vão (S26 2026-09-17: "manhã:" derrubava a vírgula); o resto da pontuação da revisão
// continua. O resultado ainda passa pelo guard.
// P157: concordância é de frase, não de palavra. No S26 (2026-09-16 16:18) a revisão trocou "Um deles
// foram nomeados" por "foi nomeado", a mistura aceitou só "nomeado" e o campo ficou "foram nomeado",
// pior que as duas versões. Quando uma palavra da frase é recusada, as trocas de terminação da mesma
// frase também voltam ao ditado; a ortografia e a pontuação seguem valendo.
object ProofreadingMerge {
    fun merge(original: String, revised: String): String? {
        val source = words(original)
        val target = words(revised)
        // Quantidade diferente de palavras não é revisão: é outro texto, e não há o que alinhar.
        if (source.isEmpty() || source.size != target.size) return null
        val edits = source.indices.map { ProofreadingGuard.wordEdit(original.substring(source[it]), revised.substring(target[it])) }
        val sentences = sentenceIndexes(revised, target)
        val refusedSentences = source.indices.filter { edits[it] == WordEdit.Refused }.map { sentences[it] }.toSet()
        return buildString {
            var revisedCursor = 0
            var originalCursor = 0
            target.forEachIndexed { index, span ->
                append(
                    sanitizedGap(
                        original.substring(originalCursor, source[index].first),
                        revised.substring(revisedCursor, span.first),
                        original,
                        afterNegation = index > 0 && ProofreadingGuard.isNegation(original.substring(source[index - 1]))
                    )
                )
                val dictated = original.substring(source[index])
                val proposed = revised.substring(span)
                val keepDictated = edits[index] == WordEdit.Refused ||
                    (edits[index] == WordEdit.Agreement && sentences[index] in refusedSentences)
                append(if (keepDictated) keep(dictated, proposed) else proposed)
                revisedCursor = span.last + 1
                originalCursor = source[index].last + 1
            }
            append(
                sanitizedGap(
                    original.substring(originalCursor, original.length),
                    revised.substring(revisedCursor, revised.length),
                    original,
                    afterNegation = false
                )
            )
        }
    }

    // Frase de cada palavra, pela pontuação da revisão: ponto, interrogação, exclamação ou quebra de
    // linha no vão antes da palavra abrem uma frase nova.
    private fun sentenceIndexes(revised: String, spans: List<IntRange>): IntArray {
        val indexes = IntArray(spans.size)
        var sentence = 0
        spans.forEachIndexed { index, span ->
            if (index > 0 && revised.substring(spans[index - 1].last + 1, span.first).any { it in SENTENCE_ENDS }) sentence++
            indexes[index] = sentence
        }
        return indexes
    }

    private val SENTENCE_ENDS = setOf('.', '?', '!', '\n')

    // Sinal novo no vão: fica a pontuação do ditado. Se o ditado não tinha vão (fim do texto) e a
    // revisão só acrescentou aspas em volta de um ponto, o ponto entra.
    // R2: depois de uma negação o vão fica o do ditado se a revisão pôs pontuação nova ("Não, tomou").
    private fun sanitizedGap(originalGap: String, revisedGap: String, original: String, afterNegation: Boolean): String {
        if (afterNegation && ProofreadingGuard.addsPunctuation(originalGap, revisedGap)) return originalGap
        val cleaned = ProofreadingGuard.stripForbiddenNewSignals(revisedGap, original)
        if (cleaned == revisedGap) return revisedGap
        return originalGap.ifEmpty { cleaned }
    }

    // A palavra é a do ditado, mas a maiúscula é a da revisão: depois de uma vírgula que a revisão
    // acabou de pôr no lugar do ponto, a frase não recomeça com letra maiúscula.
    private fun keep(dictated: String, proposed: String): String {
        if (dictated.isEmpty() || proposed.isEmpty()) return dictated
        val first = if (proposed[0].isUpperCase()) dictated[0].uppercaseChar() else dictated[0].lowercaseChar()
        return first + dictated.substring(1)
    }

    private fun words(text: String): List<IntRange> {
        val spans = mutableListOf<IntRange>()
        var start = -1
        text.forEachIndexed { index, char ->
            if (char.isLetterOrDigit()) {
                if (start < 0) start = index
            } else if (start >= 0) {
                spans += start until index
                start = -1
            }
        }
        if (start >= 0) spans += start until text.length
        return spans
    }
}
