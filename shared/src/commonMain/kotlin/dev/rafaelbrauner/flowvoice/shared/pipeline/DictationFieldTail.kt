package dev.rafaelbrauner.flowvoice.shared.pipeline

// Quanto apagar do campo para trocar o ditado pela versão revisada (P148). A conta do próprio app
// pode divergir do campo: no S26 (2026-09-16 10:29) a revisão final apagou os 151 caracteres que o
// app achava ter escrito, o campo tinha 152, e sobrou a primeira letra — "HHoje o dia...". Aqui o
// campo é a verdade: compara-se letra a letra, ignorando espaços e pontuação (é justamente a
// pontuação que diverge), e o que sobra de sinais no meio entra no que será apagado.
object DictationFieldTail {
    // Folga aceita entre o campo e a conta do app, para mais ou para menos. Serve para uma diferença
    // de pontuação, não para um campo que virou outra coisa: acima disso não se apaga nada.
    const val SLACK = 16

    fun eraseLength(before: String, typed: String): Int? {
        if (typed.isBlank()) return null
        if (before.endsWith(typed)) return typed.length
        var index = before.length
        var remaining = typed.length
        while (remaining > 0) {
            remaining--
            val wanted = typed[remaining]
            if (!wanted.isLetterOrDigit()) continue
            var matched = false
            while (index > 0 && !matched) {
                index--
                val current = before[index]
                if (!current.isLetterOrDigit()) continue
                // Letra diferente: alguém digitou no meio do ditado, e apagar dali comeria texto que
                // não é do FlowVoice.
                if (current.lowercaseChar() != wanted.lowercaseChar()) return null
                matched = true
            }
            if (!matched) return null
        }
        var start = index
        // Ditado que começa em sinal (um espaço de emenda, por exemplo): os sinais equivalentes no
        // campo também são do FlowVoice e entram no que será apagado.
        var lead = typed.takeWhile { !it.isLetterOrDigit() }.length
        while (lead > 0 && start > 0 && !before[start - 1].isLetterOrDigit()) {
            start--
            lead--
        }
        val length = before.length - start
        val floor = (typed.length - SLACK).coerceAtLeast(1)
        return length.takeIf { it in floor..(typed.length + SLACK) }
    }
}
