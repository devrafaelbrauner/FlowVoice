package dev.rafaelbrauner.flowvoice.shared.preview

import dev.rafaelbrauner.flowvoice.shared.transcription.TranscriptionSegment

object LivePreviewAssembler {
    fun assemble(
        segments: List<TranscriptionSegment>,
        sessionComplete: Boolean
    ): LivePreview {
        val merged = segments
            .sortedBy { it.windowIndex }
            .filter { it.status == TranscriptionSegment.Status.Ok && it.text.isNotBlank() }
            .fold("") { acc, segment -> mergeAdjacent(acc, segment.text.trim(), segment.contextDurationMs) }

        val transcribing = segments.any { it.status == TranscriptionSegment.Status.Transcribing }
        return if (sessionComplete && !transcribing) {
            LivePreview(finalized = merged, provisional = "")
        } else {
            val suffix = if (transcribing) "…" else ""
            LivePreview(
                finalized = "",
                provisional = listOf(merged, suffix).filter { it.isNotBlank() }.joinToString(" ")
            )
        }
    }

    fun mergeAdjacent(left: String, right: String, contextDurationMs: Long = 0L): String {
        if (left.isBlank()) return right.trim()
        if (right.isBlank()) return left.trim()
        val match = TranscriptOverlap.match(left, right, contextDurationMs)
        val head = left.trimEnd()
        return when {
            match.text.isEmpty() -> head
            match.glued -> head + match.text
            else -> "$head ${match.text}"
        }
    }

    // O que de `right` falta depois de `left`, com a mesma deduplicação de mergeAdjacent (P127/P143).
    // Na inserção direta (P139) `left` já está no campo e não se apaga: a sobreposição sai de `right`.
    fun continuation(left: String, right: String, contextDurationMs: Long = 0L): String =
        TranscriptOverlap.match(left, right, contextDurationMs).text
}
