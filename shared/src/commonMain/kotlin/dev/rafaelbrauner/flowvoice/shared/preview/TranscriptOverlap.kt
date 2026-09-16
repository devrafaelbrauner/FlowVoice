package dev.rafaelbrauner.flowvoice.shared.preview

import dev.rafaelbrauner.flowvoice.shared.text.EditDistance
import kotlin.math.min

// Deduplicação da emenda entre janelas (P127, endurecida pela P143 e pela P150).
//
// Com o contexto sobreposto em áudio (P143), o começo de cada janela repete o fim da anterior, e o
// modelo não devolve essa repetição igual: muda o caixa, a pontuação e às vezes o acento. A
// comparação é feita sem nada disso.
//
// Quatro degraus, do mais seguro ao mais arriscado:
//  1. maior sufixo de `left` igual ao prefixo de `right`;
//  2. o mesmo ignorando a última palavra de `left`, quando ela é um pedaço de palavra partida no
//     corte e a palavra inteira abre `right`: o que falta é colado sem espaço (`glued`);
//  3. o mesmo sem colar, quando o pedaço não casa com a palavra inteira — o pedaço sobra repetido,
//     que é feio mas nunca troca a palavra;
//  4. o casamento aproximado (P150), só onde os três primeiros desistiram e só quando a janela veio
//     com contexto: o mesmo áudio pode voltar transcrito com outras palavras.
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

    // `contextDurationMs`: quanto áudio da janela anterior foi repetido à frente desta (P143). Zero
    // significa "esta janela não repetiu nada", e aí o degrau 4 nem é tentado.
    fun match(left: String, right: String, contextDurationMs: Long = 0L): Match {
        if (right.isBlank()) return Match("", glued = false)
        if (left.isBlank()) return Match(right.trim(), glued = false)

        val leftTokens = tokenize(left)
        val rightTokens = tokenize(right)

        val exact = overlapSize(leftTokens, rightTokens)
        if (exact > 0) return Match(rightTokens.drop(exact).joinToString(" "), glued = false)

        val anchor = overlapSize(leftTokens.dropLast(1), rightTokens)
        if (anchor == 0) {
            val approximate = approximateOverlapSize(leftTokens, rightTokens, contextDurationMs)
            return Match(rightTokens.drop(approximate).joinToString(" "), glued = false)
        }

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

    // Degrau 4 (P150). Medido no S26 em 2026-09-16 com `openai/gpt-transcribe`: o mesmo segundo de
    // áudio saiu "está muito bonito" na janela 0 e "Tá muito bonito" na janela 1, e a comparação por
    // letras não reconheceu — "muito bonito" entrou duas vezes no campo.
    //
    // Aqui as palavras podem divergir um pouco, o que é perigoso: o risco é comer fala de verdade.
    // Por isso a busca é cercada de três lados. Só o COMEÇO de `right` contra o FIM de `left`, então
    // repetição que o usuário fez de propósito no meio da fala nunca é tocada. Nunca mais caracteres
    // do que o contexto poderia conter. E é preciso evidência: duas palavras casando, ou uma longa.
    private fun approximateOverlapSize(
        left: List<String>,
        right: List<String>,
        contextDurationMs: Long
    ): Int {
        if (contextDurationMs <= 0L) return 0
        val removableChars = removableChars(contextDurationMs)
        if (removableChars <= 0) return 0
        val max = minOf(left.size, right.size, MAX_OVERLAP_TOKENS)
        for (size in max downTo 1) {
            if (right.take(size).joinToString(" ").length > removableChars) continue
            val tail = left.takeLast(size).map(::normalize)
            val head = right.take(size).map(::normalize)
            val kinds = tail.indices.map { kindOf(tail[it], head[it]) }
            if (isRepetition(tail, kinds)) return size
        }
        return 0
    }

    // Quanto do começo de `right` o contexto poderia explicar. A fala corrida dá umas 15 letras por
    // segundo; o dobro é folga para o modelo escrever mais do que foi dito, e o teto absoluto impede
    // que um contexto longo autorize apagar um parágrafo.
    private fun removableChars(contextDurationMs: Long): Int =
        min(contextDurationMs * CHARS_PER_SECOND / 1000L, MAX_REMOVABLE_CHARS).toInt()

    private enum class Kind {
        // A mesma palavra, sem acento, sem pontuação e sem caixa (ou dois trechos só de pontuação).
        Same,

        // Palavra longa o bastante para que uma ou duas letras de diferença ainda sejam ortografia.
        Close,

        // Curta e diferente ("tá" por "está"): plausível, mas sozinha não prova nada.
        Loose,

        // Outra palavra.
        Other
    }

    private fun kindOf(a: String, b: String): Kind = when {
        a == b -> Kind.Same
        a.isEmpty() || b.isEmpty() -> Kind.Other
        min(a.length, b.length) >= CLOSE_MIN_CHARS &&
            EditDistance.within(a, b, closeBudget(a, b)) -> Kind.Close
        EditDistance.within(a, b, LOOSE_MAX_EDITS) -> Kind.Loose
        else -> Kind.Other
    }

    // Proporcional ao tamanho: duas edições em "diarreia" ainda são a mesma palavra; em "casa" já
    // seriam outra.
    private fun closeBudget(a: String, b: String): Int =
        if (maxOf(a.length, b.length) >= LONG_WORD_CHARS) 2 else 1

    private fun isRepetition(tail: List<String>, kinds: List<Kind>): Boolean {
        if (kinds.any { it == Kind.Other }) return false
        // Uma palavra frouxa só vale apoiada em vizinhas idênticas. Sozinha, ou encostada noutra
        // frouxa, ela casaria com fala de verdade — e o que é apagado aqui não volta.
        val supported = kinds.indices.all { index ->
            kinds[index] != Kind.Loose || neighbours(kinds, index).all { it == Kind.Same }
        }
        if (!supported) return false
        // Pontuação solta não é evidência: casa com qualquer coisa.
        val evidence = kinds.indices.filter { kinds[it] != Kind.Loose && tail[it].isNotEmpty() }
        return when {
            evidence.size >= MIN_EVIDENCE_WORDS -> true
            evidence.size == 1 -> tail[evidence.single()].length >= LONG_WORD_CHARS
            else -> false
        }
    }

    private fun neighbours(kinds: List<Kind>, index: Int): List<Kind> =
        listOfNotNull(kinds.getOrNull(index - 1), kinds.getOrNull(index + 1))

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
    private const val CLOSE_MIN_CHARS = 4
    private const val LONG_WORD_CHARS = 8
    private const val LOOSE_MAX_EDITS = 2
    private const val MIN_EVIDENCE_WORDS = 2
    private const val CHARS_PER_SECOND = 30L
    private const val MAX_REMOVABLE_CHARS = 120L

    private val ACCENTS: Map<Char, Char> = mapOf(
        'á' to 'a', 'à' to 'a', 'â' to 'a', 'ã' to 'a', 'ä' to 'a',
        'é' to 'e', 'è' to 'e', 'ê' to 'e', 'ë' to 'e',
        'í' to 'i', 'ì' to 'i', 'î' to 'i', 'ï' to 'i',
        'ó' to 'o', 'ò' to 'o', 'ô' to 'o', 'õ' to 'o', 'ö' to 'o',
        'ú' to 'u', 'ù' to 'u', 'û' to 'u', 'ü' to 'u',
        'ç' to 'c', 'ñ' to 'n'
    )
}
