package dev.rafaelbrauner.flowvoice.shared.text

// Pontuação falada: "vírgula", "dois pontos", "nova linha", "novo item"… viram sinal no texto de cada
// trecho, antes do vocabulário do usuário e de tudo o que vem depois (digitação, prévia, revisão,
// nota). Portado de `SpokenPunctuation.kt` do Intelligent Keyboard (medições de 11/set lá), só a parte
// por trecho: o ponto que o modelo pôs antes de uma vírgula dita sozinha já é tirado pelo
// DirectInsertionPlanner (P144).
//
// - O sinal gruda na palavra anterior, e a pontuação que o modelo pôs em volta do comando sai.
// - Depois de ponto final, interrogação, exclamação, quebra de linha e item, a palavra seguinte começa
//   com maiúscula.
// - "37 vírgula 5" vira "37,5"; "zero vírgula cinco", "dois pontos de sutura" e "o ponto final" são fala.
// - "ponto" sozinho continua texto ("ponto de ônibus").
// - Palavras de comando longas aceitam uma letra de diferença ("intervogação"); as curtas só exatas.
// - "novo item" e "próximo item" começam uma linha de lista com "- ".
object SpokenPunctuation {
    // Trecho inteiro: além dos comandos, reconhece o comando dito sozinho que o modelo ouviu errado
    // ("Nova Lí", "O latino final"). Não usar no provisório: "Nova Lí" pode ser o começo de "Nova Lima".
    fun applyToSegment(text: String): String {
        val command = misheardCommand(text) ?: return apply(text)
        val start = text.indexOfFirst { !it.isWhitespace() }
        val end = text.indexOfLast { !it.isWhitespace() }
        return apply(text.substring(0, start) + command + text.substring(end + 1))
    }

    fun apply(text: String): String {
        val source = withSignDotRewritten(text)
        val converted = convertCommands(source)
        if (source != text && converted == source) return text
        return converted
    }

    // Quantas palavras do fim de `text` começam um comando de várias palavras sem terminá-lo ("nova",
    // "ponto de"). O corte no meio da fala segura essas palavras para o pedaço seguinte, senão
    // "nova" | "linha" sai por extenso (S26, 2026-09-28). Se não era comando, só entra um pedaço depois.
    fun trailingCommandPrefixWords(text: String): Int {
        val words = WORD.findAll(text).map { it.value }.toList()
        return COMMANDS.filter { it.words.size > 1 }.maxOf { command ->
            (command.words.size - 1 downTo 1).firstOrNull { n ->
                n <= words.size && (0 until n).all { k ->
                    val token = words[words.size - n + k]
                    token.last() !in PUNCTUATION && similar(clean(token), command.words[k])
                }
            } ?: 0
        }
    }

    private fun misheardCommand(text: String): String? {
        val words = WORD.findAll(text).map { clean(it.value) }.filter { it.isNotEmpty() }.toList()
        if (words.isEmpty() || words.size > 3 || words.sumOf { it.length } > SHORT_SEGMENT_LETTERS) return null
        if (words.size == 2 && words[0] == "nova" && looksLikeLine(words[1])) return "nova linha"
        if (words.joinToString(" ") in FULL_STOP_VARIANTS) return "ponto final"
        return null
    }

    private fun looksLikeLine(word: String): Boolean =
        distance(word, "linha") <= 1 || (word.length >= 2 && "linha".startsWith(word))

    // ".de interrogação" volta a ser "ponto de interrogação" para o comando casar; "Fim. De manhã" é texto.
    private fun withSignDotRewritten(text: String): String {
        if ('.' !in text) return text
        return SIGN_DOT.replace(text) { m ->
            val sign = clean(m.groupValues[2])
            if (similar(sign, "interrogacao") || similar(sign, "exclamacao")) {
                " ponto ${m.groupValues[1]} ${m.groupValues[2]}"
            } else {
                m.value
            }
        }
    }

    private fun convertCommands(text: String): String {
        val words = WORD.findAll(text).toList()
        if (words.isEmpty()) return text

        val out = StringBuilder(text.length)
        var anyCommand = false
        // Depois de um comando, o espaço antes da palavra seguinte é decidido por ele; null quando a
        // última foi palavra comum.
        var spaceAfter: Boolean? = null
        var capitalize = false
        var previousEnd = 0
        var i = 0

        while (i < words.size) {
            val space = text.substring(previousEnd, words[i].range.first)
            val command = COMMANDS.firstOrNull { matches(words, i, it) && countsAsCommand(words, i, it) }

            if (command == null) {
                when (spaceAfter) {
                    null -> out.append(space)
                    true -> out.append(' ')
                    false -> Unit
                }
                val word = words[i].value
                out.append(if (capitalize) capitalized(word) else word)
                spaceAfter = null
                capitalize = false
                previousEnd = words[i].range.last + 1
                i++
                continue
            }

            anyCommand = true
            val last = i + command.words.size - 1
            when (command.fit) {
                Fit.CLOSES -> {
                    trimEnd(out, dropPunctuation = true)
                    out.append(command.symbol)
                    spaceAfter = !(command.symbol == "," && betweenNumbers(words, i, last) == Numbers.DIGITS)
                }
                Fit.BREAKS -> {
                    trimEnd(out, dropPunctuation = false)
                    out.append(command.symbol)
                    spaceAfter = false
                }
                Fit.OPENS -> {
                    if (out.isEmpty()) {
                        out.append(space)
                    } else if (!out.last().isWhitespace() && out.last() !in OPENINGS) {
                        out.append(' ')
                    }
                    out.append(command.symbol)
                    spaceAfter = false
                }
            }
            capitalize = command.endsSentence
            previousEnd = words[last].range.last + 1
            i = last + 1
        }

        out.append(text.substring(previousEnd))
        return if (anyCommand) out.toString() else text
    }

    private enum class Fit { CLOSES, OPENS, BREAKS }

    private class Command(
        val words: List<String>,
        val symbol: String,
        val fit: Fit,
        val endsSentence: Boolean = false,
        // Palavra que, logo antes ou logo depois, mostra que é fala comum.
        val notAfter: Set<String> = emptySet(),
        val notBefore: Set<String> = emptySet()
    )

    // Os de mais palavras primeiro: "ponto e vírgula" antes de "vírgula".
    private val COMMANDS = listOf(
        Command(listOf("ponto", "de", "interrogacao"), "?", Fit.CLOSES, endsSentence = true),
        Command(listOf("ponto", "de", "exclamacao"), "!", Fit.CLOSES, endsSentence = true),
        Command(listOf("ponto", "e", "virgula"), ";", Fit.CLOSES),
        Command(listOf("ponto", "final"), ".", Fit.CLOSES, endsSentence = true),
        Command(
            listOf("dois", "pontos"), ":", Fit.CLOSES,
            notAfter = setOf("mais", "menos"),
            notBefore = setOf("de", "do", "da", "dos", "das", "percentuais", "percentual", "acima", "abaixo")
        ),
        Command(listOf("nova", "linha"), "\n", Fit.BREAKS, endsSentence = true),
        Command(listOf("novo", "paragrafo"), "\n\n", Fit.BREAKS, endsSentence = true),
        Command(listOf("novo", "item"), "\n- ", Fit.BREAKS, endsSentence = true),
        Command(listOf("proximo", "item"), "\n- ", Fit.BREAKS, endsSentence = true),
        Command(listOf("abre", "aspas"), "\"", Fit.OPENS),
        Command(listOf("fecha", "aspas"), "\"", Fit.CLOSES),
        Command(listOf("abre", "parenteses"), "(", Fit.OPENS),
        Command(listOf("fecha", "parenteses"), ")", Fit.CLOSES),
        Command(listOf("interrogacao"), "?", Fit.CLOSES, endsSentence = true),
        Command(listOf("exclamacao"), "!", Fit.CLOSES, endsSentence = true),
        Command(listOf("virgula"), ",", Fit.CLOSES),
        Command(listOf("reticencias"), "...", Fit.CLOSES)
    )

    // Ninguém dita um comando logo depois de artigo ou preposição: "uma vírgula", "o ponto final".
    private val BEFORE_NOUN = setOf(
        "o", "a", "os", "as", "um", "uma", "uns", "umas",
        "no", "na", "nos", "nas", "do", "da", "dos", "das",
        "ao", "aos", "pelo", "pela", "de", "sem"
    )

    private val SPELLED_NUMBERS = setOf(
        "zero", "um", "uma", "dois", "duas", "tres", "quatro", "cinco", "seis", "sete", "oito", "nove", "dez"
    )

    private const val SHORT_SEGMENT_LETTERS = 16
    private val FULL_STOP_VARIANTS = setOf("o latino final", "ponto finau", "ponto fina")
    private val SIGN_DOT = Regex("(?<!\\.)\\.(?!\\.)\\s*(de)\\s+(\\S+)", RegexOption.IGNORE_CASE)
    private val WORD = Regex("\\S+")
    private val DIGITS = Regex("\\d+")
    private const val PUNCTUATION = ".,;:!?…\"“”'()"
    private const val MODEL_PUNCTUATION = ".,;:!?…"
    private const val OPENINGS = "(\""
    // Uma letra de diferença só vale para palavras de comando a partir deste tamanho.
    private const val APPROXIMATE_FROM = 9

    private fun matches(words: List<MatchResult>, start: Int, command: Command): Boolean {
        val n = command.words.size
        if (start + n > words.size) return false
        for (k in 0 until n) {
            val token = words[start + k].value
            // Pontuação entre as palavras de um comando separa: "ponto, final" não é comando.
            if (k < n - 1 && token.last() in PUNCTUATION) return false
            if (k > 0 && token.first() in PUNCTUATION) return false
            if (!similar(clean(token), command.words[k])) return false
        }
        return true
    }

    private fun countsAsCommand(words: List<MatchResult>, start: Int, command: Command): Boolean {
        val last = start + command.words.size - 1
        val before = words.getOrNull(start - 1)?.value
        if (before != null && before.last() !in PUNCTUATION) {
            val word = clean(before)
            if (word in BEFORE_NOUN || word in command.notAfter) return false
        }
        val after = words.getOrNull(last + 1)?.value
        if (after != null && words[last].value.last() !in PUNCTUATION && clean(after) in command.notBefore) {
            return false
        }
        return !(command.symbol == "," && betweenNumbers(words, start, last) == Numbers.SPELLED)
    }

    private enum class Numbers { DIGITS, SPELLED, NONE }

    private fun betweenNumbers(words: List<MatchResult>, start: Int, last: Int): Numbers {
        val before = words.getOrNull(start - 1)?.value?.trim { it in PUNCTUATION } ?: return Numbers.NONE
        val after = words.getOrNull(last + 1)?.value?.trim { it in PUNCTUATION } ?: return Numbers.NONE
        if (DIGITS.matches(before) && DIGITS.matches(after)) return Numbers.DIGITS
        if (plain(before) in SPELLED_NUMBERS && plain(after) in SPELLED_NUMBERS) return Numbers.SPELLED
        return Numbers.NONE
    }

    private fun clean(token: String): String = plain(token.trim { it in PUNCTUATION })

    private fun plain(word: String): String = buildString {
        word.lowercase().forEach { append(ACCENTS[it] ?: it) }
    }

    private fun similar(heard: String, command: String): Boolean {
        if (heard == command) return true
        // "dois pontos" sai "2 pontos" (medição do teclado, 11/set).
        if (command == "dois" && heard == "2") return true
        if (command.length < APPROXIMATE_FROM) return false
        if (kotlin.math.abs(heard.length - command.length) > 1) return false
        return distance(heard, command) <= 1
    }

    private fun distance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
            }
            previous = current
        }
        return previous[b.length]
    }

    private fun trimEnd(out: StringBuilder, dropPunctuation: Boolean) {
        while (out.isNotEmpty() && (out.last().isWhitespace() || (dropPunctuation && out.last() in MODEL_PUNCTUATION))) {
            out.setLength(out.length - 1)
        }
    }

    private fun capitalized(word: String): String {
        val i = word.indexOfFirst { it.isLetter() }
        if (i < 0 || !word[i].isLowerCase()) return word
        return word.substring(0, i) + word[i].uppercase() + word.substring(i + 1)
    }

    private val ACCENTS: Map<Char, Char> = buildMap {
        "áàâãä".forEach { put(it, 'a') }
        "éèêë".forEach { put(it, 'e') }
        "íìîï".forEach { put(it, 'i') }
        "óòôõö".forEach { put(it, 'o') }
        "úùûü".forEach { put(it, 'u') }
        put('ç', 'c')
    }
}
