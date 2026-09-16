package dev.rafaelbrauner.flowvoice.shared.pipeline

import dev.rafaelbrauner.flowvoice.shared.preview.LivePreviewAssembler
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
                val piece = LivePreviewAssembler.continuation(transcript, segment.text)
                if (piece.isNotEmpty()) {
                    val separator = if (transcript.isEmpty()) "" else " "
                    pieces += DirectInsertionPiece(windowIndex = next, separator = separator, text = piece)
                    transcript += separator + piece
                }
            }
            next++
        }
        return DirectInsertionStep(DirectInsertionPlan(nextWindow = next, transcript = transcript), pieces)
    }
}
