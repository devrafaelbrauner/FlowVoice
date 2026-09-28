package dev.rafaelbrauner.flowvoice.shared.pipeline

import dev.rafaelbrauner.flowvoice.shared.preview.LivePreview
import dev.rafaelbrauner.flowvoice.shared.preview.LivePreviewAssembler
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionSegment

object LiveDictationText {
    const val PENDING_MARK = "…"

    // A emenda usa o contexto de cada janela como o texto final (`LivePreviewAssembler.assemble`),
    // senão a prévia mostra repetições que o texto inserido não terá (N1).
    fun split(segments: List<TranscriptionSegment>): LivePreview {
        val ok = segments
            .sortedBy { it.windowIndex }
            .filter { it.status == TranscriptionSegment.Status.Ok && it.text.isNotBlank() }
        val transcribing = segments.any { it.status == TranscriptionSegment.Status.Transcribing }
        val stable = ok.dropLast(1).fold("") { acc, next ->
            LivePreviewAssembler.mergeAdjacent(acc, next.text.trim(), next.contextDurationMs, next.continuous, next.glued)
        }
        val latest = ok.lastOrNull()
        val merged = LivePreviewAssembler.mergeAdjacent(
            stable,
            latest?.text?.trim().orEmpty(),
            latest?.contextDurationMs ?: 0L,
            latest?.continuous ?: false,
            latest?.glued ?: false
        )
        val provisional = merged.removePrefix(stable).trim()
        val tail = listOf(provisional, if (transcribing) PENDING_MARK else "")
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return LivePreview(finalized = stable, provisional = tail)
    }
}
