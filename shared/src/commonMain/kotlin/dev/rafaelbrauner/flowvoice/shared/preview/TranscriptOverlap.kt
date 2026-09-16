package dev.rafaelbrauner.flowvoice.shared.preview

import dev.rafaelbrauner.flowvoice.shared.text.EditDistance
import dev.rafaelbrauner.flowvoice.shared.text.PtBrSound
import kotlin.math.min

// Deduplicação da emenda entre janelas (P127, endurecida pela P143, pela P150 e pela P155).
//
// Com o contexto sobreposto em áudio (P143), o começo de cada janela repete o fim da anterior, e o
// modelo não devolve essa repetição igual: muda o caixa, a pontuação e às vezes o acento. A
// comparação é feita sem nada disso.
//
// Cinco degraus, do mais seguro ao mais arriscado:
//  1. maior sufixo de `left` igual ao prefixo de `right`;
//  2. o mesmo ignorando a última palavra de `left`, quando ela é um pedaço de palavra partida no
//     corte e a palavra inteira abre `right`: o que falta é colado sem espaço (`glued`);
//  3. o mesmo sem colar, quando o pedaço não casa com a palavra inteira — o pedaço sobra repetido,
//     que é feio mas nunca troca a palavra;
//  4. o casamento aproximado (P150), só onde os três primeiros desistiram e só quando a janela veio
//     com contexto: o mesmo áudio pode voltar transcrito com outras palavras;
//  5. a primeira palavra com o começo cortado (P155), só onde o degrau 4 também desistiu e só com
//     contexto: o contexto começa num ponto qualquer da fala e o modelo inventa o começo da primeira
//     palavra, mas o resto tem de casar exato.
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
                .takeIf { it > 0 }
                ?: clippedOnsetOverlapSize(leftTokens, rightTokens, contextDurationMs)
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

    // Degrau 5 (P155). Medido no S26 em 2026-09-16 15:28 (ditado de ~2 min, 30 janelas, contexto de
    // 1 s em todas): três emendas entraram dobradas, e nas três a PRIMEIRA palavra do trecho novo era
    // a única diferente — "do STF," → "No STF,", "afirmações contundentes." → "informações
    // contundentes" e "e julgada." → "Em julgado.". O segundo de contexto começa num ponto qualquer da
    // fala, então o começo da primeira palavra chega cortado e o modelo completa com o que soa
    // plausível; o fim dela e as palavras seguintes foram ouvidos inteiros.
    //
    // Por isso a tolerância vale só para essa primeira palavra, e só de três formas que o corte
    // explica (`isShortSwap`, `sharesClippedEnding`, palavra curta acrescida em
    // `explainsAddedShortWord`). Todas as palavras depois dela casam exatas e vão até o fim de `left`,
    // e o trecho removido cabe no que o contexto comporta.
    private fun clippedOnsetOverlapSize(
        left: List<String>,
        right: List<String>,
        contextDurationMs: Long
    ): Int {
        if (contextDurationMs <= 0L) return 0
        val removableChars = removableChars(contextDurationMs)
        val leftPlain = left.map(::normalize)
        val rightPlain = right.map(::normalize)
        val max = minOf(right.size, left.size + 1, MAX_OVERLAP_TOKENS)
        for (size in max downTo 2) {
            if (right.take(size).joinToString(" ").length > removableChars) continue
            if (explainsSwappedFirstWord(leftPlain, rightPlain, size)) return size
            if (explainsAddedShortWord(leftPlain, rightPlain, size)) return size
        }
        return 0
    }

    // `right[0]` ocupa o lugar de uma palavra de `left`, e `right[1 until size]` repete exatamente o
    // fim de `left`. A palavra seguinte precisa ter conteúdo: sem ela a primeira palavra é palpite.
    private fun explainsSwappedFirstWord(left: List<String>, right: List<String>, size: Int): Boolean {
        if (left.size < size) return false
        val followers = right.subList(1, size)
        if (followers != left.takeLast(size - 1) || !hasContentWord(followers)) return false
        val aligned = left[left.size - size]
        val first = right[0]
        return isShortSwap(aligned, first) || sharesClippedEnding(aligned, first)
    }

    // `right[0]` é uma palavra curta a mais ("Em"), `right[1]` é a palavra de `left` — igual ou só com
    // a última vogal trocada ("julgada" → "julgado") — e o resto repete o fim de `left` exatamente.
    private fun explainsAddedShortWord(left: List<String>, right: List<String>, size: Int): Boolean {
        if (left.size < size - 1) return false
        val added = right[0]
        if (!isShortWord(added)) return false
        val followers = right.subList(2, size)
        if (followers != left.takeLast(size - 2)) return false
        val aligned = left[left.size - size + 1]
        val word = right[1]
        if (aligned in NEGATIONS || (word != aligned && !isFinalVowelInflection(aligned, word))) return false
        return hasContentWord(followers) || aligned.length >= ADDED_WORD_EVIDENCE_CHARS
    }

    // Palavra curta com só o começo trocado: "do" → "no", "o" → "no". Artigo, preposição e contração
    // têm 1 a 3 letras, e são átonas — é nelas que o corte cai sem o modelo ouvir. Só a primeira letra
    // pode mudar (entrar, sair ou ser trocada); o resto é o que foi ouvido e tem de ser igual.
    private fun isShortSwap(aligned: String, first: String): Boolean {
        if (!isShortWord(aligned) || !isShortWord(first) || aligned == first) return false
        val shared = commonSuffixLength(aligned, first)
        return shared >= 1 && aligned.length - shared <= 1 && first.length - shared <= 1
    }

    // Palavra longa com o começo cortado: "afirmações" → "informações" dividem "rmacoes". O final
    // comum tem de ser maior que um sufixo de derivação (os mais comuns — "acoes", "mente", "mento",
    // "idade" — têm 5 letras; com 6, "amente" ainda casaria "rapidamente" e "lentamente", barrado
    // pela metade da palavra e pelo começo de até 4 letras), cobrir ao menos metade da palavra maior e
    // deixar no máximo 4 letras diferentes no começo de cada uma — uma sílaba e pouco, uns 250 ms de
    // fala a 15 letras por segundo, que é o que um corte no meio da palavra consegue esconder.
    private fun sharesClippedEnding(aligned: String, first: String): Boolean {
        if (aligned == first || !isWord(aligned) || !isWord(first)) return false
        if (aligned in NEGATIONS || first in NEGATIONS) return false
        val shared = commonSuffixLength(aligned, first)
        return shared >= CLIPPED_ENDING_MIN_CHARS &&
            shared * 2 >= maxOf(aligned.length, first.length) &&
            aligned.length - shared <= CLIPPED_ONSET_MAX_CHARS &&
            first.length - shared <= CLIPPED_ONSET_MAX_CHARS
    }

    // Mesma palavra com a última vogal trocada: gênero e número do particípio ("julgada"/"julgado").
    private fun isFinalVowelInflection(a: String, b: String): Boolean =
        a.length == b.length && a.length >= 2 && isWord(a) &&
            a.dropLast(1) == b.dropLast(1) &&
            a.last() != b.last() && a.last() in PtBrSound.VOWELS && b.last() in PtBrSound.VOWELS

    // Negação nunca é tolerada como palavra divergente: trocar "ao" por "não" inverte a frase, e o que
    // é apagado aqui não volta.
    private fun isShortWord(token: String): Boolean =
        token.length in 1..SHORT_WORD_MAX_CHARS && isWord(token) && token !in NEGATIONS

    private fun isWord(token: String): Boolean = token.isNotEmpty() && token.all { it.isLetter() }

    // Evidência de conteúdo: ao menos uma palavra de 3+ letras repetida exatamente. Três letras
    // admitem sigla e nome curto ("STF", "UTI", "SUS"), que o modelo reescreve sempre igual; com uma ou
    // duas seriam só "o", "de", "no", que casam por acaso.
    private fun hasContentWord(tokens: List<String>): Boolean = tokens.any { it.length >= CONTENT_WORD_MIN_CHARS }

    private fun commonSuffixLength(a: String, b: String): Int {
        var size = 0
        while (size < a.length && size < b.length && a[a.length - 1 - size] == b[b.length - 1 - size]) size++
        return size
    }

    private fun normalize(token: String): String = PtBrSound.plain(token)

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
    private const val SHORT_WORD_MAX_CHARS = 3
    private const val CONTENT_WORD_MIN_CHARS = 3
    private const val CLIPPED_ENDING_MIN_CHARS = 6
    private const val CLIPPED_ONSET_MAX_CHARS = 4

    // Sem palavra repetida depois, a palavra alinhada é a única prova e precisa de 7+ letras: as
    // flexões mais frequentes da fala são curtas ("ele"/"ela", "todo"/"toda", "outro"/"outra",
    // "bonito"/"bonita"), e ali artigo + palavra repetida é fala real ("O bonito é que…").
    private const val ADDED_WORD_EVIDENCE_CHARS = 7

    // Já sem acento, como sai de `normalize`.
    private val NEGATIONS = setOf("nao", "nem", "sem", "nunca", "jamais", "nada", "nenhum", "nenhuma")
}
