package dev.rafaelbrauner.flowvoice.shared.dictionary

object TermSuggester {
    // Palavras funcionais e de tempo do pt-BR que a fala produz, inclusive as clínicas do dia a dia
    // ("paciente disse"-tipo frases): nunca são termo do vocabulário (Item 3 da revisão do motor).
    // O candidato chega cru, como no texto — com acento e com maiúscula de início de frase — e é
    // comparado em minúsculas, então as entradas guardam a forma acentuada que a fala produz
    // ("não", "está", "à"). Entradas curtas de 1 a 3 letras ficam como proteção: o filtro de
    // comprimento (4+ letras) as descarta antes desta lista ser consultada.
    private val stop = setOf(
        "a", "o", "as", "os", "um", "uma", "de", "da", "do", "das", "dos",
        "e", "em", "no", "na", "nos", "nas", "para", "por", "com", "que",
        "se", "é", "está", "estão", "ao", "à", "às", "ou",
        "não", "pela", "pelo", "pelas", "pelos", "seu", "sua", "seus", "suas",
        "meu", "minha", "meus", "minhas", "isso", "isto", "esse", "essa",
        "esses", "essas", "este", "esta", "estes", "estas", "aquele", "aquela",
        "muito", "muita", "muitos", "muitas", "mais", "menos", "já", "aqui",
        "ali", "lá", "então", "também", "quando", "onde", "porque", "porém",
        "mas", "tudo", "todos", "todas", "cada", "qual", "quais", "quem",
        "eles", "elas", "nós", "você", "vocês", "dele", "dela", "deles", "delas",
        "cujo", "cuja", "assim", "ainda", "quase", "talvez", "depois", "antes",
        "sempre", "nunca", "hoje", "ontem", "amanhã"
    )

    fun candidates(text: String): List<String> {
        val seen = linkedSetOf<String>()
        text.split(Regex("[^\\p{L}\\p{N}]+"))
            .map { it.trim() }
            .filter { it.length >= 4 }
            .filter { it.lowercase() !in stop }
            .forEach { token -> seen += token }
        return seen.toList()
    }
}