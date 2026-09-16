package dev.rafaelbrauner.flowvoice.shared.pipeline

import dev.rafaelbrauner.flowvoice.shared.preview.TranscriptOverlap
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionSegment

data class DirectInsertionPlan(val nextWindow: Int = 0, val transcript: String = "")

data class DirectInsertionPiece(val windowIndex: Int, val separator: String, val text: String)

data class DirectInsertionStep(val plan: DirectInsertionPlan, val pieces: List<DirectInsertionPiece>)

// Inserção direta (P139): cada janela vira um pedaço a digitar, sempre na ordem das janelas. A janela n
// só sai depois que todas as anteriores estão resolvidas (texto, silêncio ou falha).
object DirectInsertionPlanner {
    fun advance(plan: DirectInsertionPlan, segments: List<TranscriptionSegment>): DirectInsertionStep {
        val byWindow = segments.associateBy { it.windowIndex }
        var next = plan.nextWindow
        var transcript = plan.transcript
        val pieces = mutableListOf<DirectInsertionPiece>()
        while (true) {
            val segment = byWindow[next] ?: break
            if (segment.status == TranscriptionSegment.Status.Transcribing) break
            if (segment.status == TranscriptionSegment.Status.Ok) {
                val match = TranscriptOverlap.match(transcript, segment.text)
                if (match.text.isNotEmpty()) {
                    val separator = separatorFor(transcript, match)
                    pieces += DirectInsertionPiece(windowIndex = next, separator = separator, text = match.text)
                    transcript += separator + match.text
                }
            }
            next++
        }
        return DirectInsertionStep(DirectInsertionPlan(nextWindow = next, transcript = transcript), pieces)
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
}
