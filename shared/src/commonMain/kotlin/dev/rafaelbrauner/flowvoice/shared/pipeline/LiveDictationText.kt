package dev.rafaelbrauner.flowvoice.shared.pipeline

import dev.rafaelbrauner.flowvoice.shared.preview.LivePreview
import dev.rafaelbrauner.flowvoice.shared.preview.LivePreviewAssembler
import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionSegment

object LiveDictationText {
    const val PENDING_MARK = "…"

    fun split(segments: List<TranscriptionSegment>): LivePreview {
        val ok = segments
            .sortedBy { it.windowIndex }
            .filter { it.status == TranscriptionSegment.Status.Ok && it.text.isNotBlank() }
            .map { it.text.trim() }
        val transcribing = segments.any { it.status == TranscriptionSegment.Status.Transcribing }
        val stable = ok.dropLast(1).fold("") { acc, next -> LivePreviewAssembler.mergeAdjacent(acc, next) }
        val latest = ok.lastOrNull().orEmpty()
        val merged = LivePreviewAssembler.mergeAdjacent(stable, latest)
        val provisional = merged.removePrefix(stable).trim()
        val tail = listOf(provisional, if (transcribing) PENDING_MARK else "")
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return LivePreview(finalized = stable, provisional = tail)
    }
}
