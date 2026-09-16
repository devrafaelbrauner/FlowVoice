package dev.rafaelbrauner.flowvoice.shared.transcription

// Corte do contexto sobreposto **pelo tempo** (P146).
//
// Desde a P143 cada janela vai à API com o último 1 s da janela anterior à frente, e o modelo devolve
// esse pedaço transcrito de novo. Até aqui a repetição era achada comparando texto (P127/P143), o que
// falha quando o modelo escreve o contexto de outro jeito: no S26 (2026-09-16) "Avaliado pelo doutor"
// voltou como "Segundo doutor" na janela seguinte e o trecho entrou dobrado no campo.
//
// Com os tempos da resposta, **tudo que termina antes do fim do contexto é repetição** e sai sem
// depender de o modelo escrever igual. Três estratégias, da mais fina para a mais grossa:
//  - `Words`: corte por palavra, quando o provedor devolve `words`;
//  - `Segments`: corte por segmento, mais grosso — um segmento costuma ser uma frase inteira, e o
//    contexto quase sempre cai no primeiro deles, junto com o começo da fala da janela;
//  - `Text`: sem tempos, nada é cortado aqui e vale a deduplicação por texto.
//
// A folga (`TOLERANCE_MS`) anda para trás, nunca para a frente: na dúvida a palavra fica. Perder a
// primeira palavra da janela por arredondamento seria irreversível; repeti-la, não — a deduplicação
// por texto continua a jusante como rede de segurança.
object ContextTrim {
    const val TOLERANCE_MS = 120L

    enum class Strategy { Words, Segments, Text }

    data class Result(
        val text: String,
        val strategy: Strategy,
        val droppedMs: Long = 0L,
        val droppedWords: Int = 0,
        val droppedSegments: Int = 0
    )

    fun trim(
        payload: TranscriptionPayload,
        contextDurationMs: Long,
        toleranceMs: Long = TOLERANCE_MS
    ): Result {
        val text = payload.text.trim()
        if (text.isEmpty()) return Result("", Strategy.Text)
        if (contextDurationMs <= 0L) return Result(text, Strategy.Text)
        val cutoffMs = (contextDurationMs - toleranceMs).coerceAtLeast(0L)
        return when {
            payload.words.isNotEmpty() -> trimByWords(text, payload.words, cutoffMs)
            payload.segments.isNotEmpty() -> trimBySegments(text, payload.segments, cutoffMs)
            else -> Result(text, Strategy.Text)
        }
    }

    private fun trimByWords(text: String, words: List<TimedUnit>, cutoffMs: Long): Result {
        // Só o começo é contexto: `takeWhile` protege uma palavra do meio da janela que volte com
        // tempo estranho.
        val dropped = words.takeWhile { it.endMs <= cutoffMs }
        if (dropped.isEmpty()) return Result(text, Strategy.Words)
        val droppedMs = dropped.last().endMs
        if (dropped.size == words.size) return Result("", Strategy.Words, droppedMs, dropped.size)

        // Com uma palavra por token, o texto que sobra sai do `text` original e mantém a pontuação e a
        // caixa do modelo. Quando as contagens não batem (palavra hifenizada, pontuação solta), o
        // texto é remontado das palavras que ficaram: perde-se pontuação, não palavra.
        val tokens = text.split(SPACES).filter { it.isNotBlank() }
        val kept = if (tokens.size == words.size) {
            tokens.drop(dropped.size).joinToString(" ")
        } else {
            words.drop(dropped.size).joinToString(" ") { it.text.trim() }
        }
        return Result(kept.trim(), Strategy.Words, droppedMs, dropped.size)
    }

    private fun trimBySegments(text: String, segments: List<TimedUnit>, cutoffMs: Long): Result {
        val dropped = segments.takeWhile { it.endMs <= cutoffMs }
        if (dropped.isEmpty()) return Result(text, Strategy.Segments)
        val droppedMs = dropped.last().endMs
        val kept = segments.drop(dropped.size).joinToString(" ") { it.text.trim() }
        return Result(kept.trim(), Strategy.Segments, droppedMs, droppedSegments = dropped.size)
    }

    private val SPACES = Regex("\\s+")
}
