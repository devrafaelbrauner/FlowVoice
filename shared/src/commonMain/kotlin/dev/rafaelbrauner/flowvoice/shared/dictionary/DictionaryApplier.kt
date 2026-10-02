package dev.rafaelbrauner.flowvoice.shared.dictionary

import dev.rafaelbrauner.flowvoice.shared.text.EditDistance
import dev.rafaelbrauner.flowvoice.shared.text.PtBrSound
import kotlin.math.abs

// O vocabulário do usuário aplicado ao texto que voltou da transcrição (P145).
//
// Medido no S26 em 2026-09-16 com `openai/gpt-transcribe`: "dispneia" saiu "de Espinéia" (uma palavra
// virou duas), "na praia" saiu "napraj" (duas viraram uma) e um sobrenome virou "Grandmont". O
// vocabulário de antes trocava palavra por palavra e só quando o texto já trazia o termo inteiro com
// outro caixa, então não alcançava nenhum dos três.
//
// Antes da fonética vale a regra que o usuário ensinou explicitamente, errado → correto: a troca
// apontada por ele decide sobre qualquer casamento por som (Item 1 da revisão do motor). A regra é
// janela de 1, palavra inteira, e nunca toca token com dígito.
//
// Aqui o termo é procurado por som, em janelas de uma a N palavras, e o casamento é estreito de
// propósito: o risco é reescrever o que o usuário falou de verdade.
//  1. o esqueleto de consoantes tem de ser idêntico — vogal e semivogal o modelo troca, consoante
//     nova é outra palavra ("na praça" nunca vira "na praia", "hipertensão" nunca vira "hipotensão");
//  2. a distância de edição sobre a chave de som fica em 1, ou 2 a partir de 8 letras;
//  3. aproximação só quando a fronteira mudou (o número de palavras difere) ou quando o termo é longo
//     — palavra curta de mesma estrutura é fala legítima parecida, não o termo;
//  4. diferença só na última vogal é flexão de gênero ou número ("comprimido" não vira "comprimida");
//  5. nada com dígito é tocado, nem no texto nem no termo: número, dose e unidade ficam como foram
//     ditados.
object DictionaryApplier {
    fun apply(
        text: String,
        approved: Collection<String>,
        corrections: Collection<CorrectionPair> = emptyList()
    ): String {
        if (text.isBlank()) return text
        val terms = approved.mapNotNull(Term::of)
        val rules = corrections.mapNotNull(CorrectionRule::of)
        if (terms.isEmpty() && rules.isEmpty()) return text
        val words = WORD.findAll(text).map { it.range }.toList()
        if (words.isEmpty()) return text
        // Sem termo não há janela de som para computar; a regra é sempre janela 1.
        val maxWindow = if (terms.isEmpty()) 0 else minOf(terms.maxOf { it.words } + 1, MAX_WINDOW_WORDS)

        val result = StringBuilder()
        var copied = 0
        var index = 0
        while (index < words.size) {
            // Regra primeiro, na mesma posição: dentro da janela de 1 ela vence termo exato e som.
            val rule = if (rules.isEmpty()) null else ruleAt(text, words, index, rules)
            if (rule != null) {
                val range = words[index]
                result.append(text, copied, range.first)
                result.append(rule.replacementFor(text.substring(range.first, range.last + 1)))
                copied = range.last + 1
                index++
                continue
            }
            val hit = if (terms.isEmpty()) null else matchAt(text, words, index, terms, maxWindow)
            if (hit == null) {
                index++
                continue
            }
            val start = words[index].first
            val end = words[index + hit.words - 1].last
            result.append(text, copied, start)
            result.append(hit.term.replacementFor(text.substring(start, end + 1)))
            copied = end + 1
            index += hit.words
        }
        if (copied == 0) return text
        result.append(text, copied, text.length)
        return result.toString()
    }

    // Janela maior primeiro: "de espinéia" tem de ser reconhecido inteiro antes que "de" sozinho
    // encoste em algum termo. O termo exato ganha do aproximado, e entre aproximados vale a ordem em
    // que o usuário aprovou.
    private fun matchAt(
        text: String,
        words: List<IntRange>,
        index: Int,
        terms: List<Term>,
        maxWindow: Int
    ): Hit? {
        for (size in minOf(maxWindow, words.size - index) downTo 1) {
            val candidate = candidateAt(text, words, index, size) ?: continue
            val term = terms.firstOrNull { it.matchesExactly(candidate) }
                ?: terms.firstOrNull { it.matchesBySound(candidate) }
            if (term != null) return Hit(term, size)
        }
        return null
    }

    // A regra casa a palavra inteira do token, sem caixa. Token com dígito nunca é tocado, a mesma
    // proteção da janela de som: número, dose e unidade ficam como foram ditados.
    private fun ruleAt(
        text: String,
        words: List<IntRange>,
        index: Int,
        rules: List<CorrectionRule>
    ): CorrectionRule? {
        val range = words[index]
        if (range.any { text[it].isDigit() }) return null
        val word = text.substring(range.first, range.last + 1)
        return rules.firstOrNull { it.wrong == word.lowercase() }
    }

    // Uma janela só existe se as palavras estiverem separadas apenas por espaço: pontuação no meio
    // pertence à frase, e engoli-la mudaria o que o usuário ditou. Dígito na janela a descarta.
    private fun candidateAt(text: String, words: List<IntRange>, index: Int, size: Int): Candidate? {
        for (offset in 0 until size) {
            val range = words[index + offset]
            if (range.any { text[it].isDigit() }) return null
            if (offset > 0) {
                val gap = text.substring(words[index + offset - 1].last + 1, range.first)
                if (gap.isEmpty() || gap.any { !it.isWhitespace() }) return null
            }
        }
        // Palavra sem consoante ("o", "ou", "ao") não deixa rastro no esqueleto: na borda da janela ela
        // seria engolida de graça, e "o comprimido" viraria o termo "comprimida".
        if (size > 1 && (!hasConsonant(text, words[index]) || !hasConsonant(text, words[index + size - 1]))) {
            return null
        }
        val span = text.substring(words[index].first, words[index + size - 1].last + 1)
        return Candidate(span, size)
    }

    private fun hasConsonant(text: String, word: IntRange): Boolean =
        PtBrSound.skeleton(PtBrSound.key(text.substring(word.first, word.last + 1))).isNotEmpty()

    private class Hit(val term: Term, val words: Int)

    private class Candidate(val text: String, val words: Int) {
        val key: String = PtBrSound.key(text)
        val skeleton: String = PtBrSound.skeleton(key)
    }

    private class Term(val surface: String, val key: String, val words: Int) {
        val skeleton: String = PtBrSound.skeleton(key)

        // Caixa e acento de sempre (o vocabulário antigo): o termo entra exatamente como o usuário o
        // escreveu.
        fun matchesExactly(candidate: Candidate): Boolean =
            (candidate.words == 1 && candidate.text.equals(surface, ignoreCase = true)) ||
                (candidate.key == key && key.length >= MIN_SOUND_CHARS)

        fun matchesBySound(candidate: Candidate): Boolean {
            if (candidate.key == key) return false
            if (key.length < MIN_SOUND_CHARS) return false
            // A fronteira anda no máximo uma palavra para cada lado; o resto é outra frase.
            if (abs(candidate.words - words) > 1) return false
            // O começo é o que o modelo não inventa: casamento que desloca o início está engolindo a
            // palavra vizinha, não corrigindo a fronteira.
            if (candidate.key.firstOrNull() != key.first()) return false
            if (candidate.skeleton != skeleton) return false
            if (candidate.skeleton.length < MIN_SKELETON_CONSONANTS) return false
            // Mesma fronteira e termo curto: parecido demais com fala legítima para valer a troca.
            if (candidate.words == words && key.length < LONG_TERM_CHARS) return false
            if (isInflection(candidate.key)) return false
            val budget = if (maxOf(candidate.key.length, key.length) >= LONG_TERM_CHARS) 2 else 1
            return EditDistance.within(candidate.key, key, budget)
        }

        // Em pt-BR a última vogal é gênero ou número, não erro de transcrição.
        private fun isInflection(candidate: String): Boolean =
            candidate.length == key.length &&
                candidate.dropLast(1) == key.dropLast(1) &&
                candidate.last() in PtBrSound.VOWELS &&
                key.last() in PtBrSound.VOWELS

        // Maiúscula de início de frase é do texto, não do termo: mantê-la evita rebaixar a primeira
        // palavra de uma frase. Termo que já começa com maiúscula entra como está.
        fun replacementFor(span: String): String =
            if (span.first().isUpperCase() && surface.first().isLowerCase()) {
                surface.replaceFirstChar { it.uppercaseChar() }
            } else {
                surface
            }

        companion object {
            fun of(raw: String): Term? {
                val surface = raw.trim()
                if (surface.isEmpty() || surface.any { it.isDigit() }) return null
                val key = PtBrSound.key(surface)
                if (key.isEmpty()) return null
                return Term(surface, key, WORD.findAll(surface).count())
            }
        }
    }

    // Regra errado→correto ensinada. O casamento é a chave em minúsculas, exata e palavra inteira,
    // sem cair na fonética. A caixa da palavra casada é preservada como nos termos: "Caza" vira
    // "Casa" (início de frase é do texto, não da regra), e palavra minúscula recebe o `right` tal
    // como o usuário o ensinou.
    private class CorrectionRule(val wrong: String, val right: String) {
        fun replacementFor(word: String): String =
            if (word.first().isUpperCase() && right.first().isLowerCase()) {
                right.replaceFirstChar { it.uppercaseChar() }
            } else {
                right
            }

        companion object {
            fun of(pair: CorrectionPair): CorrectionRule? {
                val key = pair.wrong.trim().lowercase()
                val right = pair.right.trim()
                if (key.isEmpty() || right.isEmpty()) return null
                return CorrectionRule(key, right)
            }
        }
    }

    private val WORD = Regex("[\\p{L}\\p{N}]+")

    private const val MAX_WINDOW_WORDS = 4
    private const val MIN_SOUND_CHARS = 4
    private const val MIN_SKELETON_CONSONANTS = 3
    private const val LONG_TERM_CHARS = 8
}
