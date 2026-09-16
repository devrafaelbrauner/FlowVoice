package dev.rafaelbrauner.flowvoice.shared.pipeline

import dev.rafaelbrauner.flowvoice.shared.preview.TranscriptOverlap
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionSegment

// `contiguous`: o texto que o FlowVoice digitou ainda está logo antes do cursor. Depois de uma recusa
// ou de "Inserir aqui" noutro campo isso deixa de valer, e nada pode ser apagado (P144).
data class DirectInsertionPlan(
    val nextWindow: Int = 0,
    val transcript: String = "",
    val contiguous: Boolean = true
)

data class DirectInsertionPiece(
    val windowIndex: Int,
    val separator: String,
    val text: String,
    // Caracteres a apagar antes de escrever este pedaço (P144): só o ponto que o próprio FlowVoice
    // inseriu ao fechar o trecho anterior.
    val deleteBefore: Int = 0
)

data class DirectInsertionStep(val plan: DirectInsertionPlan, val pieces: List<DirectInsertionPiece>)

// Inserção direta (P139): cada janela vira um pedaço a digitar, sempre na ordem das janelas. A janela n
// só sai depois que todas as anteriores estão resolvidas (texto, silêncio ou falha).
object DirectInsertionPlanner {
    fun advance(plan: DirectInsertionPlan, segments: List<TranscriptionSegment>): DirectInsertionStep {
        val byWindow = segments.associateBy { it.windowIndex }
        var next = plan.nextWindow
        var transcript = plan.transcript
        var contiguous = plan.contiguous
        val pieces = mutableListOf<DirectInsertionPiece>()
        while (true) {
            val segment = byWindow[next] ?: break
            if (segment.status == TranscriptionSegment.Status.Transcribing) break
            if (segment.status == TranscriptionSegment.Status.Ok) {
                val match = TranscriptOverlap.match(transcript, segment.text, segment.contextDurationMs)
                if (match.text.isNotEmpty()) {
                    val deleteBefore = if (contiguous) trailingStopToErase(transcript, match) else 0
                    val kept = transcript.dropLast(deleteBefore)
                    val separator = separatorFor(kept, match)
                    pieces += DirectInsertionPiece(
                        windowIndex = next,
                        separator = separator,
                        text = match.text,
                        deleteBefore = deleteBefore
                    )
                    transcript = kept + separator + match.text
                    // O pedaço seguinte do mesmo lote vem logo depois do que acabamos de escrever.
                    contiguous = true
                }
            }
            next++
        }
        return DirectInsertionStep(DirectInsertionPlan(next, transcript, contiguous), pieces)
    }

    // O modelo fecha cada trecho com ponto. Quando o trecho seguinte continua a frase, o ponto fica no
    // meio dela ("O exame de sangue. mostrou leucocitose.", medido no S26 em 2026-09-16). Como ele já
    // está no campo, o jeito de tirá-lo é apagar um caractere antes de escrever o pedaço novo.
    private fun trailingStopToErase(transcript: String, match: TranscriptOverlap.Match): Int = when {
        !transcript.endsWith(FULL_STOP) -> 0
        !continuesSentence(match) -> 0
        // "Dr." não é fim de frase. Exigir a palavra anterior toda minúscula evita desfazer
        // abreviação e nome próprio, que carregam maiúscula.
        !lastWordIsPlainLowercase(transcript.dropLast(1)) -> 0
        else -> 1
    }

    // Maiúscula é o sinal de que o próprio modelo encerrou a frase; nesse caso nada é apagado e a
    // inicial não é rebaixada, porque rebaixar corromperia nome próprio.
    private fun continuesSentence(match: TranscriptOverlap.Match): Boolean =
        match.glued || match.text.first().isLowerCase() || match.text.first() in ATTACHED_PUNCTUATION

    private fun lastWordIsPlainLowercase(head: String): Boolean {
        val word = head.takeLastWhile { !it.isWhitespace() }
        return word.isNotEmpty() && word.none { it.isLetter() && it.isUpperCase() }
    }

    // O separador sai da fronteira de verdade, não só de "é o primeiro trecho": pontuação que
    // pertence à frase anterior e palavra partida no corte (P143) entram coladas; todo o resto entra
    // com um espaço, inclusive depois de ponto final.
    private fun separatorFor(transcript: String, match: TranscriptOverlap.Match): String = when {
        transcript.isEmpty() -> ""
        match.glued -> ""
        transcript.last().isWhitespace() -> ""
        match.text.first().isWhitespace() -> ""
        match.text.first() in ATTACHED_PUNCTUATION -> ""
        else -> " "
    }

    private const val ATTACHED_PUNCTUATION = ",.;:!?…%)]}»"
    private const val FULL_STOP = "."
}
