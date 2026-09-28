package dev.rafaelbrauner.flowvoice.shared.insertion

// Pedaços de cada escrita pela rota commitText. O corpo de nota do Samsung Notes (S26, 2026-09-28, sondas de
// texto fixo) trata um commitText de 25 caracteres ou mais como colagem: aplica depois (de ~60 a ~300 ms), tira
// o espaço da frente e do fim, põe uma linha nova no fim e deixa o cursor depois dela, ignorando o
// `newCursorPosition`. Era a emenda quebrada do ditado ao vivo ("coco?\n tomar", "bem?Consegue") e, quando o
// trecho seguinte saía antes de o longo ser aplicado, a ordem trocada que fazia a passada final ser recusada
// ("bem? Paciente do leito doze.Consegue me ligar…"). Até 24 caracteres o commit entra na hora e igual ao
// mandado, em qualquer sequência; o título da nota (EditText) aceita os longos. Juntos, os pedaços são o texto
// inteiro, na ordem.
object CommitChunks {
    // Folga abaixo dos 25 medidos.
    const val MAX_CHARS = 20

    fun split(text: String, max: Int = MAX_CHARS): List<String> {
        require(max >= 2) { "max must be >= 2" }
        val chunks = mutableListOf<String>()
        var start = 0
        while (text.length - start > max) {
            val end = cut(text, start, max)
            chunks += text.substring(start, end)
            start = end
        }
        if (start < text.length) chunks += text.substring(start)
        return chunks
    }

    // Antes do último espaço que cabe, para cada pedaço começar como os trechos do ditado (" palavra"); numa
    // palavra maior que o pedaço, no limite, sem separar um par substituto (emoji).
    private fun cut(text: String, start: Int, max: Int): Int {
        for (index in start + max downTo start + 1) {
            if (text[index].isWhitespace()) return index
        }
        val end = start + max
        return if (text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end - 1 else end
    }
}
