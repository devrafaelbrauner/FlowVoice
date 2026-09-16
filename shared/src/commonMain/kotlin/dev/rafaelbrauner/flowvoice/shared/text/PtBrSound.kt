package dev.rafaelbrauner.flowvoice.shared.text

// Como duas grafias soam em pt-BR, para poder compará-las. O modelo escreve o mesmo som de vários
// jeitos — com outro caixa, outro acento, outra fronteira de palavra — e comparar letra a letra não
// reconhece nada disso.
object PtBrSound {
    // Letras e dígitos, sem acento e sem caixa: "a mesma palavra escrita de outro jeito".
    fun plain(token: String): String = buildString {
        token.forEach { char ->
            val lower = char.lowercaseChar()
            val plain = ACCENTS[lower] ?: lower
            if (plain.isLetterOrDigit()) append(plain)
        }
    }

    // A chave de som de um trecho inteiro: `plain` sem dígito, sem espaço, com a semivogal valendo
    // sempre `i` (é o que separa "napraj" de "na praia") e sem letra repetida em seguida, que em
    // pt-BR não dobra o som e aparece justamente onde a fronteira foi trocada ("de espinéia" →
    // "deespineia"). Dígito sai fora porque número, dose e unidade nunca entram em comparação de som.
    fun key(text: String): String = buildString {
        plain(text).forEach { char ->
            if (char.isDigit()) return@forEach
            val sound = GLIDES[char] ?: char
            if (lastOrNull() != sound) append(sound)
        }
    }

    // As consoantes da chave, na ordem: o esqueleto da palavra. A vogal o modelo troca o tempo todo;
    // consoante nova é outra palavra ("hipotensão" não é "hipertensão").
    fun skeleton(key: String): String = key.filter { it !in VOWELS }

    const val VOWELS = "aeiou"

    private val GLIDES = mapOf('j' to 'i', 'y' to 'i')

    private val ACCENTS: Map<Char, Char> = mapOf(
        'á' to 'a', 'à' to 'a', 'â' to 'a', 'ã' to 'a', 'ä' to 'a',
        'é' to 'e', 'è' to 'e', 'ê' to 'e', 'ë' to 'e',
        'í' to 'i', 'ì' to 'i', 'î' to 'i', 'ï' to 'i',
        'ó' to 'o', 'ò' to 'o', 'ô' to 'o', 'õ' to 'o', 'ö' to 'o',
        'ú' to 'u', 'ù' to 'u', 'û' to 'u', 'ü' to 'u',
        'ç' to 'c', 'ñ' to 'n'
    )
}
